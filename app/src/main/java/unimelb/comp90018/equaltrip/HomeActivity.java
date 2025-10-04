package unimelb.comp90018.equaltrip;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int RC_LOCATION = 201;

    // 顶部信息
    private TextView tvUsername, tvOngoingTripsNum, tvUnpaidBillsNum;

    private FirebaseAuth mAuth;
    private boolean suppressNav = false;

    // 地图
    private GoogleMap gmap;

    // BottomNav 成员，便于 onResume 处理
    private BottomNavigationView bottom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home); // XML 里有 bottomNav / mapFragment / 文本控件

        // ===== 绑定视图 =====
        tvUsername        = findViewById(R.id.tvUsername);
        tvOngoingTripsNum = findViewById(R.id.tvOngoingTripsNum);
        tvUnpaidBillsNum  = findViewById(R.id.tvUnpaidBillsNum);

        // ===== Firebase Auth & 文案 =====
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
            return;
        }

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

        // ===== 地图初始化 =====
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Toast.makeText(this, "Map container (mapFragment) not found in layout.", Toast.LENGTH_SHORT).show();
        }

        // ===== BottomNav（统一逻辑） =====
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

            // 程序化高亮 Home（不触发导航）
            suppressNav = true;
            bottom.getMenu().findItem(R.id.nav_home).setChecked(true);
            bottom.post(() -> suppressNav = false);

            bottom.setOnItemReselectedListener(item -> { /* no-op */ });
        }
    }

    // ===== 地图就绪 =====
    @Override
    public void onMapReady(GoogleMap map) {
        gmap = map;

        // 基本 UI
        gmap.getUiSettings().setZoomControlsEnabled(true);
        gmap.getUiSettings().setMapToolbarEnabled(false);
        gmap.getUiSettings().setCompassEnabled(true);

        // 默认定位：Melbourne
        LatLng melbourne = new LatLng(-37.8136, 144.9631);
        gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(melbourne, 12f));
        gmap.addMarker(new MarkerOptions().position(melbourne).title("Melbourne"));

        // 点击地图落点
        gmap.setOnMapClickListener(latLng -> {
            gmap.clear();
            gmap.addMarker(new MarkerOptions().position(latLng)
                    .title(String.format("Lat: %.5f, Lng: %.5f", latLng.latitude, latLng.longitude)));
            gmap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
        });

        // 蓝点（需权限）
        enableMyLocationIfGranted();
    }

    // ===== 定位权限 & 蓝点 =====
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

    @Override protected void onResume() {
        super.onResume();
        if (bottom != null) {
            suppressNav = true;
            bottom.getMenu().findItem(R.id.nav_home).setChecked(true);
            bottom.post(() -> suppressNav = false);
        }
    }
}
