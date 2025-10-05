package unimelb.comp90018.equaltrip;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;

/**
 * EditTripActivity (只增成员 + 蓝牙扫描；不支持删除)
 * 复用 activity_add_trip.xml：
 *  需要的 id：toolbar, etTripName, etLocation, etDesc,
 *           btnStartDate, btnEndDate, btnCreateTrip(保存),
 *           btnAddViaBluetooth, etMateEmail, btnAddMate(FAB), rvTripmates
 */
public class EditTripActivity extends AppCompatActivity {

    // ===== Firestore & Auth =====
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // ===== runtime =====
    private String tripId;
    private String ownerUid;

    // ===== views =====
    private TextInputEditText etTripName, etLocation, etDesc, etMateEmail;
    private MaterialButton btnStartDate, btnEndDate, btnSave, btnAddViaBluetooth;
    private FloatingActionButton btnAddMate;   // ★ 方案 A：使用真实类型
    private RecyclerView rvTripmates;

    // ===== state =====
    @Nullable private Long startMillis = null;
    @Nullable private Long endMillis   = null;

    // 成员只读显示
    private final List<String> currentMemberLabels = new ArrayList<>();
    private SimpleEmailAdapter readOnlyAdapter;
    private ListenerRegistration membersReg;

    // ===== BLE（与 AddTrip 保持一致） =====
    private static final int REQ_BLE_S_SCAN = 2001;
    private final HashSet<String> seenUids = new HashSet<>();

