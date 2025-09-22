package unimelb.comp90018.equaltrip;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.firebase.auth.FirebaseAuth;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.UUID;

/**
 * 只做两件事：
 * 1) startBroadcasting(ms)：作为 B 端广播 + 提供 GATT Server，Read 特征时返回当前 Firebase UID
 * 2) scanAndFetchUids(ms, callback)：作为 A 端扫描，逐个连接匹配到的设备，读取对方 UID，通过回调交给调用者
 *
 * 使用方式（Activity 内）：
 *   BleUidExchange ble = BleUidExchange.get(this);
 *   // B 端：开始广播
 *   ble.startBroadcasting(10_000);
 *   // A 端：扫描并取 UID
 *   ble.scanAndFetchUids(20_000, new BleUidExchange.OnUidFoundListener() {
 *       @Override public void onUidFound(String uid) {
 *           // 用 uid 去查 Firestore -> email -> addTripmateByEmail(email)
 *       }
 *   });
 */
public class BleUidExchange {

    // ===== 自定义 Service/Characteristic UUID（与 demo 保持一致）=====
    public static final UUID SERVICE_UUID = UUID.fromString("0000e001-0000-1000-8000-00805f9b34fb");
    public static final UUID CHAR_UID     = UUID.fromString("0000e012-0000-1000-8000-00805f9b34fb");

    private static final String TAG = "BleUidExchange";

    public interface OnUidFoundListener {
        void onUidFound(String uid);      // 每发现一个对端 UID 就回调一次
        default void onScanFinished() {}  // 扫描结束（可选）
    }

    // ===== 单例 =====
    private static BleUidExchange sInstance;
    public static synchronized BleUidExchange get(Context ctx) {
        if (sInstance == null) sInstance = new BleUidExchange(ctx.getApplicationContext());
        return sInstance;
    }

    private final Context app;
    private final Handler mainH = new Handler(Looper.getMainLooper());

    private BluetoothAdapter adapter;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothLeScanner scanner;
    private BluetoothGattServer gattServer;
    private BluetoothGatt activeGatt;

    // 扫描/连接去重
    private final LinkedList<BluetoothDevice> connectQueue = new LinkedList<>();
    private final HashSet<String> queuedAddrs = new HashSet<>();
    private final HashSet<String> processedAddrs = new HashSet<>();
    private boolean isConnecting = false;
    private boolean isScanning = false;

    private BleUidExchange(Context appCtx) {
        this.app = appCtx;
        BluetoothManager bm = (BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm != null) {
            adapter = bm.getAdapter();
        }
    }

    // ========= 对外：B 端开始广播 =========
    @SuppressLint("MissingPermission")
    public void startBroadcasting(long durationMs) {
        if (!checkPrerequisitesForRadio(true)) return;

        // 确保有 GATT Server
        ensureGattServer();
        // 启动 BLE 广播
        try { if (advertiser != null) advertiser.stopAdvertising(adCallback); } catch (Exception ignored) {}
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            toast("无法获取 BLE Advertiser");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        advertiser.startAdvertising(settings, data, adCallback);
        toast("开始广播 UID（限时 " + (durationMs/1000) + "s）");

        mainH.postDelayed(this::stopAdvertisingSafe, durationMs);
    }

