package unimelb.comp90018.equaltrip;
// Author: Jinglin Lei
// SignUp Function
//Date: 2025-09-09
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.appbar.MaterialToolbar;
import androidx.activity.OnBackPressedCallback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

/**
 * AddTripActivity - full skeleton with improvements:
 *  - Date range picker
 *  - Check user existence before adding tripmate (case-insensitive)
 *  - Create trip in Firestore (with id backfill + complete listener)
 *  - Save & restore UI state on rotation/process recreation
 */



public class AddTripActivity extends AppCompatActivity {

    // Views
    private TextInputEditText etTripName;
    private TextInputEditText etLocation;
    private MaterialButton btnStartDate;
    private MaterialButton btnEndDate;
    private TextInputEditText etDesc;
    private MaterialButton btnAddViaBluetooth;
    private TextInputEditText etMateEmail;
    private FloatingActionButton btnAddMate;
    private RecyclerView rvTripmates;
    private MaterialButton btnCreateTrip;

    // Data
    private final List<String> tripmates = new ArrayList<>();
    @Nullable private Long startMillis = null;
    @Nullable private Long endMillis = null;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // Adapter
    private SimpleEmailAdapter emailAdapter;

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

        // Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        bindViews();
        setupTripmatesList();
        setupDatePickers();
        setupClicks();

