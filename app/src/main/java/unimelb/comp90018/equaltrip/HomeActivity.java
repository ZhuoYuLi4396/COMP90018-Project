package unimelb.comp90018.equaltrip;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int RC_LOCATION = 201;

    private TextView tvUsername, tvOngoingTripsNum, tvUnpaidBillsNum;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Bottom nav
    private BottomNavigationView bottom;
    private boolean suppressNav = false;

    // Map
    private GoogleMap gmap;
    private boolean mapReady = false;

    // ---- 最新 trip 选择 & 监听 ----
    @Nullable private ListenerRegistration latestOwnerReg = null;
    @Nullable private ListenerRegistration latestInvitedReg = null;
    @Nullable private ListenerRegistration billsRegTrip   = null;

    @Nullable private Trip ownerLatest = null;
    @Nullable private Trip invitedLatest = null;

    @Nullable private String activeTripId = null; // 当前已附着 bills 监听的 tripId

    // markers 合并容器：docPath -> LatLng/Title
    private final Map<String, LatLng> currentMarkers = new HashMap<>();
    private final Map<String, String> currentTitles  = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // 顶部文案
        tvUsername        = findViewById(R.id.tvUsername);
        tvOngoingTripsNum = findViewById(R.id.tvOngoingTripsNum);
        tvUnpaidBillsNum  = findViewById(R.id.tvUnpaidBillsNum);

        // Firebase
        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
            return;
        }

        // 顶部示例文案
        tvOngoingTripsNum.setText(" 2 ");
        tvUnpaidBillsNum.setText("6 ");
        String name = currentUser.getDisplayName();
        if (name == null || name.isEmpty()) {
            String email = currentUser.getEmail();
            if (email != null && email.contains("@")) {
                name = email.substring(0, email.indexOf('@'));
            }
        }
        tvUsername.setText((name != null && !name.isEmpty()) ? name : "User");

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String userId = doc.getString("userId");
                        if (userId != null && !userId.isEmpty()) tvUsername.setText(userId);
                        Long ongoing = doc.getLong("ongoingTrips");
                        if (ongoing != null) tvOngoingTripsNum.setText(" " + ongoing + " ");
                        Long unpaid = doc.getLong("unpaidBills");
                        if (unpaid != null) tvUnpaidBillsNum.setText(String.valueOf(unpaid));
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile.", Toast.LENGTH_SHORT).show());

        // Map
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Toast.makeText(this, "Map container (mapFragment) not found.", Toast.LENGTH_SHORT).show();
        }

        // BottomNav —— 使用 include_bottom_nav 里的 @id/bottom_nav
        bottom = findViewById(R.id.bottom_nav);
        if (bottom != null) {
            bottom.setOnItemSelectedListener(item -> {
                if (suppressNav) return true; // 程序化高亮时不导航
                int id = item.getItemId();
                if (id == R.id.nav_home) return true;
                if (id == R.id.nav_profile) {
                    startActivity(new Intent(this, ProfileActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                    overridePendingTransition(0, 0);
                    return true;
                }
                if (id == R.id.nav_trips) {
                    startActivity(new Intent(this, TripPageActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                    overridePendingTransition(0, 0);
                    return true;
                }
                return false;
            });
            // 程序化高亮 Home
            suppressNav = true;
            bottom.getMenu().findItem(R.id.nav_home).setChecked(true);
            bottom.post(() -> suppressNav = false);
            bottom.setOnItemReselectedListener(item -> { /* no-op */ });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 回到首页时，开始监听“我最新的 trip”，并据此附着 bills 监听
        startWatchingLatestTrip();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopWatchingLatestTrip();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopWatchingLatestTrip();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottom != null) {
            suppressNav = true;
            bottom.getMenu().findItem(R.id.nav_home).setChecked(true);
            bottom.post(() -> suppressNav = false);
        }
    }

    // ---------------- Map ----------------
    @Override
    public void onMapReady(GoogleMap map) {
        gmap = map;
        mapReady = true;

        gmap.getUiSettings().setZoomControlsEnabled(true);
        gmap.getUiSettings().setMapToolbarEnabled(false);
        gmap.getUiSettings().setCompassEnabled(true);

        // 默认 Melbourne
        LatLng melbourne = new LatLng(-37.8136, 144.9631);
        gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(melbourne, 12f));
        gmap.addMarker(new MarkerOptions().position(melbourne).title("Melbourne"));

        enableMyLocationIfGranted();
    }

    private void enableMyLocationIfGranted() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (fine || coarse) {
            actuallyEnableMyLocation();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    RC_LOCATION
            );
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void actuallyEnableMyLocation() {
        if (gmap != null) gmap.setMyLocationEnabled(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(requestCode, perms, res);
        if (requestCode == RC_LOCATION) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
                actuallyEnableMyLocation();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ---------------- 最新 trip 选择 + bills 监听 ----------------

    private void startWatchingLatestTrip() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        final String myUid = user.getUid();
        final String myEmailLower = (user.getEmail() == null) ? "" : user.getEmail().toLowerCase(Locale.ROOT);

        // 先解绑旧的“最新 trip”监听，避免重复
        if (latestOwnerReg != null) { latestOwnerReg.remove(); latestOwnerReg = null; }
        if (latestInvitedReg != null) { latestInvitedReg.remove(); latestInvitedReg = null; }

        // owner 最新 1 条
        latestOwnerReg = db.collection("trips")
                .whereEqualTo("ownerId", myUid)
                .orderBy("createdAtClient", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;
                    ownerLatest = null;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Trip t = d.toObject(Trip.class);
                        if (t != null) {
                            if (t.id == null) t.id = d.getId();
                            ownerLatest = t;
                        }
                    }
                    decideActiveTripAndAttach(ownerLatest, invitedLatest);
                });

        // 被邀请（按邮箱）最新 1 条
        latestInvitedReg = db.collection("trips")
                .whereArrayContains("tripmates", myEmailLower)
                .orderBy("createdAtClient", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;
                    invitedLatest = null;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Trip t = d.toObject(Trip.class);
                        if (t != null) {
                            if (t.id == null) t.id = d.getId();
                            invitedLatest = t;
                        }
                    }
                    decideActiveTripAndAttach(ownerLatest, invitedLatest);
                });
    }

    private void stopWatchingLatestTrip() {
        if (latestOwnerReg != null) { latestOwnerReg.remove(); latestOwnerReg = null; }
        if (latestInvitedReg != null) { latestInvitedReg.remove(); latestInvitedReg = null; }
        detachBillsListener();

        // ★ 关键修复：清空 activeTripId，保证回到首页会强制重挂 bills 监听
        activeTripId = null;
    }

    private void detachBillsListener() {
        if (billsRegTrip != null) {
            billsRegTrip.remove();
            billsRegTrip = null;
        }
    }

    private void decideActiveTripAndAttach(@Nullable Trip owner, @Nullable Trip invited) {
        long ownerTs  = (owner   == null || owner.createdAtClient   == null) ? Long.MIN_VALUE : owner.createdAtClient;
        long invitTs  = (invited == null || invited.createdAtClient == null) ? Long.MIN_VALUE : invited.createdAtClient;
        Trip pick = (ownerTs >= invitTs) ? owner : invited;

        String newId = (pick == null) ? null : pick.id;

        boolean sameTrip = (activeTripId != null && activeTripId.equals(newId));
        // ★ 关键修复：如果 trip 相同但监听已被移除（例如 onStop() 后），也要强制重挂
        if (sameTrip && billsRegTrip != null) {
            return; // 已有监听且 trip 未变化
        }

        activeTripId = newId;
        attachBillsListenerForTrip(activeTripId);
    }

    private void attachBillsListenerForTrip(@Nullable String tripId) {
        // 先解绑旧的
        detachBillsListener();

        // 清空地图标记缓存
        currentMarkers.clear();
        currentTitles.clear();
        redrawAllMarkers();

        if (tripId == null || tripId.trim().isEmpty()) return;

        CollectionReference bills = db.collection("trips").document(tripId).collection("bills");
        billsRegTrip = bills.addSnapshotListener((snap, err) -> {
            if (err != null || snap == null || gmap == null) return;

            // 按子集合里所有账单画点
            currentMarkers.clear();
            currentTitles.clear();

            for (QueryDocumentSnapshot doc : snap) {
                LatLng p = extractLatLng(doc.get("geo"));
                if (p != null) {
                    String key = doc.getReference().getPath();
                    currentMarkers.put(key, p);
                    currentTitles.put(key, buildTitle(doc));
                }
            }
            redrawAllMarkers();
        });
    }

    // -------------- 地图绘制 --------------

    private void redrawAllMarkers() {
        if (gmap == null) return;

        gmap.clear();

        if (currentMarkers.isEmpty()) return;

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (Map.Entry<String, LatLng> e : currentMarkers.entrySet()) {
            String key = e.getKey();
            LatLng p   = e.getValue();
            String title = currentTitles.get(key);
            if (title == null || title.isEmpty()) title = "Bill";

            gmap.addMarker(new MarkerOptions().position(p).title(title));
            bounds.include(p);
        }

        try {
            gmap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100));
        } catch (Exception ignore) {
            LatLng first = currentMarkers.values().iterator().next();
            gmap.animateCamera(CameraUpdateFactory.newLatLngZoom(first, 14f));
        }
    }

    @Nullable
    private LatLng extractLatLng(Object geoObj) {
        if (!(geoObj instanceof Map)) return null;
        Map<?, ?> geo = (Map<?, ?>) geoObj;

        Double lat = coerceDouble(geo.get("lat"));
        Double lon = coerceDouble(geo.get("lon"));
        if (lat == null || lon == null) return null;
        if (Math.abs(lat) < 1e-8 && Math.abs(lon) < 1e-8) return null;

        return new LatLng(lat, lon);
    }

    @Nullable
    private Double coerceDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Double)   return (Double) v;
        if (v instanceof Float)    return ((Float) v).doubleValue();
        if (v instanceof Long)     return ((Long) v).doubleValue();
        if (v instanceof Integer)  return ((Integer) v).doubleValue();
        if (v instanceof String) {
            try { return Double.parseDouble((String) v); } catch (Exception ignored) {}
        }
        return null;
    }

    private String buildTitle(QueryDocumentSnapshot doc) {
        String title = doc.getString("billName");
        if (title == null || title.isEmpty()) title = doc.getString("merchant");
        if (title == null) title = "Bill";
        return title;
    }
}
