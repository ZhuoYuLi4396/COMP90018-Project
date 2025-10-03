package unimelb.comp90018.equaltrip;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
// import com.google.firebase.firestore.Query; // TODO: bills 接入时打开

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TripDetailActivity extends AppCompatActivity {

    private static final String TAG = "TripDetail";

    private MaterialToolbar topAppBar;
    private TextView tvTitle, tvLocation, tvDate, tvDesc;
    private MaterialButton ctaButton;

    private RecyclerView rvBalances, rvBills;
    private final List<Bill> bills = new ArrayList<>();
    private BillsAdapter billsAdapter;

    // 成员（用于显示名 & 计算净额）
    private final Map<String, Member> members = new HashMap<>();
    private ListenerRegistration tripReg, membersReg; // ListenerRegistration billsReg;

    private String tripId;
    private String myUid;
    private String ownerId;

    private boolean allowOwnerMenu = false; // 仅 owner 显示右上角菜单
    private FirebaseFirestore db;

    // ====== 小模型 ======
    static class Member {
        String uid; String email; String userId; String role;
        String display() {
            if (userId != null && !userId.isEmpty()) return userId;
            if (email != null && !email.isEmpty()) return email;
            return uid != null ? uid : "Unknown";
        }
    }
    static class BalanceRow { String uid; long cents;
        BalanceRow(String u, long c){ uid=u; cents=c; } }

    // ====== 生命周期 ======
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_detail);

        // Views
        topAppBar  = findViewById(R.id.topAppBar);
        tvTitle    = findViewById(R.id.tripTitle);
        tvLocation = findViewById(R.id.tripLocation);
        tvDate     = findViewById(R.id.tripDate);
        tvDesc     = findViewById(R.id.tripDesc);
        ctaButton  = findViewById(R.id.ctaButton);

        rvBalances = findViewById(R.id.rvBalances);
        rvBills    = findViewById(R.id.rvBills);
        rvBalances.setLayoutManager(new LinearLayoutManager(this));
        rvBalances.setNestedScrollingEnabled(false);
        rvBills.setLayoutManager(new LinearLayoutManager(this));
        rvBills.setNestedScrollingEnabled(false);

        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        // Toolbar 设置（标题与返回）
        setSupportActionBar(topAppBar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(true);
        topAppBar.setTitle("Trip Details");
        topAppBar.setNavigationOnClickListener(v -> finish());

        // tripId
        tripId = getIntent().getStringExtra("tripId");
        if (tripId == null || tripId.trim().isEmpty()) {
            Toast.makeText(this, "Missing tripId", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // CTA（占位）
        ctaButton.setOnClickListener(v ->
                Toast.makeText(this, "TODO: add bills to this trip", Toast.LENGTH_SHORT).show());

        // Firestore
        db = FirebaseFirestore.getInstance();

        // Bills 占位适配器（显示“谁付了钱”等；显示名通过 members 映射）
        billsAdapter = new BillsAdapter(bills, myUid, uid -> {
            Member m = members.get(uid);
            return m != null ? m.display() : uid;
        });
        rvBills.setAdapter(billsAdapter);
        // 注意：假数据放在 members 加载之后再喂（见 membersReg 末尾），这样 splits 能包含成员
    }

    @Override
    protected void onStart() {
        super.onStart();

        // 监听 trip 文档（头部信息 + owner 判定）
        tripReg = db.collection("trips").document(tripId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) { Log.e(TAG, "listen error", e); return; }
                    if (snap == null || !snap.exists()) {
                        Log.w(TAG, "Trip not found: " + tripId);
                        Toast.makeText(this, "Trip not found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    bindTripHeader(snap);
                });

        // 监听 members（用于显示名 & 净额 & 兜底 owner 判定）
        membersReg = db.collection("trips").document(tripId)
                .collection("members")
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;
                    members.clear();
                    boolean amOwnerInMembers = false;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Member m = new Member();
                        m.uid   = d.getString("uid");
                        m.email = d.getString("email");
                        m.userId= d.getString("userId");
                        m.role  = d.getString("role");
                        if (m.uid != null) {
                            members.put(m.uid, m);
                            if (myUid != null && myUid.equals(m.uid)
                                    && "owner".equalsIgnoreCase(m.role)) {
                                amOwnerInMembers = true;
                            }
                        }
                    }
                    // 若 trip 文档没有 ownerId，用 members 的 role=owner 兜底
                    if (amOwnerInMembers && !allowOwnerMenu) {
                        allowOwnerMenu = true;
                        invalidateOptionsMenu();
                    }
                    // 首次进入或成员变化，若还没有假账单，填充一下以便演示净额
                    if (bills.isEmpty()) {
                        setDummyBills();
                    }
                    billsAdapter.notifyDataSetChanged();
                    bindBalances();
                });

        // TODO: 接入真实 bills 时，放开以下监听
        // billsReg = db.collection("trips").document(tripId)
        //         .collection("bills")
        //         .orderBy("createdAt", Query.Direction.DESCENDING)
        //         .addSnapshotListener((snap, e) -> {
        //             if (e != null || snap == null) return;
        //             bills.clear();
        //             for (DocumentSnapshot d : snap.getDocuments()) {
        //                 Bill b = d.toObject(Bill.class);
        //                 if (b == null) continue;
        //                 b.id = d.getId();
        //                 bills.add(b);
        //             }
        //             billsAdapter.notifyDataSetChanged();
        //             bindBalances();
        //         });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (tripReg != null) { tripReg.remove(); tripReg = null; }
        if (membersReg != null) { membersReg.remove(); membersReg = null; }
        // if (billsReg != null) { billsReg.remove(); billsReg = null; }
    }

    // ====== 标题区 & owner 菜单判定 ======
    private void bindTripHeader(DocumentSnapshot d) {
        String name     = safeString(d.getString("name"), "Untitled trip");
        String location = safeString(d.getString("location"), "");
        String desc     = safeString(d.getString("description"), "");
        String dateText = buildDateText(d.get("startDate"), d.get("endDate"), d.get("date"));

        tvTitle.setText(name);
        tvLocation.setText(location);
        tvDate.setText(dateText);
        tvDesc.setText(desc);

        ownerId = d.getString("ownerId");
        if (ownerId == null) ownerId = d.getString("ownerUid"); // 兼容旧字段
        boolean isOwnerByTrip = (myUid != null && myUid.equals(ownerId));
        if (isOwnerByTrip != allowOwnerMenu) {
            allowOwnerMenu = isOwnerByTrip;
            Log.d(TAG, "ownerId=" + ownerId + ", myUid=" + myUid + ", allowOwnerMenu=" + allowOwnerMenu);
            invalidateOptionsMenu(); // 触发 onCreateOptionsMenu 重新决定是否显示三点
        }
    }

    // ====== 菜单（只对 owner 显示） ======
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!allowOwnerMenu) return true; // 非 owner：不给菜单
        getMenuInflater().inflate(R.menu.menu_trip_details, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit_trip) {
            Intent i = new Intent(this, EditTripActivity.class);
            i.putExtra("tripId", tripId);
            startActivity(i);
            return true;
        } else if (id == R.id.action_delete_trip) {
            confirmDelete();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete trip?")
                .setMessage("This action cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> deleteTrip())
                .show();
    }

    private void deleteTrip() {
        db.collection("trips").document(tripId)
                .delete()
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ====== Tripmates 净额（不含自己；正=对方欠你，负=你欠对方） ======
    private void bindBalances() {
        if (myUid == null) return;

        Map<String, Long> net = new HashMap<>();
        for (Bill b : bills) {
            if (b == null || b.splits == null || b.payerUid == null) continue;

            for (Map.Entry<String, Long> e : b.splits.entrySet()) {
                String uid = e.getKey();
                long share = (e.getValue() == null) ? 0L : e.getValue();

                if (uid.equals(b.payerUid)) continue; // 付款人不欠自己
                if (b.payerUid.equals(myUid)) {
                    // 是我付的钱 → 别人欠我
                    if (!uid.equals(myUid)) net.put(uid, net.getOrDefault(uid, 0L) + share);
                } else if (uid.equals(myUid)) {
                    // 别人付，我是参与者 → 我欠付款人
                    net.put(b.payerUid, net.getOrDefault(b.payerUid, 0L) - share);
                }
            }
        }

        List<BalanceRow> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : net.entrySet()) {
            String uid = e.getKey();
            if (uid.equals(myUid)) continue;
            rows.add(new BalanceRow(uid, e.getValue()));
        }
        rows.sort((a, b) -> Long.compare(b.cents, a.cents));

        rvBalances.setAdapter(new RecyclerView.Adapter<BalanceVH>() {
            @NonNull @Override
            public BalanceVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                android.view.View item = android.view.LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_tripmate_balance, parent, false);
                return new BalanceVH(item);
            }
            @Override
            public void onBindViewHolder(@NonNull BalanceVH h, int position) {
                BalanceRow r = rows.get(position);
                Member m = members.get(r.uid);
                String who = (m != null) ? m.display() : r.uid;
                h.tvWho.setText(who);

                String money = NumberFormat.getCurrencyInstance()
                        .format(Math.abs(r.cents) / 100.0);
                if (r.cents > 0) {
                    h.tvBalance.setText("owes you " + money);
                    h.tvBalance.setTextColor(0xFF43A047);
                } else if (r.cents < 0) {
                    h.tvBalance.setText("you owe " + money);
                    h.tvBalance.setTextColor(0xFFE53935);
                } else {
                    h.tvBalance.setText("settled");
                    h.tvBalance.setTextColor(0xFF808080);
                }
            }
            @Override
            public int getItemCount() { return rows.size(); }
        });
    }

    static class BalanceVH extends RecyclerView.ViewHolder {
        TextView tvWho, tvBalance;
        BalanceVH(@NonNull android.view.View itemView) {
            super(itemView);
            tvWho = itemView.findViewById(R.id.tvWho);
            tvBalance = itemView.findViewById(R.id.tvBalance);
        }
    }

    // ====== 假数据：占位 Bills（不写入 Firestore） ======
    private void setDummyBills() {
        bills.clear();

        // 示例 1：我付了 18 AUD，其他每人 6 AUD
        Bill b1 = new Bill();
        b1.id = "dummy1";
        b1.title = "Taxi to Unimelb";
        b1.category = "Transport";
        b1.dateMs = 1753968000000L; // 2025-08-01
        b1.payerUid = myUid;
        b1.totalCents = 1800L;
        if (!members.isEmpty()) {
            b1.splits = new HashMap<>();
            int others = 0;
            for (String uid : members.keySet()) if (!uid.equals(myUid)) others++;
            if (others > 0) {
                long each = 600L; // 仅演示
                for (String uid : members.keySet()) if (!uid.equals(myUid)) b1.splits.put(uid, each);
            }
        }
        bills.add(b1);

        // 示例 2：某位他人付款，其他每人 25 AUD
        Bill b2 = new Bill();
        b2.id = "dummy2";
        b2.title = "Lunch at Italian Restaurant";
        b2.category = "Dining";
        b2.dateMs = 1754313600000L; // 2025-08-05
        String someone = null;
        for (String uid : members.keySet()) { if (!uid.equals(myUid)) { someone = uid; break; } }
        b2.payerUid = (someone != null) ? someone : myUid;
        b2.totalCents = 10000L;
        if (!members.isEmpty()) {
            b2.splits = new HashMap<>();
            for (String uid : members.keySet()) if (!uid.equals(b2.payerUid)) b2.splits.put(uid, 2500L);
        }
        bills.add(b2);

        billsAdapter.notifyDataSetChanged();
        bindBalances();
    }

    // ====== 工具方法 ======
    private String buildDateText(Object startObj, Object endObj, Object dateObj) {
        Long s = (startObj instanceof Long) ? (Long) startObj : null;
        Long e = (endObj instanceof Long) ? (Long) endObj : null;
        if (s != null && e != null) return formatRangeMillis(s, e);

        if (dateObj instanceof Map) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) dateObj;
                Object sStr = map.get("start");
                Object eStr = map.get("end");
                if (sStr instanceof String && eStr instanceof String) {
                    return sStr + " to " + eStr;
                }
            } catch (ClassCastException ignored) {}
        }
        return "";
    }

    private String formatRangeMillis(long startMs, long endMs) {
        SimpleDateFormat fmt = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        return fmt.format(new Date(startMs)) + " \u2013 " + fmt.format(new Date(endMs));
    }

    private String safeString(@Nullable String s, String def) {
        return (s == null || s.trim().isEmpty()) ? def : s;
    }
}
