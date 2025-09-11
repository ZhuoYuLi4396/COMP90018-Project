package unimelb.comp90018.equaltrip;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;


public class HomeActivity extends AppCompatActivity{
    private TextView tvWelcome;
    private Button btnLogout;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // 初始化Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // 绑定控件
        tvWelcome = findViewById(R.id.tvWelcome);
//        btnLogout = findViewById(R.id.btnLogout);

        // Check whether the user is logged in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // Not logged in. Return to login page.
            finish();
            return;
        }

        // get user information from user
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String userId = documentSnapshot.getString("userId");
                        if (userId != null && !userId.isEmpty()) {
                            tvWelcome.setText("Welcome, " + userId + "!");
                        } else {
                            tvWelcome.setText("Welcome to EqualTrip!");
                        }
                    }
                });

        // Logout
//        btnLogout.setOnClickListener(v -> {
//            mAuth.signOut();
//            finish();
//            Toast.makeText(HomeActivity.this, "Logged out", Toast.LENGTH_SHORT).show();
//        });
        // 底部导航栏
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_home); // 当前页面高亮 Home

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true; // 已经在首页
            } else if (id == R.id.nav_trips) {
                startActivity(new Intent(this, TripPageActivity.class));
                overridePendingTransition(0,0);
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                overridePendingTransition(0,0);
                return true;
            }
            return true;
        });

    }

    @Override protected void onResume() {
        super.onResume();
        BottomNavigationView bottom = findViewById(R.id.bottom_nav);
        if (bottom != null) bottom.setSelectedItemId(R.id.nav_home);
    }


}