        // 顶部返回箭头
        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setNavigationOnClickListener(v -> handleBack());
        // 系统返回键（与左上角一致）
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBack(); }
        });


        // Restore state if any
        restoreState(savedInstanceState);
    }

    private void bindViews() {
        etTripName         = findViewById(R.id.etTripName);
        etLocation         = findViewById(R.id.etLocation);
        btnStartDate       = findViewById(R.id.btnStartDate);
        btnEndDate         = findViewById(R.id.btnEndDate);
        etDesc             = findViewById(R.id.etDesc);
        btnAddViaBluetooth = findViewById(R.id.btnAddViaBluetooth); // placeholder
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

    /** Both buttons open a date-range picker */
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
                }
            });

            picker.show(getSupportFragmentManager(), "trip_range");
        };

        btnStartDate.setOnClickListener(openRangePicker);
        btnEndDate.setOnClickListener(openRangePicker);

        // Optional: long press to clear
        btnStartDate.setOnLongClickListener(v -> {
            startMillis = null;
            btnStartDate.setText(R.string.date_start_placeholder);
            return true;
        });
        btnEndDate.setOnLongClickListener(v -> {
            endMillis = null;
            btnEndDate.setText(R.string.date_end_placeholder);
            return true;
        });
    }

    private void setupClicks() {
        // Add email (check Firestore first)
        btnAddMate.setOnClickListener(v -> {
            String emailRaw = safeText(etMateEmail);
            String email = emailRaw.toLowerCase(Locale.ROOT);

            if (emailRaw.isEmpty()) {
                toast("Please enter an email");
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(emailRaw).matches()) {
                toast("Invalid email format");
                return;
            }
            if (containsIgnoreCase(tripmates, emailRaw)) {
                toast("This user has already been added");
                return;
            }

            btnAddMate.setEnabled(false);
            // Try "email", then fallback to "email"
            checkUserExists(emailRaw, exists -> {
                btnAddMate.setEnabled(true);
                if (exists) {
                    // Store normalized lowercase for consistency
                    tripmates.add(email);
                    emailAdapter.notifyItemInserted(tripmates.size() - 1);
                    etMateEmail.setText("");
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Notice")
                            .setMessage("User does not exist.")
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        });

        // (Placeholder) Add via Bluetooth
        btnAddViaBluetooth.setOnClickListener(v ->
                toast("Add via Bluetooth (TODO: to be implemented)"));

        // Create Trip (write to Firestore)
        btnCreateTrip.setOnClickListener(v -> {
            if (!validateForm()) return;
            btnCreateTrip.setEnabled(false);
            createTrip();
        });
    }

    /** Query Firestore: try "email" first, then fallback to "email" if present */
    private void checkUserExists(String emailRaw, UserCheckCallback callback) {
        String email = emailRaw.toLowerCase(Locale.ROOT);

        db.collection("users")
                .whereEqualTo("email", emailRaw)
                .limit(1)
                .get()
                .addOnSuccessListener(q -> {
                    boolean exists = q != null && !q.isEmpty();
                    if (exists) {
                        callback.onResult(true);
                    } else {
                        // Fallback: email
                        db.collection("users")
                                .whereEqualTo("email", email)
                                .limit(1)
                                .get()
                                .addOnSuccessListener(q2 -> {
                                    boolean exists2 = q2 != null && !q2.isEmpty();
                                    callback.onResult(exists2);
                                })
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

    /** Create Trip - Firestore write with id backfill and complete close */
    private void createTrip() {
        String name     = safeText(etTripName);
        String location = safeText(etLocation);
        String desc     = safeText(etDesc);
        String ownerId  = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : "anonymous";

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("location", location);

        // 机器用：毫秒
        data.put("startDate", startMillis);
        data.put("endDate", endMillis);

        data.put("description", desc);
        data.put("tripmates", new ArrayList<>(tripmates));
        data.put("ownerId", ownerId);
        data.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        data.put("createdAtClient", System.currentTimeMillis()); // ← 新增：稳定排序用
        // 展示用：字符串 yyyy-MM-dd（可选）
        Map<String, Object> dateMap = new HashMap<>();
        dateMap.put("start", formatYmd(startMillis));
        dateMap.put("end",   formatYmd(endMillis));
        data.put("date", dateMap);

        db.collection("trips")
                .add(data)
                .addOnSuccessListener(docRef -> {
//                    // ★ 写 members 子合集（创建者 + 受邀邮箱）
//                    String ownerUid = auth.getCurrentUser().getUid();
//                    List<String> inviteEmailsLower = new ArrayList<>(tripmates); // 你列表里本来就存小写
//                    populateMembersSubcollection(docRef.getId(), ownerUid, inviteEmailsLower);

                    docRef.update("id", docRef.getId()).addOnCompleteListener(t -> {
                        toast("Trip created successfully!");

                        // 强制把 TripPage 拉到前台，复用已有实例
                        Intent back = new Intent(this, TripPageActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(back);

                        finish(); // 关闭 AddTripActivity
                    });

                })
                .addOnFailureListener(e -> {
                    btnCreateTrip.setEnabled(true);
                    toast("Failed to create trip: " + e.getMessage());
                });
    }

    private @Nullable String formatYmd(@Nullable Long ms) {
        if (ms == null) return null;
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
                .format(new java.util.Date(ms));
    }

    private boolean validateForm() {
        String name = safeText(etTripName);
        String location = safeText(etLocation);

        if (name.isEmpty()) {
            etTripName.setError("Required");
            return false;
        }
        if (location.isEmpty()) {
            etLocation.setError("Required");
            return false;
        }
        if (startMillis == null || endMillis == null) {
            toast("Please select a date");
            return false;
        }
        if (endMillis < startMillis) {
            toast("End date must be later than start date");
            return false;
        }
        return true;
    }

    private String safeText(@Nullable TextInputEditText et) {
        return (et == null || et.getText() == null) ? "" : et.getText().toString().trim();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private String formatDate(long millis) {
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                .format(new Date(millis));
    }

    // ---------- State save/restore ----------
    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
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

        if (in.containsKey(K_S)) {
            startMillis = in.getLong(K_S);
            btnStartDate.setText(formatDate(startMillis));
        }
        if (in.containsKey(K_E)) {
            endMillis = in.getLong(K_E);
            btnEndDate.setText(formatDate(endMillis));
        }
        ArrayList<String> mates = in.getStringArrayList(K_MATES);
        if (mates != null) {
            tripmates.clear();
            tripmates.addAll(mates);
            if (emailAdapter != null) emailAdapter.notifyDataSetChanged();
        }
    }

    // ---------- Utils ----------
    private static boolean containsIgnoreCase(List<String> list, String target) {
        for (String s : list) {
            if (s != null && s.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    private void handleBack() {
        if (hasUnsavedChanges()) {
            new AlertDialog.Builder(this)
                    .setTitle("Discard changes?")
                    .setMessage("You have unsaved changes. Discard and go back?")
                    .setPositiveButton("Discard", (d, w) -> finish()) // 不设结果，默认 RESULT_CANCELED
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            finish(); // 直接返回
        }
    }

    private boolean hasUnsavedChanges() {
        return !safeText(etTripName).isEmpty()
                || !safeText(etLocation).isEmpty()
                || !safeText(etDesc).isEmpty()
                || startMillis != null
                || endMillis != null
                || !tripmates.isEmpty();
    }

    // ================== Minimal RecyclerView Adapter ==================
    private static class SimpleEmailAdapter extends RecyclerView.Adapter<SimpleEmailVH> {
        interface OnRemoveClick { void onRemove(int position); }
        private final List<String> data;
        private final OnRemoveClick onRemove;

        SimpleEmailAdapter(List<String> data, OnRemoveClick onRemove) {
            this.data = data;
            this.onRemove = onRemove;
        }

        @Override public SimpleEmailVH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View item = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tripmate_email, parent, false);
            return new SimpleEmailVH(item, onRemove);
        }

        @Override public void onBindViewHolder(SimpleEmailVH holder, int position) {
            holder.bind(data.get(position));
        }

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
                if (onRemove != null && pos != RecyclerView.NO_POSITION) {
                    onRemove.onRemove(pos);
                }
            });
        }

        void bind(String email) { tvEmail.setText(email); }
    }
    //在trip合集中添加members data子合集
    //子合集包含所有参与者（创建人 + 受邀人）{用户名，uid，邮箱}
//    private void populateMembersSubcollection(String tripId, String ownerUid, List<String> inviteEmailsLower) {
//        FirebaseFirestore db = FirebaseFirestore.getInstance();
//        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
//
//        DocumentReference tripRef = db.collection("trips").document(tripId);
//
//        // 1) 先写入 owner（docId = ownerUid）
//        WriteBatch first = db.batch();
//        DocumentReference ownerRef = tripRef.collection("members").document(ownerUid);
//        Map<String, Object> owner = new HashMap<>();
//        owner.put("uid", ownerUid);
//        owner.put("username", null); // 稍后从 /users/{ownerUid}.userId 补充
//        owner.put("email", me != null && me.getEmail()!=null ? me.getEmail().toLowerCase() : null);
//        owner.put("role", "owner");
//        owner.put("status", "active");
//        owner.put("joinedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
//        first.set(ownerRef, owner, com.google.firebase.firestore.SetOptions.merge());
//
//        first.commit().addOnSuccessListener(_ok -> {
//            db.collection("users").document(ownerUid).get()
//                    .addOnSuccessListener(doc -> {
//                        if (doc.exists()) {
//                            String userId = doc.getString("userId");
//                            if (userId != null) {
//                                ownerRef.update("username", userId);
//                            }
//                        }
//
//                    });
//
//            // 2) 受邀邮箱 → /users 查 uid 批量写入（whereIn ≤ 10；超过分批）
//            if (inviteEmailsLower == null || inviteEmailsLower.isEmpty()) return;
//            addMembersByEmailInChunks(tripRef, inviteEmailsLower, 0);
//        });
//    }
//
//    private void addMembersByEmailInChunks(DocumentReference tripRef, List<String> emails, int from) {
//        final int LIMIT = 10;
//        int to = Math.min(from + LIMIT, emails.size());
//        List<String> chunk = emails.subList(from, to);
//
//        FirebaseFirestore db = FirebaseFirestore.getInstance();
//        db.collection("users").whereIn("email", chunk).get()
//                .addOnSuccessListener(q -> {
//                    WriteBatch batch = db.batch();
//
//                    // 已注册：docId 用 uid
//                    java.util.Set<String> matched = new java.util.HashSet<>();
//                    for (com.google.firebase.firestore.DocumentSnapshot u : q.getDocuments()) {
//                        String uid  = u.contains("uid") ? u.getString("uid") : u.getId();
//                        String mail = u.getString("email");
//                        String name = u.getString("userId");
//                        if (mail != null) matched.add(mail.toLowerCase());
//
//                        Map<String,Object> m = new HashMap<>();
//                        m.put("uid", uid);
//                        m.put("email", mail != null ? mail.toLowerCase() : null);
//                        m.put("username", name);
//                        m.put("role", "member");
//                        m.put("status", "active");
//                        m.put("joinedAt", FieldValue.serverTimestamp());
//                        batch.set(tripRef.collection("members").document(uid),
//                                m, SetOptions.merge());
//                    }
//
//                    // 未注册邮箱：占位（autoId）
//                    for (String mail : chunk) {
//                        String lm = mail == null ? null : mail.toLowerCase();
//                        if (lm == null || matched.contains(lm)) continue;
//
//                        Map<String,Object> invited = new HashMap<>();
//                        invited.put("uid", null);
//                        invited.put("email", lm);
//                        invited.put("username", null);
//                        invited.put("role", "member");
//                        invited.put("status", "invited");
//                        invited.put("joinedAt", FieldValue.serverTimestamp());
//                        batch.set(tripRef.collection("members").document(),
//                                invited, SetOptions.merge());
//                    }
//
//                    batch.commit().addOnSuccessListener(_ok -> {
//                        if (to < emails.size()) addMembersByEmailInChunks(tripRef, emails, to);
//                    });
//                })
//
//                .addOnFailureListener(e -> {
//                    // /users 查不到也别让创建失败；直接把这批邮箱都当 invited 写进去
//                    WriteBatch batch = db.batch();
//                    for (String mail : chunk) {
//                        Map<String,Object> invited = new java.util.HashMap<>();
//                        invited.put("uid", null);
//                        invited.put("email", mail);
//                        invited.put("username", null);
//                        invited.put("role", "member");
//                        invited.put("status", "invited");
//                        invited.put("joinedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
//                        batch.set(tripRef.collection("members").document(),
//                                invited, com.google.firebase.firestore.SetOptions.merge());
//                    }
//                    batch.commit().addOnSuccessListener(_ok -> {
//                        if (to < emails.size()) addMembersByEmailInChunks(tripRef, emails, to);
//                    });
//                });
//    }


    // ================== Callback ==================
    interface UserCheckCallback { void onResult(boolean exists); }
}
