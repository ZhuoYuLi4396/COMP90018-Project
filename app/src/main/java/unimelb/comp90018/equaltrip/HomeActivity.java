package unimelb.comp90018.equaltrip;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeActivity extends AppCompatActivity {

    private TextView tvUsername, tvOngoingTripsNum, tvUnpaidBillsNum;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tvUsername = findViewById(R.id.tvUsername);
        tvOngoingTripsNum = findViewById(R.id.tvOngoingTripsNum);
        tvUnpaidBillsNum = findViewById(R.id.tvUnpaidBillsNum);

        // Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
            return;
        }

        // mock / Firestore 覆盖
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
                        if (userId != null && !userId.isEmpty()) {
                            tvUsername.setText(userId);
                        }
                        Long ongoing = doc.getLong("ongoingTrips");
                        if (ongoing != null) {
                            tvOngoingTripsNum.setText(" " + ongoing + " ");
                        }
                        Long unpaid = doc.getLong("unpaidBills");
                        if (unpaid != null) {
                            tvUnpaidBillsNum.setText(String.valueOf(unpaid));
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile.", Toast.LENGTH_SHORT).show());

        // ===== BottomNav：Home -> Profile / Trips =====
        BottomNavigationView bottom = findViewById(R.id.bottomNav);
        if (bottom != null) {
            bottom.setSelectedItemId(R.id.nav_home);        // 高亮 Home
            bottom.setOnItemReselectedListener(item -> {});  // 选中当前项不重复触发

            bottom.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    return true; // 已在 Home
                } else if (id == R.id.nav_profile) {
                    Intent i = new Intent(HomeActivity.this, ProfileActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(i);
                    overridePendingTransition(0, 0);
                    finish(); // 想保留返回栈可去掉
                    return true;
                } else if (id == R.id.nav_trips) {
                    Toast.makeText(this, "Trips coming soon", Toast.LENGTH_SHORT).show();
                    bottom.setSelectedItemId(R.id.nav_home); // 保持选中 Home
                    return false;
                }
                return false;
            });
        }
    }
}
