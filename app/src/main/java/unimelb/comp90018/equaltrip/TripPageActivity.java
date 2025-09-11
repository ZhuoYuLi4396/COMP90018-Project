package unimelb.comp90018.equaltrip;
// Author: Jinglin Lei
// SignUp Function
//Date: 2025-09-05
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.SoundEffectConstants;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class TripPageActivity extends AppCompatActivity {

    private RecyclerView rvTrips;
    private FloatingActionButton fabAdd;
    private BottomNavigationView bottom;

    private FirebaseFirestore db;
    private ListenerRegistration tripListener;

    private final List<Trip> trips = new ArrayList<>();
    private TripAdapter adapter;
    private boolean suppressNav = false;

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
        setContentView(R.layout.activity_trip_page);

        // --- Bottom nav ---
        bottom = findViewById(R.id.bottom_nav);
        if (bottom != null) {
            bottom.setSelectedItemId(R.id.nav_trips);
            bottom.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_trips) return true; // already here
                if (id == R.id.nav_home) {
                    startActivity(new Intent(this, HomeActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                    overridePendingTransition(0, 0);
                    return true;
                }
                if (id == R.id.nav_profile) {
                    startActivity(new Intent(this, ProfileActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                    overridePendingTransition(0, 0);
                    return true;
                }
                return false;
            });
            bottom.setOnItemReselectedListener(item -> { /* no-op */ });
        }

        // --- List & FAB ---
        rvTrips = findViewById(R.id.rv_trips);
        fabAdd = findViewById(R.id.fab_add_trip);
        // 可选：显式开启该 View 的点击音效（通常默认就是 true）
        fabAdd.setSoundEffectsEnabled(true);


        rvTrips.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TripAdapter(trips, t -> {
            Intent i = new Intent(this, TripDetailActivity.class);
            i.putExtra("tripId", t.id);
            startActivity(i);
        });
        rvTrips.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> {

            v.playSoundEffect(SoundEffectConstants.CLICK); // ✅ 播放系统点击音
            Intent i = new Intent(this, AddTripActivity.class);
            startActivity(i);
        });

        // Firebase
        db = FirebaseFirestore.getInstance();
    }

    @Override protected void onStart() {
        super.onStart();
        tripListener = db.collection("trips")
                .orderBy("createdAtClient", Query.Direction.DESCENDING) // ← 用客户端毫秒
                .limit(100)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        android.util.Log.e("Trips", "listen error", e);
                        return;
                    }
                    trips.clear();
                    for (com.google.firebase.firestore.DocumentSnapshot d : snap.getDocuments()) {
                        try {
                            // 看看关键字段的真实类型，定位“毒数据”
                            Object cAt = d.get("createdAt");
                            Object s   = d.get("startDate");
                            Object en  = d.get("endDate");
                            android.util.Log.d("Trips", d.getId() + " types => createdAt="
                                    + (cAt==null?"null":cAt.getClass().getName())
                                    + ", startDate=" + (s==null?"null":s.getClass().getName())
                                    + ", endDate="   + (en==null?"null":en.getClass().getName()));

                            Trip t = d.toObject(Trip.class); // 这里最容易抛异常
                            if (t != null) {
                                if (t.id == null) t.id = d.getId();
                                trips.add(t);
                            }
                        } catch (RuntimeException ex) {
                            // 关键：跳过坏文档，避免整页直接崩
                            android.util.Log.e("Trips", "Skip bad doc " + d.getId(), ex);
                        }
                    }
                    adapter.notifyDataSetChanged();
                });


    }

    @Override protected void onStop() {
        super.onStop();
        if (tripListener != null) {
            tripListener.remove();
            tripListener = null;
        }
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