    // =========== lifecycle ===========
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_trip);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        tripId = getIntent().getStringExtra("tripId");
        if (TextUtils.isEmpty(tripId)) {
            finish();
            return;
        }

        bindViews();
        setupToolbar();
        setupList();
        setupClicks();

        // 预填基本信息
        db.collection("trips").document(tripId).get()
                .addOnSuccessListener(d -> {
                    if (!d.exists()) {
                        toast("Trip not found");
                        finish();
                        return;
                    }
                    etTripName.setText(d.getString("name"));
                    etLocation.setText(d.getString("location"));
                    etDesc.setText(d.getString("description"));

                    startMillis = getLong(d.get("startDate"));
                    endMillis   = getLong(d.get("endDate"));
                    if (startMillis != null) btnStartDate.setText(fmt(startMillis));
                    if (endMillis   != null) btnEndDate.setText(fmt(endMillis));

                    ownerUid = nz(d.getString("ownerId"), d.getString("ownerUid"));
                    if (ownerUid == null && auth.getCurrentUser() != null) {
                        ownerUid = auth.getCurrentUser().getUid();
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 实时监听 members -> 填充 rvTripmates（只读）
        membersReg = db.collection("trips").document(tripId)
                .collection("members")
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;
                    bindMembersList(snap);
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (membersReg != null) { membersReg.remove(); membersReg = null; }
    }

    // =========== UI ===========
    private void bindViews() {
        etTripName       = findViewById(R.id.etTripName);
        etLocation       = findViewById(R.id.etLocation);
        etDesc           = findViewById(R.id.etDesc);
        etMateEmail      = findViewById(R.id.etMateEmail);
        btnStartDate     = findViewById(R.id.btnStartDate);
        btnEndDate       = findViewById(R.id.btnEndDate);
        btnSave          = findViewById(R.id.btnCreateTrip); // 复用 id
        btnAddViaBluetooth = findViewById(R.id.btnAddViaBluetooth);
        btnAddMate       = findViewById(R.id.btnAddMate);     // ★ FAB
        rvTripmates      = findViewById(R.id.rvTripmates);

        if (btnSave != null) btnSave.setText("Save changes");
    }

    private void setupToolbar() {
        MaterialToolbar tb = findViewById(R.id.toolbar);
        if (tb != null) {
            tb.setTitle("Edit Trip");
            tb.setNavigationOnClickListener(v -> finish());
        }
    }

    private void setupList() {
        rvTripmates.setLayoutManager(new LinearLayoutManager(this));
        readOnlyAdapter = new SimpleEmailAdapter(currentMemberLabels);
        rvTripmates.setAdapter(readOnlyAdapter);
    }

    private void setupClicks() {
        // 日期范围选择
        View.OnClickListener openRange = v -> {
            MaterialDatePicker.Builder<androidx.core.util.Pair<Long, Long>> b =
                    MaterialDatePicker.Builder.dateRangePicker().setTitleText("Select trip dates");
            if (startMillis != null && endMillis != null) {
                b.setSelection(new androidx.core.util.Pair<>(startMillis, endMillis));
            }
            MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker = b.build();
            picker.addOnPositiveButtonClickListener(sel -> {
                if (sel != null) {
                    startMillis = sel.first;
                    endMillis   = sel.second;
                    btnStartDate.setText(fmt(startMillis));
                    btnEndDate.setText(fmt(endMillis));
                }
            });
            picker.show(getSupportFragmentManager(), "edit_range");
        };
        btnStartDate.setOnClickListener(openRange);
        btnEndDate.setOnClickListener(openRange);

        // 保存
        btnSave.setOnClickListener(v -> saveBasicsOnly());

        // ＋ 按邮箱新增成员
        btnAddMate.setOnClickListener(v -> {
            String emailRaw = safeText(etMateEmail);
            addMemberByEmail(emailRaw);
        });

        // 蓝牙扫描新增成员
        btnAddViaBluetooth.setOnClickListener(v -> tryScanForTripmates());
    }

    // =========== 成员列表 ===========
    private void bindMembersList(@NonNull QuerySnapshot snap) {
        currentMemberLabels.clear();
        for (DocumentSnapshot d : snap.getDocuments()) {
            String email = d.getString("email");
            String userId = d.getString("userId");
            String uid = nz(d.getString("uid"), d.getId());
            String label = !isEmpty(userId) ? userId : (!isEmpty(email) ? email : uid);
            currentMemberLabels.add(label);
        }
        if (readOnlyAdapter != null) readOnlyAdapter.notifyDataSetChanged();
    }

    // =========== 保存基础信息 ===========
    private void saveBasicsOnly() {
        String name = safeText(etTripName), loc = safeText(etLocation), desc = safeText(etDesc);
        if (isEmpty(name)) { etTripName.setError("Required"); return; }
        if (isEmpty(loc))  { etLocation.setError("Required"); return; }
        if (startMillis == null || endMillis == null || endMillis < startMillis) {
            toast("Invalid date range"); return;
        }

        Map<String, Object> up = new HashMap<>();
        up.put("name", name);
        up.put("location", loc);
        up.put("description", desc);
        up.put("startDate", startMillis);
        up.put("endDate", endMillis);
        Map<String, Object> dateMap = new HashMap<>();
        dateMap.put("start", ymd(startMillis));
        dateMap.put("end",   ymd(endMillis));
        up.put("date", dateMap);
        up.put("updatedAt", FieldValue.serverTimestamp());

        btnSave.setEnabled(false);
        db.collection("trips").document(tripId).update(up)
                .addOnSuccessListener(v -> { toast("Saved"); finish(); })
                .addOnFailureListener(e -> { btnSave.setEnabled(true); toast("Failed: " + e.getMessage()); });
    }

    // =========== 新增成员（邮箱） ===========
    private void addMemberByEmail(@NonNull String emailRaw) {
        String email = emailRaw.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty()) { toast("Please enter an email"); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { toast("Invalid email format"); return; }

        db.collection("users").whereEqualTo("email", email).limit(1).get()
                .addOnSuccessListener(q -> {
                    if (q.isEmpty()) { toast("User does not exist"); return; }
                    writeMemberDoc(q.getDocuments().get(0));
                    if (etMateEmail != null) etMateEmail.setText("");
                })
                .addOnFailureListener(e -> toast("Lookup failed: " + e.getMessage()));
    }

    // 写 members/{uid}，并同步 trips.tripmates
    private void writeMemberDoc(@NonNull DocumentSnapshot u) {
        String uid = nz(u.getString("uid"), u.getId());
        if (isEmpty(uid)) { toast("Invalid user"); return; }

        Map<String, Object> m = new HashMap<>();
        m.put("uid", uid);
        if (u.getString("email")  != null) m.put("email",  u.getString("email"));
        if (u.getString("userId") != null) m.put("userId", u.getString("userId"));
        m.put("role", uid.equals(ownerUid) ? "owner" : "member");
        m.put("addedAt", Timestamp.now());

        db.collection("trips").document(tripId)
                .collection("members").document(uid)
                .set(m)
                .addOnSuccessListener(v -> { toast("Member added"); syncTripmatesEmails(); })
                .addOnFailureListener(e -> toast("Add failed: " + e.getMessage()));
    }

    private void syncTripmatesEmails() {
        db.collection("trips").document(tripId)
                .collection("members").get()
                .addOnSuccessListener(q -> {
                    List<String> emails = new ArrayList<>();
                    for (DocumentSnapshot d : q.getDocuments()) {
                        String em = d.getString("email");
                        if (!isEmpty(em)) emails.add(em.trim().toLowerCase(Locale.ROOT));
                    }
                    db.collection("trips").document(tripId).update("tripmates", emails);
                });
    }

    // =========== 蓝牙扫描（与 AddTrip 一致） ===========
    private void tryScanForTripmates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] perms = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
            if (!hasAll(perms)) {
                ActivityCompat.requestPermissions(this, perms, REQ_BLE_S_SCAN);
                return;
            }
        } else {
            if (!hasAll(new String[]{Manifest.permission.ACCESS_FINE_LOCATION})) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQ_BLE_S_SCAN);
                return;
            }
        }

        seenUids.clear();
        toast("Scanning nearby devices…");

        BleUidExchange.get(this).scanAndFetchUids(20_000, new BleUidExchange.OnUidFoundListener() {
            @Override public void onUidFound(String uid) {
                if (uid == null || uid.isEmpty() || !seenUids.add(uid)) return;
                fetchEmailByUid(uid, email -> {
                    if (email == null || email.isEmpty()) return;
                    addMemberByEmail(email);
                });
            }
            @Override public void onScanFinished() { toast("Scan finished."); }
        });
    }

    // 从 uid 反查 email（与 AddTrip 对齐）
    private interface EmailCallback { void onEmail(@Nullable String email); }

    private void fetchEmailByUid(@NonNull String uid, @NonNull EmailCallback cb) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String email = getEmailField(doc);
                        if (!isEmpty(email)) { cb.onEmail(email); return; }
                    }
                    db.collection("users").whereEqualTo("uid", uid).limit(1).get()
                            .addOnSuccessListener(q -> {
                                if (q != null && !q.isEmpty()) {
                                    String email = getEmailField(q.getDocuments().get(0));
                                    if (!isEmpty(email)) { cb.onEmail(email); return; }
                                }
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

    @Nullable
    private String getEmailField(@NonNull DocumentSnapshot doc) {
        Object v = doc.get("email");
        return v == null ? null : String.valueOf(v).trim().toLowerCase(Locale.ROOT);
    }

    // =========== 权限 ===========
    private boolean hasAll(String[] ps) {
        for (String p : ps) {
            if (ActivityCompat.checkSelfPermission(this, p) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BLE_S_SCAN) {
            if (hasAll(permissions)) {
                tryScanForTripmates();
            } else {
                toast("Bluetooth permission required to scan.");
            }
        }
    }

    // =========== 工具 ===========
    private static boolean isEmpty(@Nullable String s) { return s == null || s.trim().isEmpty(); }
    private static String nz(@Nullable String v, @Nullable String alt) { return isEmpty(v) ? alt : v; }
    private String safeText(@Nullable TextInputEditText et) { return (et == null || et.getText() == null) ? "" : et.getText().toString().trim(); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private static String fmt(long ms) { return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date(ms)); }
    private static String ymd(long ms) { return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(ms)); }
    @Nullable private static Long getLong(Object o) { if (o instanceof Long) return (Long)o; if (o instanceof Number) return ((Number)o).longValue(); return null; }

    // ===== 只读 Email 列表适配器（隐藏删除） =====
    private static class SimpleEmailAdapter extends RecyclerView.Adapter<SimpleEmailVH> {
        private final List<String> data;
        SimpleEmailAdapter(List<String> data) { this.data = data; }
        @NonNull @Override public SimpleEmailVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View item = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tripmate_email, parent, false);
            return new SimpleEmailVH(item);
        }
        @Override public void onBindViewHolder(@NonNull SimpleEmailVH h, int pos) { h.bind(data.get(pos)); }
        @Override public int getItemCount() { return data.size(); }
    }

    private static class SimpleEmailVH extends RecyclerView.ViewHolder {
        private final android.widget.TextView tvEmail;
        private final android.widget.ImageView ivRemove;
        SimpleEmailVH(@NonNull View itemView) {
            super(itemView);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            ivRemove = itemView.findViewById(R.id.ivRemove);
            if (ivRemove != null) { ivRemove.setVisibility(View.GONE); ivRemove.setOnClickListener(null); }
        }
        void bind(String email) { if (tvEmail != null) tvEmail.setText(email); }
    }
}
