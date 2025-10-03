package unimelb.comp90018.equaltrip;

// Author: Jinglin Lei
// Date: 2025-09-05
// Notes:
// - 本版加入本地搜索：仅按 Trip.name 过滤（不区分大小写）
// - 输入框 id: et_search（TextInputEditText）
// - 删除了右上角 filter 图标后，search_bar 的 End 约束需连到 parent

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.SoundEffectConstants;

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

    // 两个监听：owner 和 invited
    private ListenerRegistration ownerReg;
    private ListenerRegistration invitedReg;

    // 合并两路数据、去重
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

        // 程序化选中“Trips”且不触发监听
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
        fabAdd  = findViewById(R.id.fab_add_trip);
        etSearch = findViewById(R.id.et_search);

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

        // 搜索框：仅按名称过滤（本地）
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    currentQuery = (s == null) ? "" : s.toString().trim();
                    mergeAndPublish(); // 输入变化即刷新过滤
                }
            });
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
            bottom.setSelectedItemId(R.id.nav_trips);
            bottom.getMenu().findItem(R.id.nav_trips).setChecked(true);
            bottom.post(() -> suppressNav = false);
        }
    }

    // 合并 → 排序 → 仅按 name 过滤 → 发布
    private void mergeAndPublish() {
        // 1) 合并
        List<Trip> merged = new ArrayList<>(byId.values());

        // 2) 排序：createdAtClient 降序（空视为 0）
        merged.sort((a, b) -> Long.compare(
                b.createdAtClient == null ? 0L : b.createdAtClient,
                a.createdAtClient == null ? 0L : a.createdAtClient
        ));

        // 3) 仅按 name 过滤（不区分大小写；其他字段不参与）
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
}
