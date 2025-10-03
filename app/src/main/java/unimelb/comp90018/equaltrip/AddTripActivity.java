package unimelb.comp90018.equaltrip;

// Author: Jinglin Lei
// SignUp Function
// Date: 2025-09-09

import android.content.Intent;
import android.content.res.ColorStateList;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;

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
        // 初始化一次按钮的“已选择/未选择”可视状态
        applyDateButtonState();
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
                    applyDateButtonState(); // ← 选完日期，切回正常色
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
            applyDateButtonState(); // ← 清空后回到“灰色提示感”
            return true;
        });
        btnEndDate.setOnLongClickListener(v -> {
            endMillis = null;
            btnEndDate.setText(R.string.date_end_placeholder);
            applyDateButtonState();
            return true;
        });
    }

    /** 根据是否已选择日期，切换按钮的文字/图标颜色（灰色提示感 vs 正常色） */
    private void applyDateButtonState() {
        // “正常色”取主题里的 colorOnSurface，灰色用你项目里常用的次要文本色
        int normal = MaterialColors.getColor(btnStartDate, com.google.android.material.R.attr.colorOnSurface);
        int hint   = getColor(R.color.text_secondary); // 如果没有这个颜色，可改为 field_stroke 等灰色

        // start
        if (startMillis != null) {
            btnStartDate.setTextColor(normal);
            btnStartDate.setIconTint(ColorStateList.valueOf(normal));
        } else {
            btnStartDate.setTextColor(hint);
            btnStartDate.setIconTint(ColorStateList.valueOf(hint));
        }

        // end
        if (endMillis != null) {
            btnEndDate.setTextColor(normal);
            btnEndDate.setIconTint(ColorStateList.valueOf(normal));
        } else {
            btnEndDate.setTextColor(hint);
            btnEndDate.setIconTint(ColorStateList.valueOf(hint));
        }
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
            checkUserExists(emailRaw, exists -> {
                btnAddMate.setEnabled(true);
                if (exists) {
                    tripmates.add(email); // 统一存小写
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

    /** Query Firestore: try exact email, then lowercase email */
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

    /** Create Trip - Firestore write with id backfill and complete close */
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
        data.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
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

    private @Nullable String formatYmd(@Nullable Long ms) {
        if (ms == null) return null;
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(ms));
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
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date(millis));
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
                    .setPositiveButton("Discard", (d, w) -> finish())
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            finish();
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

        @NonNull @Override
        public SimpleEmailVH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View item = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tripmate_email, parent, false);
            return new SimpleEmailVH(item, onRemove);
        }

        @Override public void onBindViewHolder(@NonNull SimpleEmailVH holder, int position) {
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

    // Firebase 插入 members 子合集
    // 包含创建者、参与人的个人信息
    private void writeMembersSimple(String tid, String ownerUid, List<String> inviteEmails) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1) 创建者：users/{ownerUid} -> trips/{tid}/members/{ownerUid}
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
                                .collection("members").document(ownerUid)
                                .set(owner);

                        // 2) 受邀者：按 email 精确匹配（不做大小写转换）
                        if (inviteEmails != null) {
                            for (String em : inviteEmails) {
                                if (em == null || em.isEmpty()) continue;

                                db.collection("users")
                                        .whereEqualTo("email", em)
                                        .limit(1)
                                        .get()
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
                                                        .collection("members").document(uid)
                                                        .set(m);
                                            } else {
                                                Log.w("AddTripActivity", "invite email not found in users: " + em);
                                            }
                                        });
                            }
                        }
                    }
                });
    }

    // ================== Callback ==================
    interface UserCheckCallback { void onResult(boolean exists); }
}
