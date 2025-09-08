package unimelb.comp90018.equaltrip;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;


import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;


public class HomeActivity extends AppCompatActivity {

    private TextView tvUsername, tvOngoingTripsNum, tvUnpaidBillsNum;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home); // 确保使用的是你那份首页布局

        // 1) 绑定控件（注意这些 id 必须在 activity_home.xml 中存在）
        tvUsername = findViewById(R.id.tvUsername);
        tvOngoingTripsNum = findViewById(R.id.tvOngoingTripsNum);
        tvUnpaidBillsNum = findViewById(R.id.tvUnpaidBillsNum);

        // 2) Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // 未登录：返回注册（或登录）页；按你之前的流程这里走 SignUp
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
            return;
        }

        // 3) 默认占位数据（先能跑；以后替换成真实统计）
        tvOngoingTripsNum.setText(" 2 ");
        tvUnpaidBillsNum.setText("6 ");

        // 4) 先用 Firebase 的 displayName / email 前缀作为用户名
        String name = currentUser.getDisplayName();
        if (name == null || name.isEmpty()) {
            String email = currentUser.getEmail();
            if (email != null && email.contains("@")) {
                name = email.substring(0, email.indexOf('@'));
            }
        }
        tvUsername.setText((name != null && !name.isEmpty()) ? name : "User");

        // 5) 再尝试用 Firestore 覆盖显示名与统计（可选）
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
    }
}
