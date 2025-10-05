package unimelb.comp90018.equaltrip;

// Author: Jinglin Lei, Ziyan Zhai, Zhuoyu Li
// AddTrip Activity (Merged: local UI polish + BLE invite)
// Date: 2025-10-03

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.List;

public class AddTripActivity extends AppCompatActivity {

    // ===== Views =====
    private TextInputEditText etTripName;
    private TextInputEditText etLocation;
    private MaterialButton btnStartDate;
    private MaterialButton btnEndDate;
    private TextInputEditText etDesc;
    private MaterialButton btnAddViaBluetooth;   // BLE invite
    private TextInputEditText etMateEmail;
    private FloatingActionButton btnAddMate;
    private RecyclerView rvTripmates;
    private MaterialButton btnCreateTrip;

    // ===== Data =====
    private final List<String> tripmates = new ArrayList<>();
    @Nullable private Long startMillis = null;
    @Nullable private Long endMillis = null;

    // ===== Firebase =====
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // ===== Adapter =====
    private SimpleEmailAdapter emailAdapter;

    // ===== BLE =====
    private static final int REQ_BLE_S_SCAN = 2001;
    private final HashSet<String> seenUids = new HashSet<>();

    // ----- Keys for state -----
    private static final String K_NAME  = "state_name";
    private static final String K_LOC   = "state_loc";
    private static final String K_DESC  = "state_desc";
    private static final String K_S     = "state_start";
    private static final String K_E     = "state_end";
    private static final String K_MATES = "state_mates";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_trip);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        bindViews();
        setupTripmatesList();
        setupDatePickers();
        setupClicks();

        // 顶部返回
        MaterialToolbar tb = findViewById(R.id.toolbar);
        if (tb != null) tb.setNavigationOnClickListener(v -> handleBack());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBack(); }
        });

        restoreState(savedInstanceState);
        applyDateButtonState(); // 初始化一次
    }

    private void bindViews() {
        etTripName         = findViewById(R.id.etTripName);
        etLocation         = findViewById(R.id.etLocation);
        btnStartDate       = findViewById(R.id.btnStartDate);
        btnEndDate         = findViewById(R.id.btnEndDate);
        etDesc             = findViewById(R.id.etDesc);
        btnAddViaBluetooth = findViewById(R.id.btnAddViaBluetooth);
        etMateEmail        = findViewById(R.id.etMateEmail);
        btnAddMate         = findViewById(R.id.btnAddMate);
        rvTripmates        = findViewById(R.id.rvTripmates);
        btnCreateTrip      = findViewById(R.id.btnCreateTrip);
    }

    private void setupTripmatesList() {
        rvTripmates.setLayoutManager(new LinearLayoutManager(this));
        emailAdapter = new SimpleEmailAdapter(tripmates, pos -> {
            if (pos >= 0 && pos < tripmates.size()) {
                tripmates.remove(pos);
                emailAdapter.notifyItemRemoved(pos);
            }
        });
        rvTripmates.setAdapter(emailAdapter);
    }

    /** 两个日期按钮都打开“日期范围选择” */
    private void setupDatePickers() {
        View.OnClickListener openRangePicker = v -> {
            CalendarConstraints.Builder cons = new CalendarConstraints.Builder();
            MaterialDatePicker.Builder<Pair<Long, Long>> builder =
                    MaterialDatePicker.Builder.dateRangePicker()
                            .setTitleText("Select trip dates")
                            .setCalendarConstraints(cons.build());

            if (startMillis != null && endMillis != null) {
                builder.setSelection(new Pair<>(startMillis, endMillis));
            }

            MaterialDatePicker<Pair<Long, Long>> picker = builder.build();
            picker.addOnPositiveButtonClickListener(selection -> {
                if (selection != null) {
                    startMillis = selection.first;
                    endMillis   = selection.second;
                    btnStartDate.setText(formatDate(startMillis));
                    btnEndDate.setText(formatDate(endMillis));
                    applyDateButtonState();
                }
            });

            picker.show(getSupportFragmentManager(), "trip_range");
        };

        btnStartDate.setOnClickListener(openRangePicker);
        btnEndDate.setOnClickListener(openRangePicker);

        // 长按清空
        btnStartDate.setOnLongClickListener(v -> {
            startMillis = null;
            btnStartDate.setText(R.string.date_start_placeholder);
            applyDateButtonState();
            return true;
        });
        btnEndDate.setOnLongClickListener(v -> {
            endMillis = null;
            btnEndDate.setText(R.string.date_end_placeholder);
            applyDateButtonState();
            return true;
        });
    }

    /** 根据是否选择日期切换按钮的文字/图标颜色（提示灰 vs 正常色） */
    private void applyDateButtonState() {
        int normal = MaterialColors.getColor(btnStartDate, com.google.android.material.R.attr.colorOnSurface);
        int hint   = ContextCompat.getColor(this, R.color.text_secondary);

        if (startMillis != null) {
            btnStartDate.setTextColor(normal);
            btnStartDate.setIconTint(android.content.res.ColorStateList.valueOf(normal));
        } else {
            btnStartDate.setTextColor(hint);
            btnStartDate.setIconTint(android.content.res.ColorStateList.valueOf(hint));
        }

        if (endMillis != null) {
            btnEndDate.setTextColor(normal);
            btnEndDate.setIconTint(android.content.res.ColorStateList.valueOf(normal));
        } else {
            btnEndDate.setTextColor(hint);
            btnEndDate.setIconTint(android.content.res.ColorStateList.valueOf(hint));
        }
    }

    private void setupClicks() {
        // 手动输入邮箱
        btnAddMate.setOnClickListener(v -> {
            String emailRaw = safeText(etMateEmail);
            addTripmateByEmail(emailRaw);
        });

        // 蓝牙扫描拉人
        btnAddViaBluetooth.setOnClickListener(v -> generatePinAndScan());

        // 创建 Trip
        btnCreateTrip.setOnClickListener(v -> {
            if (!validateForm()) return;
            btnCreateTrip.setEnabled(false);
            createTrip();
        });
    }

    // ========================= BLE 扫描 =========================
    // 生成 PIN → 显示 → 开始扫描
    private void generatePinAndScan() {
        // 权限检查（扫描端逻辑保持不变）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] perms = new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
            if (!hasAll(perms)) {
                ActivityCompat.requestPermissions(this, perms, REQ_BLE_S_SCAN);
                return;
            }
        } else {
            if (!hasAll(new String[]{ Manifest.permission.ACCESS_FINE_LOCATION })) {
                ActivityCompat.requestPermissions(this,
                        new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
                        REQ_BLE_S_SCAN);
                return;
            }
        }

        // 1) 生成随机 4 位 PIN（BleUidExchange 已实现，支持前导 0）
        String pin = BleUidExchange.get(this).generateSessionPin();

        // 2) 弹窗显示 PIN（大号等宽字体）+ 倒计时 + 提前停止
        final long durationMs = 60_000L;
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);

        android.widget.TextView tvTitle = new android.widget.TextView(this);
        tvTitle.setText("Share this 4-digit PIN with your friend");
        tvTitle.setTextSize(16);

        android.widget.TextView tvPin = new android.widget.TextView(this);
        tvPin.setText(pin);
        tvPin.setTextSize(36);
        tvPin.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        tvPin.setPadding(0, pad, 0, pad);

        android.widget.TextView tvHint = new android.widget.TextView(this);
        tvHint.setText("They will input this PIN on their phone to start broadcasting.");
        tvHint.setTextSize(14);

        android.widget.TextView tvCountdown = new android.widget.TextView(this);
        tvCountdown.setTextSize(14);
        tvCountdown.setPadding(0, pad, 0, 0);

        com.google.android.material.button.MaterialButton btnCopy =
                new com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnCopy.setText("Copy PIN");
        btnCopy.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("PIN", pin));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        });

        box.addView(tvTitle);
        box.addView(tvPin);
        box.addView(tvHint);
        box.addView(tvCountdown);
        box.addView(btnCopy);

        androidx.appcompat.app.AlertDialog dlg = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("Stop now", (d, w) -> {
                    try { BleUidExchange.get(this).onDestroy(); } catch (Throwable ignore) {}
                    Toast.makeText(this, "Scan stopped.", Toast.LENGTH_SHORT).show();
                })
                .create();

        dlg.show();

        // 3) 立刻按 PIN 开始扫描 60 秒（A 端扫描端）
        seenUids.clear();
        Toast.makeText(this, "Scanning with PIN " + pin + " …", Toast.LENGTH_SHORT).show();

        BleUidExchange.get(this).startScanningWithPin(pin, durationMs, new BleUidExchange.OnUidFoundListener() {
            @Override public void onUidFound(String uid) {
                if (uid == null || uid.isEmpty()) return;
                if (!seenUids.add(uid)) return;
                fetchEmailByUid(uid, email -> {
                    if (email == null || email.isEmpty()) return;
                    addTripmateByEmail(email);
                });
            }
            @Override public void onScanFinished() {
                if (dlg.isShowing()) dlg.dismiss();
                Toast.makeText(AddTripActivity.this, "Scan finished.", Toast.LENGTH_SHORT).show();
            }
        });

        // 倒计时文字
        new android.os.CountDownTimer(durationMs, 1000) {
            @Override public void onTick(long ms) { tvCountdown.setText("Scanning… " + (ms/1000) + "s left"); }
            @Override public void onFinish() { /* onScanFinished 会关对话框，这里无需重复 */ }
        }.start();
    }


    private void tryScanForTripmates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] perms = new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
            if (!hasAll(perms)) {
                ActivityCompat.requestPermissions(this, perms, REQ_BLE_S_SCAN);
                return;
            }
        } else {
            if (!hasAll(new String[]{ Manifest.permission.ACCESS_FINE_LOCATION })) {
                ActivityCompat.requestPermissions(this,
                        new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
                        REQ_BLE_S_SCAN);
                return;
            }
        }

        seenUids.clear();
        Toast.makeText(this, "Scanning nearby devices…", Toast.LENGTH_SHORT).show();

        // 项目内的 BLE 封装；若未实现，提供一个空实现以免崩溃
        BleUidExchange.get(this).scanAndFetchUids(20_000, new BleUidExchange.OnUidFoundListener() {
            @Override public void onUidFound(String uid) {
                if (uid == null || uid.isEmpty()) return;
                if (!seenUids.add(uid)) return; // 去重
                fetchEmailByUid(uid, email -> {
                    if (email == null || email.isEmpty()) return;
                    addTripmateByEmail(email);
                });
            }
            @Override public void onScanFinished() {
                Toast.makeText(AddTripActivity.this, "Scan finished.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchEmailByUid(@NonNull String uid, @NonNull EmailCallback cb) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String email = getEmailField(doc);
                        if (email != null && !email.isEmpty()) { cb.onEmail(email); return; }
                    }
                    // 备选：字段 uid
                    db.collection("users").whereEqualTo("uid", uid).limit(1).get()
                            .addOnSuccessListener(q -> {
                                if (q != null && !q.isEmpty()) {
                                    String email = getEmailField(q.getDocuments().get(0));
                                    if (email != null && !email.isEmpty()) { cb.onEmail(email); return; }
                                }
                                // 备选：数组 uids
                                db.collection("users").whereArrayContains("uids", uid).limit(1).get()
                                        .addOnSuccessListener(q2 -> {
                                            if (q2 != null && !q2.isEmpty()) {
                                                cb.onEmail(getEmailField(q2.getDocuments().get(0)));
                                            } else cb.onEmail(null);
                                        })
                                        .addOnFailureListener(e2 -> cb.onEmail(null));
                            })
                            .addOnFailureListener(e1 -> cb.onEmail(null));
                })
                .addOnFailureListener(e -> cb.onEmail(null));
    }

    private @Nullable String getEmailField(@NonNull DocumentSnapshot doc) {
        Object v = doc.get("email");
        return v == null ? null : String.valueOf(v).trim().toLowerCase(Locale.ROOT);
    }

    // 统一入口：手动/蓝牙都走它
    private void addTripmateByEmail(@NonNull String emailRaw) {
        String emailTrim = emailRaw.trim();
        if (emailTrim.isEmpty()) { toast("Please enter an email"); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(emailTrim).matches()) { toast("Invalid email format"); return; }

        String emailLower = emailTrim.toLowerCase(Locale.ROOT);
        if (containsIgnoreCase(tripmates, emailTrim)) { toast("This user has already been added"); return; }

        btnAddMate.setEnabled(false);
        checkUserExists(emailTrim, exists -> {
            btnAddMate.setEnabled(true);
            if (exists) {
                tripmates.add(emailLower);
                emailAdapter.notifyItemInserted(tripmates.size() - 1);
                if (etMateEmail != null) etMateEmail.setText("");
                toast("Added: " + emailLower);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Notice")
                        .setMessage("User does not exist.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    // ========================= Firestore =========================
    private void checkUserExists(String emailRaw, UserCheckCallback callback) {
        String emailLower = emailRaw.toLowerCase(Locale.ROOT);

        db.collection("users").whereEqualTo("email", emailRaw).limit(1).get()
                .addOnSuccessListener(q -> {
                    if (q != null && !q.isEmpty()) {
                        callback.onResult(true);
                    } else {
                        db.collection("users").whereEqualTo("email", emailLower).limit(1).get()
                                .addOnSuccessListener(q2 -> callback.onResult(q2 != null && !q2.isEmpty()))
                                .addOnFailureListener(e2 -> {
                                    toast("Verification failed, please try again later: " + e2.getMessage());
                                    callback.onResult(false);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    toast("Verification failed, please try again later: " + e.getMessage());
                    callback.onResult(false);
                });
    }

    private void createTrip() {
        String name     = safeText(etTripName);
        String location = safeText(etLocation);
        String desc     = safeText(etDesc);
        String ownerId  = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : "anonymous";

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("location", location);
        data.put("startDate", startMillis); // 毫秒
        data.put("endDate",   endMillis);
        data.put("description", desc);
        data.put("tripmates", new ArrayList<>(tripmates));
        data.put("ownerId", ownerId);
        data.put("createdAt", Timestamp.now());
        data.put("createdAtClient", System.currentTimeMillis());

        Map<String, Object> dateMap = new HashMap<>();
        dateMap.put("start", formatYmd(startMillis));
        dateMap.put("end",   formatYmd(endMillis));
        data.put("date", dateMap);

        db.collection("trips").add(data)
                .addOnSuccessListener(docRef -> {
                    String tid = docRef.getId();
                    String ownerUid = FirebaseAuth.getInstance().getUid();
                    writeMembersSimple(tid, ownerUid, tripmates);

                    docRef.update("id", tid).addOnCompleteListener(t -> {
                        toast("Trip created successfully!");
                        Intent back = new Intent(this, TripPageActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(back);
                        finish();
                    });
                })
                .addOnFailureListener(e -> {
                    btnCreateTrip.setEnabled(true);
                    toast("Failed to create trip: " + e.getMessage());
                });
    }

    private void writeMembersSimple(String tid, String ownerUid, List<String> inviteEmails) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(ownerUid).get()
                .addOnSuccessListener(ownerSnap -> {
                    if (ownerSnap.exists()) {
                        String email  = ownerSnap.getString("email");
                        String userId = ownerSnap.getString("userId");
                        Map<String, Object> owner = new HashMap<>();
                        owner.put("uid", ownerUid);
                        owner.put("email", email);
                        owner.put("userId", userId);
                        owner.put("role", "owner");

                        db.collection("trips").document(tid)
                                .collection("members").document(ownerUid).set(owner);

                        if (inviteEmails != null) {
                            for (String em : inviteEmails) {
                                if (em == null || em.isEmpty()) continue;
                                db.collection("users").whereEqualTo("email", em).limit(1).get()
                                        .addOnSuccessListener(q -> {
                                            if (!q.isEmpty()) {
                                                DocumentSnapshot u = q.getDocuments().get(0);
                                                String uid    = u.getString("uid");
                                                String email2 = u.getString("email");
                                                String userId2= u.getString("userId");
                                                if (uid == null) return;

                                                Map<String, Object> m = new HashMap<>();
                                                m.put("uid", uid);
                                                m.put("email", email2);
                                                m.put("userId", userId2);
                                                m.put("role","member");

                                                db.collection("trips").document(tid)
                                                        .collection("members").document(uid).set(m);
                                            } else {
                                                Log.w("AddTripActivity", "invite email not found: " + em);
                                            }
                                        });
                            }
                        }
                    }
                });
    }

    private static boolean containsIgnoreCase(List<String> list, String target) {
        if (list == null || target == null) return false;
        for (String s : list) {
            if (s != null && s.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    // ========================= Utils & State =========================
    private boolean validateForm() {
        String name = safeText(etTripName);
        String location = safeText(etLocation);

        if (name.isEmpty()) { etTripName.setError("Required"); return false; }
        if (location.isEmpty()) { etLocation.setError("Required"); return false; }
        if (startMillis == null || endMillis == null) { toast("Please select a date"); return false; }
        if (endMillis < startMillis) { toast("End date must be later than start date"); return false; }
        return true;
    }

    private String safeText(@Nullable TextInputEditText et) {
        return (et == null || et.getText() == null) ? "" : et.getText().toString().trim();
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    private String formatDate(long millis) {
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date(millis));
    }

    private @Nullable String formatYmd(@Nullable Long ms) {
        if (ms == null) return null;
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(ms));
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(K_NAME, safeText(etTripName));
        out.putString(K_LOC,  safeText(etLocation));
        out.putString(K_DESC, safeText(etDesc));
        if (startMillis != null) out.putLong(K_S, startMillis);
        if (endMillis != null)   out.putLong(K_E, endMillis);
        out.putStringArrayList(K_MATES, new ArrayList<>(tripmates));
    }

    private void restoreState(@Nullable Bundle in) {
        if (in == null) return;
        etTripName.setText(in.getString(K_NAME, ""));
        etLocation.setText(in.getString(K_LOC, ""));
        etDesc.setText(in.getString(K_DESC, ""));
        if (in.containsKey(K_S)) { startMillis = in.getLong(K_S); btnStartDate.setText(formatDate(startMillis)); }
        if (in.containsKey(K_E)) { endMillis   = in.getLong(K_E); btnEndDate.setText(formatDate(endMillis)); }
        ArrayList<String> mates = in.getStringArrayList(K_MATES);
        if (mates != null) { tripmates.clear(); tripmates.addAll(mates); if (emailAdapter != null) emailAdapter.notifyDataSetChanged(); }
    }

    private void handleBack() {
        if (hasUnsavedChanges()) {
            new AlertDialog.Builder(this)
                    .setTitle("Discard changes?")
                    .setMessage("You have unsaved changes. Discard and go back?")
                    .setPositiveButton("Discard", (d, w) -> finish())
                    .setNegativeButton("Cancel", null)
                    .show();
        } else finish();
    }

    private boolean hasUnsavedChanges() {
        return !safeText(etTripName).isEmpty()
                || !safeText(etLocation).isEmpty()
                || !safeText(etDesc).isEmpty()
                || startMillis != null
                || endMillis != null
                || !tripmates.isEmpty();
    }

    // ===== RecyclerView (emails) =====
    private static class SimpleEmailAdapter extends RecyclerView.Adapter<SimpleEmailVH> {
        interface OnRemoveClick { void onRemove(int position); }
        private final List<String> data;
        private final OnRemoveClick onRemove;

        SimpleEmailAdapter(List<String> data, OnRemoveClick onRemove) {
            this.data = data; this.onRemove = onRemove;
        }

        @NonNull @Override
        public SimpleEmailVH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View item = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tripmate_email, parent, false);
            return new SimpleEmailVH(item, onRemove);
        }

        @Override public void onBindViewHolder(@NonNull SimpleEmailVH holder, int position) { holder.bind(data.get(position)); }
        @Override public int getItemCount() { return data.size(); }
    }

    private static class SimpleEmailVH extends RecyclerView.ViewHolder {
        private final android.widget.TextView tvEmail;
        private final android.widget.ImageView ivRemove;

        SimpleEmailVH(View itemView, SimpleEmailAdapter.OnRemoveClick onRemove) {
            super(itemView);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            ivRemove = itemView.findViewById(R.id.ivRemove);
            ivRemove.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (onRemove != null && pos != RecyclerView.NO_POSITION) onRemove.onRemove(pos);
            });
        }

        void bind(String email) { tvEmail.setText(email); }
    }

    // ===== Callbacks =====
    interface UserCheckCallback { void onResult(boolean exists); }
    interface EmailCallback { void onEmail(@Nullable String email); }

    // ===== 权限 & 资源清理 =====
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BLE_S_SCAN) {
            if (hasAll(permissions)) {
                generatePinAndScan();   // ← 用新的 PIN 流程
            } else {
                Toast.makeText(this, "Bluetooth permission required to scan.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean hasAll(String[] ps) {
        for (String p : ps) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { BleUidExchange.get(this).onDestroy(); } catch (Throwable ignore) {}
    }
}
