package unimelb.comp90018.equaltrip;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
    }
}
