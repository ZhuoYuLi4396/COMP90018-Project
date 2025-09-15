package unimelb.comp90018.equaltrip;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
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

    private TextView tvUsername, tvOngoingTripsNum, tvUnpaidBillsNum;
    private FirebaseAuth mAuth;

    // Map
    private GoogleMap gmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tvUsername = findViewById(R.id.tvUsername);
        tvOngoingTripsNum = findViewById(R.id.tvOngoingTripsNum);
        tvUnpaidBillsNum = findViewById(R.id.tvUnpaidBillsNum);

        // ===== Firebase Auth & 显示数据 =====
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
            return;
        }

        // 默认值（随后被 Firestore 覆盖）
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

        // ===== Map 初始化 =====
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Toast.makeText(this, "Map container (mapFragment) not found in layout.", Toast.LENGTH_SHORT).show();
        }

        // ===== BottomNav：Home -> Profile / Trips =====
        BottomNavigationView bottom = findViewById(R.id.bottomNav);
        if (bottom != null) {
            bottom.setSelectedItemId(R.id.nav_home);
            bottom.setOnItemReselectedListener(item -> { /* no-op */ });
            bottom.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) return true;
                if (id == R.id.nav_profile) {
                    startActivity(new Intent(this, ProfileActivity.class));
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                }
                if (id == R.id.nav_trips) {
                    Toast.makeText(this, "Trips coming soon", Toast.LENGTH_SHORT).show();
                    bottom.setSelectedItemId(R.id.nav_home);
                    return false;
                }
                return false;
            });
        }
    }

    // ===== OnMapReadyCallback =====
    @Override
    public void onMapReady(GoogleMap map) {
        gmap = map;

        // basic UI
        gmap.getUiSettings().setZoomControlsEnabled(true);
        gmap.getUiSettings().setMapToolbarEnabled(false);

        // default cam Melbourne
        LatLng melbourne = new LatLng(-37.8136, 144.9631);
        gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(melbourne, 12f));

        // three markers
        gmap.addMarker(new MarkerOptions().position(melbourne).title("City"));
        gmap.addMarker(new MarkerOptions().position(new LatLng(-37.81, 144.99)).title("Trip A"));
        gmap.addMarker(new MarkerOptions().position(new LatLng(-37.83, 144.95)).title("Trip B"));

    }
}
