package unimelb.comp90018.equaltrip;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
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
import com.google.firebase.firestore.DocumentReference;

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

    // Fused 定位
    private FusedLocationProviderClient fusedClient;
    private Double currentLat = null, currentLon = null;
    private TextView tvCurrentTripId;

    @Nullable private ListenerRegistration userProfileReg = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // 顶部文案
        tvUsername        = findViewById(R.id.tvUsername);
        //tvOngoingTripsNum = findViewById(R.id.tvOngoingTripsNum);
        //tvUnpaidBillsNum  = findViewById(R.id.tvUnpaidBillsNum);
        tvCurrentTripId   = findViewById(R.id.tvCurrentTripId);
        if (tvCurrentTripId != null) tvCurrentTripId.setText("");

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
        //tvOngoingTripsNum.setText(" 2 ");
        //tvUnpaidBillsNum.setText("6 ");
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

        // —— 主页 Add Bill：跳到“最新 Trip”的 AddBillActivity
        findViewById(R.id.btnAddBill).setOnClickListener(v -> openAddBillForLatestTrip());

        // —— 主页 New Trip：跳转到 AddTripActivity  ✅ 新增
        findViewById(R.id.btnNewTrip).setOnClickListener(v -> {
            startActivity(new Intent(this, AddTripActivity.class));
        });

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
        attachUserProfileListener();
        startWatchingLatestTrip();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachUserProfileListener();
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
        // 如果因为内存或生命周期导致 bills 监听被移除，这里补挂一次，确保“刚添加完 bill 就能看到图钉”
        if (activeTripId != null && billsRegTrip == null) {
            attachBillsListenerForTrip(activeTripId);
        }
    }

    // ---------------- Map ----------------
    /*
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
    */

    @Override
    public void onMapReady(GoogleMap map) {
        gmap = map;
        mapReady = true;

        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        gmap.getUiSettings().setZoomControlsEnabled(true);
        gmap.getUiSettings().setCompassEnabled(true);
        gmap.getUiSettings().setMapToolbarEnabled(false);

        // 默认中心：墨尔本市中心
        LatLng melbourne = new LatLng(-37.8136, 144.9631);
        gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(melbourne, 12f));

        // ✅ Android 13+ 需要延迟执行权限检查，否则不弹框
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAndEnableMyLocation, 500);
    }

    /** Android 13+ 兼容版权限检测与启用逻辑 **/
    private void checkAndEnableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try { if (gmap != null) gmap.setMyLocationEnabled(true); } catch (SecurityException ignore) {}

            // ← 新增：取一次 lastLocation 作为基准（不弹权限框）
            fusedClient.getLastLocation().addOnSuccessListener(loc -> {
                if (loc != null) {
                    currentLat = loc.getLatitude();
                    currentLon = loc.getLongitude();
                    // 同时存一份到 SharedPreferences，防止后续 Activity 启动时没有 extras
                    getSharedPreferences("baseline_loc", MODE_PRIVATE)
                            .edit()
                            .putString("lat", String.valueOf(currentLat))
                            .putString("lon", String.valueOf(currentLon))
                            .apply();
                }
            });

        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
                    RC_LOCATION
            );
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
        if (requestCode == RC_LOCATION) {
            boolean fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            boolean coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;

            if (fineGranted || coarseGranted) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { if (gmap != null) gmap.setMyLocationEnabled(true); } catch (SecurityException ignore) {}

                    // ✅ 新增：权限刚授予时，也取一次 lastLocation 并缓存，确保后面能带到 AddBillActivity
                    if (fusedClient != null) {
                        fusedClient.getLastLocation().addOnSuccessListener(loc -> {
                            if (loc != null) {
                                currentLat = loc.getLatitude();
                                currentLon = loc.getLongitude();
                                getSharedPreferences("baseline_loc", MODE_PRIVATE)
                                        .edit()
                                        .putString("lat", String.valueOf(currentLat))
                                        .putString("lon", String.valueOf(currentLon))
                                        .apply();
                            }
                        });
                    }
                }, 300);
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }

        }
    }
    private void enableMyLocationIfGranted() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (fine || coarse) {
            actuallyEnableMyLocation();
        } else {
            // 请求时加上 coarse 一起
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    RC_LOCATION
            );
        }
    }

    /** 将首页的个人坐标塞进 Intent：优先用当前内存的 currentLat/Lon，其次用 SharedPreferences 兜底 */
    private void putBaselineExtras(Intent i) {
        if (currentLat != null && currentLon != null) {
            i.putExtra("base_lat", currentLat);
            i.putExtra("base_lon", currentLon);
            return;
        }
        String plat = getSharedPreferences("baseline_loc", MODE_PRIVATE).getString("lat", null);
        String plon = getSharedPreferences("baseline_loc", MODE_PRIVATE).getString("lon", null);
        if (plat != null && plon != null) {
            try {
                i.putExtra("base_lat", Double.parseDouble(plat));
                i.putExtra("base_lon", Double.parseDouble(plon));
            } catch (Exception ignored) {}
        }
    }


    @android.annotation.SuppressLint("MissingPermission")
    private void actuallyEnableMyLocation() {
        if (gmap != null) gmap.setMyLocationEnabled(true);
    }

    /*
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(requestCode, perms, res);
        if (requestCode == RC_LOCATION) {
            boolean granted = false;
            for (int r : res) {
                if (r == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }

            if (granted) {
                new android.os.Handler().postDelayed(this::actuallyEnableMyLocation, 300);
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
    */

    // ---------------- 最新 trip 选择 + bills 监听 ----------------

    private void startWatchingLatestTrip() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        final String myUid = user.getUid();
        final String myEmailLower = (user.getEmail() == null) ? "" : user.getEmail().toLowerCase(Locale.ROOT);


        if (latestOwnerReg != null) { latestOwnerReg.remove(); latestOwnerReg = null; }
        if (latestInvitedReg != null) { latestInvitedReg.remove(); latestInvitedReg = null; }


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
        activeTripId = null;
        if (tvCurrentTripId != null) tvCurrentTripId.setText("--"); // 保留
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
        // 若 trip 相同且监听还在，直接返回；否则强制重挂
        if (sameTrip && billsRegTrip != null) {
            updateCurrentTripDisplay(activeTripId);
            return;
        }

        activeTripId = newId;
        updateCurrentTripDisplay(activeTripId);
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

    // ---------------- 主页 Add Bill → 最新 Trip ----------------

    private void openAddBillForLatestTrip() {
        if (activeTripId != null && !activeTripId.trim().isEmpty()) {
            Intent i = new Intent(this, AddBillActivity.class);
            i.putExtra("tripId", activeTripId);
            putBaselineExtras(i); // ★ 带上个人坐标
            startActivity(i);
            return;
        }
        fetchLatestTripIdOnce(newId -> {
            if (newId != null && !newId.trim().isEmpty()) {
                Intent i = new Intent(this, AddBillActivity.class);
                i.putExtra("tripId", newId);
                putBaselineExtras(i); // ★ 带上个人坐标
                startActivity(i);
            } else {
                Toast.makeText(this, "No recent trip found. Create or open a trip first.", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void fetchLatestTripIdOnce(SimpleCallback<String> cb) {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { cb.onResult(null); return; }

        final String myUid = u.getUid();
        final String myEmailLower = (u.getEmail() == null) ? "" : u.getEmail().toLowerCase(Locale.ROOT);

        final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(2);
        final Trip[] ownerHolder   = new Trip[1];
        final Trip[] invitedHolder = new Trip[1];

        db.collection("trips")
                .whereEqualTo("ownerId", myUid)
                .orderBy("createdAtClient", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap != null && !snap.isEmpty()) {
                        DocumentSnapshot d = snap.getDocuments().get(0);
                        Trip t = d.toObject(Trip.class);
                        if (t != null) { if (t.id == null) t.id = d.getId(); ownerHolder[0] = t; }
                    }
                })
                .addOnCompleteListener(task -> {
                    if (pending.decrementAndGet() == 0) {
                        Trip pick = pickLatest(ownerHolder[0], invitedHolder[0]);
                        cb.onResult(pick == null ? null : pick.id);
                    }
                });

        db.collection("trips")
                .whereArrayContains("tripmates", myEmailLower)
                .orderBy("createdAtClient", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap != null && !snap.isEmpty()) {
                        DocumentSnapshot d = snap.getDocuments().get(0);
                        Trip t = d.toObject(Trip.class);
                        if (t != null) { if (t.id == null) t.id = d.getId(); invitedHolder[0] = t; }
                    }
                })
                .addOnCompleteListener(task -> {
                    if (pending.decrementAndGet() == 0) {
                        Trip pick = pickLatest(ownerHolder[0], invitedHolder[0]);
                        cb.onResult(pick == null ? null : pick.id);
                    }
                });
    }


    /** 用 tripId 读取一次 trips/{id} ，把“Current trip ID”改为显示 trip name（无则回退到 id） */
    private void updateCurrentTripDisplay(@Nullable String tripId) {
        if (tvCurrentTripId == null) return;

        if (tripId == null || tripId.trim().isEmpty()) {
            tvCurrentTripId.setText("--");
            return;
        }

        // 读取 trips/{tripId} 拿到 name
        db.collection("trips").document(tripId)
                .get()
                .addOnSuccessListener(doc -> {
                    String display = null;
                    if (doc != null && doc.exists()) {
                        // 常见命名都兜一下
                        display = doc.getString("tripName");
                        if (display == null || display.trim().isEmpty()) display = doc.getString("name");
                        if (display == null || display.trim().isEmpty()) display = doc.getString("title");
                    }
                    tvCurrentTripId.setText(
                            (display != null && !display.trim().isEmpty()) ? display : tripId
                    );
                })
                .addOnFailureListener(e -> tvCurrentTripId.setText(tripId));
    }

    private void attachUserProfileListener() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;

        // 先卸载旧监听，避免重复
        if (userProfileReg != null) { userProfileReg.remove(); userProfileReg = null; }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference docById = db.collection("users").document(u.getUid());

        // 先尝试按 docId = uid 监听
        docById.get().addOnSuccessListener(snap -> {
            if (snap != null && snap.exists()) {
                userProfileReg = docById.addSnapshotListener(this, (ds, e) -> {
                    if (e != null || ds == null || !ds.exists()) return;
                    String name = ds.getString("userId");
                    if (name == null || name.trim().isEmpty()) {
                        // 兜底：Auth 显示名或邮箱前缀
                        name = (u.getDisplayName() != null && !u.getDisplayName().isEmpty())
                                ? u.getDisplayName()
                                : (u.getEmail() != null && u.getEmail().contains("@")
                                ? u.getEmail().substring(0, u.getEmail().indexOf('@'))
                                : "User");
                    }
                    if (tvUsername != null) tvUsername.setText(name);
                });
            } else {
                // 若 docId 不是 uid，则回退用 where uid == <auth uid> 再监听
                db.collection("users")
                        .whereEqualTo("uid", u.getUid())
                        .limit(1)
                        .get()
                        .addOnSuccessListener(qs -> {
                            if (!qs.isEmpty()) {
                                DocumentReference realDoc = qs.getDocuments().get(0).getReference();
                                userProfileReg = realDoc.addSnapshotListener(this, (ds, e) -> {
                                    if (e != null || ds == null || !ds.exists()) return;
                                    String name = ds.getString("userId");
                                    if (name == null || name.trim().isEmpty()) {
                                        name = (u.getDisplayName() != null && !u.getDisplayName().isEmpty())
                                                ? u.getDisplayName()
                                                : (u.getEmail() != null && u.getEmail().contains("@")
                                                ? u.getEmail().substring(0, u.getEmail().indexOf('@'))
                                                : "User");
                                    }
                                    if (tvUsername != null) tvUsername.setText(name);
                                });
                            }
                        });
            }
        });
    }

    private void detachUserProfileListener() {
        if (userProfileReg != null) { userProfileReg.remove(); userProfileReg = null; }
    }


    @Nullable
    private Trip pickLatest(@Nullable Trip owner, @Nullable Trip invited) {
        long ot = (owner==null || owner.createdAtClient==null) ? Long.MIN_VALUE : owner.createdAtClient;
        long it = (invited==null || invited.createdAtClient==null) ? Long.MIN_VALUE : invited.createdAtClient;
        return (ot >= it) ? owner : invited;
    }

    private interface SimpleCallback<T> { void onResult(@Nullable T value); }
}