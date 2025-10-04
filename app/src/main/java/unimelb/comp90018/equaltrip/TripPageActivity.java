package unimelb.comp90018.equaltrip;

// Author: Jinglin Lei (base) + merges
// Date: 2025-10-03
// Trip list + search + BLE + unified bottom nav
// Author: Jinglin Lei
// SignUp Function
//Date: 2025-09-05

// Author: Ziyan Zhai
// Based on the code of Jinglin Lei, add 2 extra functions:
// 1, Only synchronous the related to the users, instead of synchronously all the trip cards.
// 2, While user A create a trip and invite user B ahd user C. B and C will see the trip card.
// But user D will not receive any trip card(Function 1).
// Date: 2025-09-14

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.SoundEffectConstants;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 你项目中的实体与适配器（保持原样）
/*
import unimelb.comp90018.equaltrip.data.Trip;
import unimelb.comp90018.equaltrip.ui.TripAdapter;
*/

public class TripPageActivity extends AppCompatActivity {

    private RecyclerView rvTrips;
    private FloatingActionButton fabAdd;
    private BottomNavigationView bottom;

    private TextInputEditText etSearch;
    private String currentQuery = "";

    private FirebaseFirestore db;

    private final List<Trip> trips = new ArrayList<>();
    private TripAdapter adapter;
    private boolean suppressNav = false;

    // ==== BLE 权限请求码 ====
    private static final int REQ_BLE_S_BROADCAST = 1001;

    // 两个监听：owner 和 invited
    private ListenerRegistration ownerReg;
    private ListenerRegistration invitedReg;

    // 合并两路数据、去重
    private final Map<String, Trip> byId = new HashMap<>();

    private void setupBottomNav() {
        bottom = findViewById(R.id.bottom_nav);
        if (bottom == null) return;

        bottom.setOnItemSelectedListener(item -> {
            if (suppressNav) return true; // 程序化高亮时不导航
            int id = item.getItemId();
            if (id == R.id.nav_trips) return true;
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                overridePendingTransition(0,0);
                return true;
            }
            if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                overridePendingTransition(0,0);
                return true;
            }
            return false;
        });

        // 程序化高亮 Trips（不触发导航）
        suppressNav = true;
        bottom.getMenu().findItem(R.id.nav_trips).setChecked(true);
        bottom.post(() -> suppressNav = false);

        bottom.setOnItemReselectedListener(item -> { /* no-op */ });
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_page);

        setupBottomNav(); // 统一导航

        // --- List & FAB & Search ---
        rvTrips  = findViewById(R.id.rv_trips);
        fabAdd   = findViewById(R.id.fab_add_trip);
        etSearch = findViewById(R.id.et_search);

        if (fabAdd != null) fabAdd.setSoundEffectsEnabled(true);
        rvTrips.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TripAdapter(trips, t -> {
            Intent i = new Intent(this, TripDetailActivity.class);
            i.putExtra("tripId", t.id);
            startActivity(i);
        });
        rvTrips.setAdapter(adapter);

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> {
                v.playSoundEffect(SoundEffectConstants.CLICK);
                startActivity(new Intent(this, AddTripActivity.class));
            });
        }

        // 搜索框：仅按名称过滤（本地）
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    currentQuery = (s == null) ? "" : s.toString().trim();
                    mergeAndPublish(); // 输入变化即刷新
                }
            });
        }

        // 右上角蓝牙图标（存在则启用）
        ImageView ivBluetooth = findViewById(R.id.iv_bluetooth);
        if (ivBluetooth != null) {
            ivBluetooth.setOnClickListener(v -> tryStartBleBroadcast());
        }

        db = FirebaseFirestore.getInstance();
    }

    @Override
    protected void onStart() {
        super.onStart();

        byId.clear();
        trips.clear();
        adapter.notifyDataSetChanged();

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return; // 未登录保护

        final String myUid = auth.getCurrentUser().getUid();
        final String myEmailLower = auth.getCurrentUser().getEmail() == null
                ? "" : auth.getCurrentUser().getEmail().toLowerCase(Locale.ROOT);

        // 合并应用器：合并到 byId 后调用 mergeAndPublish
        EventListener<QuerySnapshot> apply = (snap, e) -> {
            if (e != null || snap == null) return;
            for (DocumentSnapshot d : snap.getDocuments()) {
                Trip t = d.toObject(Trip.class);
                if (t == null) continue;
                if (t.id == null) t.id = d.getId();
                byId.put(t.id, t);
            }
            mergeAndPublish();
        };

        // ① 我是 owner 的 trips
        ownerReg = db.collection("trips")
                .whereEqualTo("ownerId", myUid)
                .addSnapshotListener(apply);

        // ② 我被邮箱邀请的 trips（tripmates 是“邮箱数组”字段）
        invitedReg = db.collection("trips")
                .whereArrayContains("tripmates", myEmailLower)
                .addSnapshotListener(apply);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (ownerReg != null) { ownerReg.remove(); ownerReg = null; }
        if (invitedReg != null) { invitedReg.remove(); invitedReg = null; }
    }

    @Override protected void onResume() {
        super.onResume();
        if (bottom != null) {
            suppressNav = true;
            bottom.getMenu().findItem(R.id.nav_trips).setChecked(true);
            bottom.post(() -> suppressNav = false);
        }
    }

    // ====== 合并 → 排序 → 按 name 过滤 → 发布 ======
    private void mergeAndPublish() {
        // 1) 合并
        List<Trip> merged = new ArrayList<>(byId.values());

        // 2) 排序：createdAtClient 降序（空视为 0）
        merged.sort((a, b) -> Long.compare(
                b.createdAtClient == null ? 0L : b.createdAtClient,
                a.createdAtClient == null ? 0L : a.createdAtClient
        ));

        // 3) 仅按 name 过滤（不区分大小写）
        String q = currentQuery == null ? "" : currentQuery.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            List<Trip> filtered = new ArrayList<>();
            for (Trip t : merged) {
                String name = (t == null || t.name == null) ? "" : t.name;
                if (name.toLowerCase(Locale.ROOT).contains(q)) {
                    filtered.add(t);
                }
            }
            merged = filtered;
        }

        // 4) 发布
        trips.clear();
        trips.addAll(merged);
        adapter.notifyDataSetChanged();
    }

    // ====== BLE：尝试开始广播（含动态权限） ======
    private void tryStartBleBroadcast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] perms = new String[] {
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            };
            if (!hasAll(perms)) {
                ActivityCompat.requestPermissions(this, perms, REQ_BLE_S_BROADCAST);
                return;
            }
        } else {
            // Android 12 以下，扫描/连接配套需要定位权限
            if (!hasAll(new String[]{ Manifest.permission.ACCESS_FINE_LOCATION })) {
                ActivityCompat.requestPermissions(this,
                        new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
                        REQ_BLE_S_BROADCAST);
                return;
            }
        }
        // 权限就绪：开始广播（例如 10 秒）
        try {
            BleUidExchange.get(this).startBroadcasting(10_000);
            Toast.makeText(this, "Broadcasting my UID…", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "BLE module not available.", Toast.LENGTH_SHORT).show();
        }
    }

    // ====== 权限回调，允许后再次尝试 ======
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BLE_S_BROADCAST) {
            if (hasAll(permissions)) {
                tryStartBleBroadcast();
            } else {
                Toast.makeText(this, "Bluetooth permission required to broadcast.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean hasAll(String[] ps) {
        for (String p : ps) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // ====== 释放 BLE 资源 ======
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            BleUidExchange.get(this).onDestroy();
        } catch (Throwable ignored) {
            // 未集成 BLE 时静默忽略
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
}
