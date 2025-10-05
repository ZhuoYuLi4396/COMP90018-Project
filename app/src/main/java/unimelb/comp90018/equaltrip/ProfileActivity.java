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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // ===== 绑定视图 =====
        swLocation      = findViewById(R.id.swLocation);
        swNotification  = findViewById(R.id.swNotification);
        swBluetooth     = findViewById(R.id.swBluetooth);
        swCamera        = findViewById(R.id.swCamera);
        tvCurrencyValue = findViewById(R.id.tvCurrencyValue);
        LinearLayout rowCurrency = findViewById(R.id.rowCurrency);
        bottom = findViewById(R.id.bottomNav);   // 和 XML 一致的 id

        tvProfileName  = findViewById(R.id.tvProfileName);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        rowChangeName  = findViewById(R.id.rowChangeName);

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

        // 尝试附着用户文档并监听（支持文档ID不是uid的情况）
        attachUserProfileListener(currentUser.getUid());

        // ===== 权限开关初始化 =====
        swLocation.setChecked(hasPermission(Manifest.permission.ACCESS_FINE_LOCATION));
        if (Build.VERSION.SDK_INT >= 33) {
            swNotification.setChecked(hasPermission(Manifest.permission.POST_NOTIFICATIONS));
        } else swNotification.setChecked(true);
        if (Build.VERSION.SDK_INT >= 31) {
            swBluetooth.setChecked(hasPermission(Manifest.permission.BLUETOOTH_CONNECT));
        } else swBluetooth.setChecked(true);
        swCamera.setChecked(hasPermission(Manifest.permission.CAMERA));

        // 货币
        tvCurrencyValue.setText(currency);
        rowCurrency.setOnClickListener(v -> showCurrencyDialog());

        // 监听
        swLocation.setOnCheckedChangeListener((b, c) -> {
            if (c) requestIfNeeded(Manifest.permission.ACCESS_FINE_LOCATION, RC_LOCATION);
            else toast("Location usage turned off in app");
        });
        swNotification.setOnCheckedChangeListener((b, c) -> {
            if (Build.VERSION.SDK_INT >= 33) {
                if (c) requestIfNeeded(Manifest.permission.POST_NOTIFICATIONS, RC_NOTI);
                else toast("Notifications turned off in app");
            } else toast("Notifications preference updated");
        });
        swBluetooth.setOnCheckedChangeListener((b, c) -> {
            if (Build.VERSION.SDK_INT >= 31) {
                if (c) requestIfNeeded(Manifest.permission.BLUETOOTH_CONNECT, RC_BT);
                else toast("Bluetooth usage turned off in app");
            } else toast("Bluetooth preference updated");
        });
        swCamera.setOnCheckedChangeListener((b, c) -> {
            if (c) requestIfNeeded(Manifest.permission.CAMERA, RC_CAMERA);
            else toast("Camera usage turned off in app");
        });

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
                String fallback = (currentUser.getDisplayName() != null &&
                        !currentUser.getDisplayName().isEmpty())
                        ? currentUser.getDisplayName() : "User";
                tvProfileName.setText(fallback);
            } else {
                tvProfileName.setText(name);
            }

            // 邮箱：优先用文档字段 email，其次 Auth 邮箱
            String mail = snap.getString("email");
            if (mail == null || mail.trim().isEmpty()) {
                mail = currentUser.getEmail();
            }
            tvProfileEmail.setText(mail != null ? mail : "No email");
        });
    }

    // ===== 工具方法 =====
    private boolean hasPermission(String perm) {
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED;
    }
    private void requestIfNeeded(String perm, int rc) {
        if (!hasPermission(perm) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{perm}, rc);
        }
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void showCurrencyDialog() {
        int selected = 0;
        for (int i = 0; i < currencies.length; i++) if (currencies[i].equals(currency)) { selected = i; break; }
        final int[] tmp = {selected};
        new AlertDialog.Builder(this)
                .setTitle("Choose currency")
                .setSingleChoiceItems(currencies, selected, (d, which) -> tmp[0] = which)
                .setPositiveButton("OK", (d, w) -> {
                    currency = currencies[tmp[0]];
                    tvCurrencyValue.setText(currency);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        boolean granted = r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED;
        if (rc == RC_LOCATION) {
            swLocation.setChecked(granted);
            if (!granted) toast("Location permission denied");
        } else if (rc == RC_NOTI) {
            swNotification.setChecked(granted || Build.VERSION.SDK_INT < 33);
            if (!granted && Build.VERSION.SDK_INT >= 33) toast("Notification permission denied");
        } else if (rc == RC_BT) {
            swBluetooth.setChecked(granted || Build.VERSION.SDK_INT < 31);
            if (!granted && Build.VERSION.SDK_INT >= 31) toast("Bluetooth permission denied");
        } else if (rc == RC_CAMERA) {
            swCamera.setChecked(granted);
            if (!granted) toast("Camera permission denied");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (bottom != null) {
            suppressNav = true;
            bottom.getMenu().findItem(R.id.nav_profile).setChecked(true);
            bottom.post(() -> suppressNav = false);
        }
    }
}
