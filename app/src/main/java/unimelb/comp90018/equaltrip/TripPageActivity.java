package unimelb.comp90018.equaltrip;

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
import android.view.SoundEffectConstants;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TripPageActivity extends AppCompatActivity {

    private RecyclerView rvTrips;
    private FloatingActionButton fabAdd;
    private BottomNavigationView bottom;

    private FirebaseFirestore db;
    private ListenerRegistration tripListener;

    private final List<Trip> trips = new ArrayList<>();
    private TripAdapter adapter;
    private boolean suppressNav = false;

    // ====== 新增：蓝牙权限请求码 ======
    private static final int REQ_BLE_S_BROADCAST = 1001;

    // 1) 两个监听的引用，便于 onStop() 解绑
    private ListenerRegistration ownerReg;
    private ListenerRegistration invitedReg;

    // 2) 数据容器：用 Map 去重（两路查询合并），再喂给原来的 adapter
    private final Map<String, Trip> byId = new HashMap<>();

    private void setupBottomNav() {
        bottom = findViewById(R.id.bottom_nav);
        if (bottom == null) return;

        bottom.setOnItemSelectedListener(item -> {
            if (suppressNav) return true; // 程序化选中时不导航
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

        // 程序化设中“Trips”且不触发监听
        suppressNav = true;
        bottom.setSelectedItemId(R.id.nav_trips);
        bottom.getMenu().findItem(R.id.nav_trips).setChecked(true);
        bottom.post(() -> suppressNav = false);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_page);
        setupBottomNav();

        // --- List & FAB ---
        rvTrips = findViewById(R.id.rv_trips);
        fabAdd = findViewById(R.id.fab_add_trip);
        fabAdd.setSoundEffectsEnabled(true);

        rvTrips.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TripAdapter(trips, t -> {
            Intent i = new Intent(this, TripDetailActivity.class);
            i.putExtra("tripId", t.id);
            startActivity(i);
        });
        rvTrips.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> {
            v.playSoundEffect(SoundEffectConstants.CLICK);
            startActivity(new Intent(this, AddTripActivity.class));
        });

        // ====== 新增：右上角蓝牙图标接“广播我的UID” ======
        ImageView ivBluetooth = findViewById(R.id.iv_bluetooth);
        if (ivBluetooth != null) {
            ivBluetooth.setOnClickListener(v -> tryStartBleBroadcast());
        }

        db = FirebaseFirestore.getInstance();
    }

    // ====== 新增：尝试开始广播（含动态权限） ======
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
            // Android 12 以下扫描/连接配套需要定位权限（是否已授予）
            if (!hasAll(new String[]{ Manifest.permission.ACCESS_FINE_LOCATION })) {
                ActivityCompat.requestPermissions(this,
                        new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
                        REQ_BLE_S_BROADCAST);
                return;
            }
        }
        // 权限就绪：开始广播（例如 10 秒）
        BleUidExchange.get(this).startBroadcasting(10_000);
        Toast.makeText(this, "Broadcasting my UID…", Toast.LENGTH_SHORT).show();
    }

    // ====== 新增：权限回调，允许后再次尝试 ======
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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

    @Override
    protected void onStart() {
        byId.clear();
        trips.clear();
        adapter.notifyDataSetChanged();

        super.onStart();

        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        final String myUid = auth.getCurrentUser().getUid();
        final String myEmailLower = auth.getCurrentUser().getEmail().toLowerCase(java.util.Locale.ROOT);

        // 统一的结果应用器：把本次快照合并进 byId，列表本地降序排序，再通知 adapter
        EventListener<QuerySnapshot> apply = (snap, e) -> {
            if (e != null || snap == null) return;
            for (DocumentSnapshot d : snap.getDocuments()) {
                Trip t = d.toObject(Trip.class);
                if (t == null) continue;
                if (t.id == null) t.id = d.getId();
                byId.put(t.id, t);
            }
            // 本地排序（避免 where + orderBy 触发复合索引）
            List<Trip> merged = new java.util.ArrayList<>(byId.values());
            java.util.Collections.sort(merged, (a, b) -> Long.compare(
                    b.createdAtClient == null ? 0L : b.createdAtClient,
                    a.createdAtClient == null ? 0L : a.createdAtClient
            ));
            trips.clear();
            trips.addAll(merged);
            adapter.notifyDataSetChanged();
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

    // ====== 新增：释放 BLE 资源，避免 “Call requires permission …” 报警/泄漏 ======
    @Override
    protected void onDestroy() {
        super.onDestroy();
        BleUidExchange.get(this).onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        if (bottom != null) {
            suppressNav = true;
            bottom.setSelectedItemId(R.id.nav_trips);
            bottom.getMenu().findItem(R.id.nav_trips).setChecked(true);
            bottom.post(() -> suppressNav = false);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // if (requestCode == 1001 && resultCode == RESULT_OK) fetchTripsOnce();
    }
}
