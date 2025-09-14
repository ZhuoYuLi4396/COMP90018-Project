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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.SoundEffectConstants;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
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
        //setContentView(R.layout.activity_trip_page);

        // --- Bottom nav ---
//        bottom = findViewById(R.id.bottom_nav);
//        if (bottom != null) {
//            bottom.setSelectedItemId(R.id.nav_trips);
//            bottom.setOnItemSelectedListener(item -> {
//                int id = item.getItemId();
//                if (id == R.id.nav_trips) return true; // already here
//                if (id == R.id.nav_home) {
//                    startActivity(new Intent(this, HomeActivity.class)
//                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
//                    overridePendingTransition(0, 0);
//                    return true;
//                }
//                if (id == R.id.nav_profile) {
//                    startActivity(new Intent(this, ProfileActivity.class)
//                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
//                    overridePendingTransition(0, 0);
//                    return true;
//                }
//                return false;
//            });
//            bottom.setOnItemReselectedListener(item -> { /* no-op */ });
//        }

//        // --- List & FAB ---
//        rvTrips = findViewById(R.id.rv_trips);
//        fabAdd = findViewById(R.id.fab_add_trip);
//
//        // 可选：显式开启该 View 的点击音效（通常默认就是 true）
//        fabAdd.setSoundEffectsEnabled(true);
//
//
//        rvTrips.setLayoutManager(new LinearLayoutManager(this));
//        adapter = new TripAdapter(trips, t -> {
//            Intent i = new Intent(this, TripDetailActivity.class);
//            i.putExtra("tripId", t.id);
//            startActivity(i);
//        });
//        rvTrips.setAdapter(adapter);
//
//        fabAdd.setOnClickListener(v -> {
//
//            v.playSoundEffect(SoundEffectConstants.CLICK); // ✅ 播放系统点击音
//            Intent i = new Intent(this, AddTripActivity.class);
//            startActivity(i);
//        });
//
//        // Firebase
//        db = FirebaseFirestore.getInstance();
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

        db = FirebaseFirestore.getInstance();
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
