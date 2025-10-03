package unimelb.comp90018.equaltrip;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
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

    private GoogleMap gmap;
    private boolean mapReady = false; // ✅ 新增：记录地图是否已就绪

    // Firestore 实时监听（两路：我参与、我支付）
    @Nullable private ListenerRegistration billsRegParticipants = null;
    @Nullable private ListenerRegistration billsRegPayer = null;

    // 合并容器：docPath -> LatLng/Title
    private final Map<String, com.google.android.gms.maps.model.LatLng> currentMarkers = new HashMap<>();
    private final Map<String, String> currentTitles  = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tvUsername        = findViewById(R.id.tvUsername);
        tvOngoingTripsNum = findViewById(R.id.tvOngoingTripsNum);
        tvUnpaidBillsNum  = findViewById(R.id.tvUnpaidBillsNum);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
            return;
        }

        // 顶部文案（原逻辑）
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

        // 地图
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Toast.makeText(this, "Map container (mapFragment) not found.", Toast.LENGTH_SHORT).show();
        }

        // BottomNav
        BottomNavigationView bottom = findViewById(R.id.bottomNav);
        if (bottom != null) {
            bottom.setSelectedItemId(R.id.nav_home);
            bottom.setOnItemReselectedListener(item -> { /* no-op */ });
            bottom.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    return true;
                } else if (id == R.id.nav_trips) {
                    startActivity(new Intent(this, TripPageActivity.class));
                    overridePendingTransition(0, 0);
                    return true;
                } else if (id == R.id.nav_profile) {
                    startActivity(new Intent(this, ProfileActivity.class));
                    overridePendingTransition(0, 0);
                    return true;
                }
                return false;
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // ✅ 关键：回到首页时，如果地图已就绪，就重新挂监听，立刻能收到新 bill 更新
        if (mapReady && gmap != null) {
            startListeningMyBills();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView bottom = findViewById(R.id.bottomNav);
        if (bottom != null) bottom.setSelectedItemId(R.id.nav_home);
    }

    // 地图回调
    @Override
    public void onMapReady(GoogleMap map) {
        gmap = map;
        mapReady = true; // ✅ 标记地图已就绪

        gmap.getUiSettings().setZoomControlsEnabled(true);
        gmap.getUiSettings().setMapToolbarEnabled(false);
        gmap.getUiSettings().setCompassEnabled(true);

        // 默认 Melbourne（首次加载时）
        LatLng melbourne = new LatLng(-37.8136, 144.9631);
        gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(melbourne, 12f));
        gmap.addMarker(new MarkerOptions().position(melbourne).title("Melbourne"));

        enableMyLocationIfGranted();

        // 首次地图就绪也挂一次
        startListeningMyBills();
    }

    // —— 两路监听：我参与 + 我支付 —— //
    private void startListeningMyBills() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || gmap == null) return;

        String myUid = user.getUid();

        // 先清旧监听，避免重复
        stopListeningMyBills();

        // 不清空 currentMarkers，让另一路的结果能够合并；但首次进入可以清空再画
        currentMarkers.clear();
        currentTitles.clear();

        // A. 我参与的
        billsRegParticipants = db.collectionGroup("bills")
                .whereArrayContains("participants", myUid)
                .addSnapshotListener((snap, err) -> {
                    if (err != null || snap == null) return;

                    // 先移除这路里不再存在的文档（用路径区分）
                    Set<String> alive = new HashSet<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        String key = doc.getReference().getPath();
                        alive.add(key);
                        LatLng p = extractLatLng(doc.get("geo"));
                        if (p != null) {
                            currentMarkers.put(key, p);
                            currentTitles.put(key, buildTitle(doc));
                        } else {
                            currentMarkers.remove(key);
                            currentTitles.remove(key);
                        }
                    }
                    // 不在快照中的旧 key（但可能被 payer 路径保留），这里不强删，让合并逻辑决定

                    redrawAllMarkers();
                });

        // B. 我支付的
        billsRegPayer = db.collectionGroup("bills")
                .whereEqualTo("paidBy", myUid)
                .addSnapshotListener((snap, err) -> {
                    if (err != null || snap == null) return;

                    for (QueryDocumentSnapshot doc : snap) {
                        String key = doc.getReference().getPath();
                        LatLng p = extractLatLng(doc.get("geo"));
                        if (p != null) {
                            currentMarkers.put(key, p);
                            currentTitles.put(key, buildTitle(doc));
                        } else {
                            currentMarkers.remove(key);
                            currentTitles.remove(key);
                        }
                    }
                    redrawAllMarkers();
                });
    }

    private void stopListeningMyBills() {
        if (billsRegParticipants != null) {
            billsRegParticipants.remove();
            billsRegParticipants = null;
        }
        if (billsRegPayer != null) {
            billsRegPayer.remove();
            billsRegPayer = null;
        }
    }

    private void redrawAllMarkers() {
        if (gmap == null) return;

        gmap.clear();

        if (currentMarkers.isEmpty()) return;

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (Map.Entry<String, LatLng> e : currentMarkers.entrySet()) {
            String key = e.getKey();
            LatLng p = e.getValue();
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
        Map<?,?> geo = (Map<?,?>) geoObj;

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

    // 定位权限
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

    @SuppressLint("MissingPermission")
    private void actuallyEnableMyLocation() {
        if (gmap != null) gmap.setMyLocationEnabled(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                actuallyEnableMyLocation();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 仍然在 onStop 里移除监听，避免泄漏
        stopListeningMyBills();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListeningMyBills();
    }
}