    @SuppressLint("MissingPermission")
    private void stopAdvertisingSafe() {
        try {
            if (advertiser != null &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                            ActivityCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED)) {
                advertiser.stopAdvertising(adCallback);
            }
        } catch (Exception ignored) {}
    }

    private final AdvertiseCallback adCallback = new AdvertiseCallback() {
        @Override public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.d(TAG, "Advertising started: " + settingsInEffect);
        }
        @Override public void onStartFailure(int errorCode) {
            Log.d(TAG, "Advertising failed: " + errorCode);
            toast("广播失败 code=" + errorCode);
        }
    };

    // ========= 对外：A 端开始扫描并获取对方 UID =========
    @SuppressLint("MissingPermission")
    public void scanAndFetchUids(long durationMs, OnUidFoundListener cb) {
        if (!checkPrerequisitesForRadio(false)) return;

        processedAddrs.clear();
        queuedAddrs.clear();
        connectQueue.clear();
        isConnecting = false;

        if (scanner == null) scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { toast("无法获取 BLE Scanner"); return; }

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        isScanning = true;
        scanner.startScan(Collections.singletonList(filter), settings,
                new InternalScan(cb, durationMs));
    }

    private class InternalScan extends ScanCallback {
        private final OnUidFoundListener cb;
        private final long durationMs;

        InternalScan(OnUidFoundListener cb, long durationMs) {
            this.cb = cb;
            this.durationMs = durationMs;
            // 到时停止扫描；队列继续跑到空
            mainH.postDelayed(() -> {
                stopScanSafe(this);
                isScanning = false;
                // 等队列清空后在 connectNext 里回调 onScanFinished()
            }, durationMs);
        }

        @Override public void onScanResult(int callbackType, ScanResult r) {
            BluetoothDevice dev = r.getDevice();
            String addr = safeAddr(dev);
            if ("(no addr)".equals(addr)) return;
            if (processedAddrs.contains(addr)) return;
            if (queuedAddrs.add(addr)) {
                connectQueue.add(dev);
                if (!isConnecting) connectNext(cb, this);
            }
        }

        @Override public void onScanFailed(int errorCode) {
            Log.d(TAG, "Scan failed: " + errorCode);
            stopScanSafe(this);
            isScanning = false;
            cb.onScanFinished();
        }
    }

    @SuppressLint("MissingPermission")
    private void stopScanSafe(ScanCallback cb) {
        try {
            if (scanner != null &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                            ActivityCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)) {
                scanner.stopScan(cb);
            }
        } catch (Exception ignored) {}
    }

    // ========= 串行连接 =========
    @SuppressLint("MissingPermission")
    private void connectNext(OnUidFoundListener cb, ScanCallback scanCb) {
        if (isConnecting) return;

        if (connectQueue.isEmpty()) {
            if (!isScanning) cb.onScanFinished();
            return;
        }
        if (!hasAllPerms()) return;

        final BluetoothDevice device = connectQueue.poll();
        final String addr = safeAddr(device);
        isConnecting = true;

        final Runnable timeout = () -> {
            safeCloseGatt(activeGatt);
            activeGatt = null;
            finishOne(addr, cb, scanCb);
        };
        mainH.postDelayed(timeout, 12_000);

        try {
            BluetoothGattCallback gattCb = new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        cleanupThenNext(gatt, timeout, addr, cb, scanCb);
                        return;
                    }
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        activeGatt = gatt;
                        gatt.requestMtu(185);
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        cleanupThenNext(gatt, timeout, addr, cb, scanCb);
                    }
                }
                @Override public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                    gatt.discoverServices();
                }
                @Override public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status != BluetoothGatt.GATT_SUCCESS) { cleanupThenNext(gatt, timeout, addr, cb, scanCb); return; }
                    BluetoothGattService svc = gatt.getService(SERVICE_UUID);
                    if (svc == null) { cleanupThenNext(gatt, timeout, addr, cb, scanCb); return; }
                    BluetoothGattCharacteristic cUid = svc.getCharacteristic(CHAR_UID);
                    if (cUid == null) { cleanupThenNext(gatt, timeout, addr, cb, scanCb); return; }
                    gatt.readCharacteristic(cUid);
                }
                @Override public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic ch, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS && CHAR_UID.equals(ch.getUuid())) {
                        String uid = new String(ch.getValue(), StandardCharsets.UTF_8).trim();
                        if (!uid.isEmpty()) cb.onUidFound(uid);
                    }
                    cleanupThenNext(gatt, timeout, addr, cb, scanCb);
                }
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activeGatt = device.connectGatt(app, false, gattCb, BluetoothDevice.TRANSPORT_LE);
            } else {
                activeGatt = device.connectGatt(app, false, gattCb);
            }
        } catch (SecurityException se) {
            finishOne(addr, cb, scanCb);
        }
    }

    private void cleanupThenNext(BluetoothGatt gatt, Runnable timeout, String addr,
                                 OnUidFoundListener cb, ScanCallback scanCb) {
        mainH.removeCallbacks(timeout);
        safeCloseGatt(gatt);
        activeGatt = null;
        finishOne(addr, cb, scanCb);
    }

    private void finishOne(String addr, OnUidFoundListener cb, ScanCallback scanCb) {
        isConnecting = false;
        queuedAddrs.remove(addr);
        processedAddrs.add(addr);
        connectNext(cb, scanCb);
    }

    // ========= GATT Server（返回当前 Firebase UID）=========
    @SuppressLint("MissingPermission")
    private void ensureGattServer() {
        if (gattServer != null) return;
        try {
            BluetoothManager bm = (BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE);
            gattServer = bm.openGattServer(app, new BluetoothGattServerCallback() {
                private volatile int mtu = 23;

                @Override public void onMtuChanged(BluetoothDevice device, int mtu) {
                    this.mtu = mtu;
                }

                @Override
                public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                        BluetoothGattCharacteristic characteristic) {
                    byte[] full;
                    if (CHAR_UID.equals(characteristic.getUuid())) {
                        full = getMyUidBytes();
                    } else {
                        full = new byte[0];
                    }
                    int mtuPayload = Math.max(20, mtu - 1);
                    int end = Math.min(full.length, offset + mtuPayload);
                    byte[] slice = Arrays.copyOfRange(full, offset, end);
                    try {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice);
                    } catch (SecurityException ignored) {}
                }
            });

            BluetoothGattService svc = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
            BluetoothGattCharacteristic cUid = new BluetoothGattCharacteristic(
                    CHAR_UID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
            );
            svc.addCharacteristic(cUid);
            gattServer.addService(svc);
        } catch (Exception e) {
            Log.d(TAG, "ensureGattServer err: " + e.getMessage());
        }
    }

    private byte[] getMyUidBytes() {
        String uid = FirebaseAuth.getInstance().getUid(); // 可能为 null（未登录）
        if (uid == null) uid = "";
        return uid.getBytes(StandardCharsets.UTF_8);
    }

    // ========= 权限/前置校验 =========
    private boolean checkPrerequisitesForRadio(boolean forAdvertise) {
        if (adapter == null) {
            toast("本机不支持蓝牙");
            return false;
        }

        // 安全地判断 isEnabled（避免 MissingPermission）
        if (!safeBtEnabled()) {
            if (hasConnectPerm()) {
                try {
                    Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    app.startActivity(i);  // 有 CONNECT 权限才调系统开启蓝牙的弹框
                } catch (SecurityException ignored) {
                    openBluetoothSettingsFallback();
                }
            } else {
                // 无 CONNECT 权限则去设置页
                openBluetoothSettingsFallback();
                toast("请在系统设置中先打开蓝牙");
            }
            return false;
        }

        if (!hasAllPerms()) {
            toast("缺少蓝牙权限");
            return false;
        }
        if (forAdvertise) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                    !adapter.isMultipleAdvertisementSupported()) {
                toast("设备不支持 BLE 广播");
                return false;
            }
        }
        return true;
    }

    private boolean hasAllPerms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return check(Manifest.permission.BLUETOOTH_SCAN)
                    && check(Manifest.permission.BLUETOOTH_ADVERTISE)
                    && check(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            // Android 12 以下需要定位权限来扫描
            return check(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private boolean check(String p) {
        return ActivityCompat.checkSelfPermission(app, p) == PackageManager.PERMISSION_GRANTED;
    }

    private String safeAddr(BluetoothDevice d) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return "(no addr)";
            }
            return d.getAddress();
        } catch (SecurityException e) { return "(no addr)"; }
    }

    private void toast(String s) {
        mainH.post(() -> Toast.makeText(app, s, Toast.LENGTH_SHORT).show());
    }

    // ========= 清理 =========
    @SuppressLint("MissingPermission")
    public void onDestroy() {
        try {
            if (advertiser != null &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                            ActivityCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED)) {
                advertiser.stopAdvertising(adCallback);
            }
        } catch (Exception ignored) {}
        advertiser = null;

        try {
            if (scanner != null &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                            ActivityCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)) {
                scanner.stopScan(new ScanCallback(){});
            }
        } catch (Exception ignored) {}
        scanner = null;

        safeCloseGatt(activeGatt);
        activeGatt = null;

        try {
            if (gattServer != null) gattServer.close(); // close 不需要额外权限
        } catch (Exception ignored) {}
        gattServer = null;
    }

    // ===== 权限与蓝牙状态辅助 =====
    private boolean hasConnectPerm() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ActivityCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void openBluetoothSettingsFallback() {
        try {
            Intent s = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(s);
        } catch (Exception ignored) {}
    }

    private boolean canUseConnectApis() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ActivityCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean safeBtEnabled() {
        if (adapter == null) return false;
        if (!canUseConnectApis()) return false;
        try {
            return adapter.isEnabled();
        } catch (SecurityException e) {
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    private void safeCloseGatt(BluetoothGatt gatt) {
        if (gatt == null) return;
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                    || ActivityCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                gatt.close();
            } else {
                try { gatt.close(); } catch (SecurityException ignored) {}
            }
        } catch (Exception ignored) {}
    }
}
