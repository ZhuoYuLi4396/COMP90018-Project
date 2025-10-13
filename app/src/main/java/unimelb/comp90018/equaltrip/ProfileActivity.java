package unimelb.comp90018.equaltrip;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    private SwitchMaterial swLocation, swNotification, swBluetooth, swCamera;
    private TextView tvCurrencyValue;

    // 资料头部
    private TextView tvProfileName, tvProfileEmail;
    private LinearLayout rowChangeName;

    private static final int RC_LOCATION = 101;
    private static final int RC_NOTI = 102;
    private static final int RC_BT = 103;
    private static final int RC_CAMERA = 104;

    // BottomNav
    private boolean suppressNav = false;
    private BottomNavigationView bottom;

    private String currency = "AUD Australian Dollar";
    private final String[] currencies = new String[]{
            "AUD Australian Dollar","USD United States Dollar","EUR Euro","CNY Chinese Yuan"
    };

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private DocumentReference attachedUserDoc; // 真正监听到的那条文档

    private TextView tvNameValue;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // ===== 绑定视图 =====
        bottom = findViewById(R.id.bottom_nav);

        tvProfileName  = findViewById(R.id.tvProfileName);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        rowChangeName  = findViewById(R.id.rowChangeName);
        tvNameValue  = findViewById(R.id.tvNameValue);
        LinearLayout rowName  = findViewById(R.id.rowName);

        if (rowName != null) {
            rowName.setOnClickListener(v ->
                    startActivity(new Intent(ProfileActivity.this, ChangeNameActivity.class)));
        }
        // 跳到改名页（容错：控件可能不存在）
        if (rowChangeName != null) {
            rowChangeName.setOnClickListener(v ->
                    startActivity(new Intent(ProfileActivity.this, ChangeNameActivity.class)));
        }
        TextView tvEdit = findViewById(R.id.tvEdit);
        if (tvEdit != null) {
            tvEdit.setOnClickListener(v ->
                    startActivity(new Intent(ProfileActivity.this, ChangeNameActivity.class)));
        }

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(ProfileActivity.this, SignUpActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });

        // ===== Firebase =====
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();

        if (currentUser == null) {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
            return;
        }

        // 先用 Auth 邮箱兜底，让 UI 立即有值
        String authEmail = currentUser.getEmail();
        tvProfileEmail.setText(authEmail != null ? authEmail : "No email");
        String fallbackName = (currentUser.getDisplayName() != null
                && !currentUser.getDisplayName().isEmpty())
                ? currentUser.getDisplayName() : "User";


        if (tvNameValue  != null) tvNameValue.setText(fallbackName);

        // 尝试附着用户文档并监听（支持文档ID不是uid的情况）
        attachUserProfileListener(currentUser.getUid());

        // ===== BottomNav（统一逻辑） =====
        if (bottom != null) {
            bottom.setOnItemSelectedListener(item -> {
                if (suppressNav) return true; // 程序化高亮时不导航
                int id = item.getItemId();
                if (id == R.id.nav_profile) return true; // 已在本页
                if (id == R.id.nav_home) {
                    startActivity(new Intent(this, HomeActivity.class)
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

            // 程序化高亮 Profile（不触发导航）
            suppressNav = true;
            bottom.getMenu().findItem(R.id.nav_profile).setChecked(true);
            bottom.post(() -> suppressNav = false);

            bottom.setOnItemReselectedListener(item -> { /* no-op */ });
        }
    }

    /** 同步用户资料：
     * 1) 先查 users/{uid}
     * 2) 若不存在，再查 users where uid == <auth uid> limit 1
     * 3) 找到文档后，挂 snapshotListener 实时更新 UI（userId 与 email）
     */
    private void attachUserProfileListener(String authUid) {
        DocumentReference docById = db.collection("users").document(authUid);
        docById.get().addOnSuccessListener(snap -> {
            if (snap != null && snap.exists()) {
                attachListenerOnDoc(docById);
            } else {
                db.collection("users")
                        .whereEqualTo("uid", authUid)
                        .limit(1)
                        .get()
                        .addOnSuccessListener(qs -> {
                            if (!qs.isEmpty()) {
                                DocumentReference realDoc = qs.getDocuments().get(0).getReference();
                                attachListenerOnDoc(realDoc);
                            } else {
                                String fallback = (currentUser.getDisplayName() != null &&
                                        !currentUser.getDisplayName().isEmpty())
                                        ? currentUser.getDisplayName() : "User";
                                tvProfileName.setText(fallback);
                            }
                        })
                        .addOnFailureListener(e -> {
                            String fallback = (currentUser.getDisplayName() != null &&
                                    !currentUser.getDisplayName().isEmpty())
                                    ? currentUser.getDisplayName() : "User";
                            tvProfileName.setText(fallback);
                        });
            }
        }).addOnFailureListener(e -> {
            String fallback = (currentUser.getDisplayName() != null &&
                    !currentUser.getDisplayName().isEmpty())
                    ? currentUser.getDisplayName() : "User";
            tvProfileName.setText(fallback);
        });
    }

    private void attachListenerOnDoc(DocumentReference userDoc) {
        attachedUserDoc = userDoc;
        attachedUserDoc.addSnapshotListener(this, (snap, e) -> {
            if (e != null || snap == null) return;
            if (!snap.exists()) return;

            // 名字：优先 userId
            String name = snap.getString("userId");
            if (name == null || name.trim().isEmpty()) {
                name = (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty())
                        ? currentUser.getDisplayName() : "User";
            }
            tvProfileName.setText(name);

            // ★ 把名字同步到“Name”那一行右侧的取值
            if (tvNameValue != null) tvNameValue.setText(name);

            // 邮箱：优先用文档字段 email，其次 Auth 邮箱
            String mail = snap.getString("email");
            if (mail == null || mail.trim().isEmpty()) {
                mail = currentUser.getEmail();
            }
            tvProfileEmail.setText(mail != null ? mail : "No email");
        });
    }

    // ===== 工具方法 =====

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }



    @Override protected void onResume() {
        super.onResume();
        if (bottom != null) {
            suppressNav = true;
            bottom.getMenu().findItem(R.id.nav_profile).setChecked(true);
            bottom.post(() -> suppressNav = false);
        }
    }
}