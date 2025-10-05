
package unimelb.comp90018.equaltrip;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
// 过滤，只和当前登录用户有关的bill
import com.google.firebase.firestore.Filter;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import android.content.Intent;      // +++
import android.widget.Button;       // +++
import android.widget.Toast;        // +++

public class TripDetailActivity extends AppCompatActivity {

    private static final String TAG = "TripDetail";

    // Header
    private TextView tvTitle, tvLocation, tvDate, tvDesc;

    // CTA
    private MaterialButton btnAddBill;

    // Lists
    private RecyclerView rvBills, rvBalances;

    // Data
    private final Map<String, Member> membersByUid = new HashMap<>();
    private final List<Member> membersList = new ArrayList<>();
    private final List<Bill> bills = new ArrayList<>();
    private final Map<String, Long> netCents = new HashMap<>(); // otherUid -> cents (relative to me)
    private final List<BalanceRow> balanceRows = new ArrayList<>();

    // Firestore
    private ListenerRegistration tripReg, membersReg, billsReg;
    private FirebaseFirestore db;

    // Runtime
    private String tripId;
    private String myUid;
    private String ownerId;
    private boolean allowOwnerMenu;

    // Adapters
    private BillsAdapter billsAdapter;
    private RecyclerView.Adapter<BalanceVH> balancesAdapter;

    // ===== Models =====
    static class Member {
        String uid; String email; String userId; String role;
        String display() {
            if (userId != null && !userId.isEmpty()) return userId;
            if (email != null && !email.isEmpty())   return email;
            return uid != null ? uid : "Unknown";
        }
    }
    static class BalanceRow { String uid; long cents; BalanceRow(String u, long c){ uid=u; cents=c; } }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_detail);

        db = FirebaseFirestore.getInstance();
        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        // ---- 先取 tripId 并校验 ----
        tripId = getIntent().getStringExtra("tripId");
        Log.d(TAG, "tripId=" + tripId);
        if (tripId == null || tripId.trim().isEmpty()) {
            Toast.makeText(this, "Missing tripId", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Toolbar
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        if (topAppBar != null) {
            setSupportActionBar(topAppBar);
            topAppBar.setNavigationOnClickListener(v -> finish());
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        // Header views
        tvTitle    = findViewById(R.id.tripTitle);
        tvLocation = findViewById(R.id.tripLocation);
        tvDate     = findViewById(R.id.tripDate);
        tvDesc     = findViewById(R.id.tripDesc);

        // RecyclerViews
        rvBalances = findViewById(R.id.rvBalances);
        rvBills    = findViewById(R.id.rvBills);

        // Balances adapter (Tripmates section)
        if (rvBalances != null) {
            rvBalances.setLayoutManager(new LinearLayoutManager(this));
            rvBalances.setNestedScrollingEnabled(false);
            balancesAdapter = new RecyclerView.Adapter<BalanceVH>() {
                @NonNull @Override
                public BalanceVH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
                    android.view.View item = android.view.LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.item_tripmate_balance, parent, false);
                    return new BalanceVH(item);
                }
                @Override
                public void onBindViewHolder(@NonNull BalanceVH h, int position) {
                    BalanceRow r = balanceRows.get(position);
                    Member m = membersByUid.get(r.uid);
                    String who = (m != null) ? m.display() : r.uid;

                    h.tvWho.setText(who);
                    String money = NumberFormat.getCurrencyInstance().format(Math.abs(r.cents) / 100.0);
                    if (r.cents > 0) {
                        h.tvBalance.setText("owes you " + money);
                        h.tvBalance.setTextColor(0xFF43A047); // green
                    } else if (r.cents < 0) {
                        h.tvBalance.setText("you owe " + money);
                        h.tvBalance.setTextColor(0xFFE53935); // red
                    } else {
                        h.tvBalance.setText("settled");
                        h.tvBalance.setTextColor(0xFF808080); // gray
                    }
                }
                @Override public int getItemCount() { return balanceRows.size(); }
            };
            rvBalances.setAdapter(balancesAdapter);
        }

        // Bills adapter (card list)
        if (rvBills != null) {
            rvBills.setLayoutManager(new LinearLayoutManager(this));
            billsAdapter = new BillsAdapter(
                    bills,
                    myUid,
                    uid -> {
                        Member m = membersByUid.get(uid);
                        return m != null ? m.display() : uid;
                    }
            );
            rvBills.setAdapter(billsAdapter);
        }

        // Add Bill
        btnAddBill = findViewById(R.id.ctaButton);
        if (btnAddBill != null) {
            btnAddBill.setOnClickListener(v -> {
                Intent i = new Intent(this, AddBillActivity.class);
                i.putExtra("tripId", tripId);
                startActivity(i);
            });
        }

        if (myUid == null) {
            Log.w(TAG, "Not signed in — balances will not compute fully.");
        }
    }

    @Override protected void onStart() {
        super.onStart();

        // 1) Trip header + owner
        tripReg = db.collection("trips").document(tripId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Log.e(TAG, "trip listen error", e);
                        Toast.makeText(this, "Trip load error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snap == null || !snap.exists()) {
                        Toast.makeText(this, "Trip not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    bindTripHeader(snap);
                    ownerId = snap.getString("ownerId");
                    if (ownerId == null) ownerId = snap.getString("ownerUid");
                    updateOwnerMenuFlag();
                });

        // 2) Tripmates (members)
        membersReg = db.collection("trips").document(tripId)
                .collection("members")
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Log.e(TAG, "members listen error", e);
                        return;
                    }
                    if (snap == null) { Log.w(TAG, "members snap null"); return; }

                    membersByUid.clear();
                    membersList.clear();

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Member m = new Member();
                        String uid = d.getString("uid");
                        if (uid == null || uid.trim().isEmpty()) uid = d.getId(); // 兜底
                        m.uid   = uid;
                        m.email = d.getString("email");
                        m.userId= d.getString("userId");
                        m.role  = d.getString("role");

                        if (m.uid != null) {
                            membersByUid.put(m.uid, m);
                            membersList.add(m);
                        }
                    }
                    // 成员变化 -> 刷新 balances（保证每个队友至少有一行）
                    bindBalancesList();
                });

        // 3) Bills + Balances
        // 🔎 服务端过滤说明：
        //    已登录时，仅拉取「我付的钱（paidBy==myUid）」或「我参与的（participants 包含 myUid）」的账单。
        //    这样能显著减少读取量；为避免索引报错，这里不再服务端 orderBy，改为客户端排序。
        if (billsReg != null) { billsReg.remove(); billsReg = null; }

        if (myUid != null) {
            billsReg = db.collection("trips").document(tripId)
                    .collection("bills")
                    .where(
                            Filter.or(
                                    Filter.equalTo("paidBy", myUid),
                                    Filter.arrayContains("participants", myUid)
                            )
                    )
                    .addSnapshotListener((snap, e) -> handleBillsSnapshot(snap, e));
        } else {
            // 未登录：回退到“拉全部”的旧逻辑（也去掉 orderBy；客户端排序）
            billsReg = db.collection("trips").document(tripId)
                    .collection("bills")
                    .addSnapshotListener((snap, e) -> handleBillsSnapshot(snap, e));
        }
    }

    private void handleBillsSnapshot(@Nullable com.google.firebase.firestore.QuerySnapshot snap, @Nullable Exception e) {
        if (e != null) {
            Log.e(TAG, "bills listen error", e);
            Toast.makeText(this, "Bills load error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        if (snap == null) { Log.w(TAG, "bills snap null"); return; }

        bills.clear();
        netCents.clear();

        for (DocumentSnapshot d : snap.getDocuments()) {
            // Bill 基本字段（配合你的外部 Bill 类）
            Bill b = new Bill();
            b.id       = d.getId();
            b.title    = nz(d.getString("billName"), "Untitled bill");
            b.category = nz(d.getString("category"), "Misc");
            b.payerUid = d.getString("paidBy");

            Object ts  = d.get("createdAt");
            if (ts instanceof Timestamp) {
                b.dateMs = ((Timestamp) ts).toDate().getTime();
            }

            b.totalCents = toCents(d.get("amount"));
            bills.add(b);

            // 统计我与每个成员的净额
            Object rawList = d.get("debts");
            if (rawList instanceof List && myUid != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> debts = (List<Map<String, Object>>) rawList;
                for (Map<String, Object> row : debts) {
                    if (row == null) continue;
                    String from = asString(row.get("from"));
                    String to   = asString(row.get("to"));
                    long cents  = toCents(row.get("amount"));

                    if (myUid.equals(to) && from != null) {
                        // 别人欠我（正数）
                        netCents.merge(from, +cents, Long::sum);
                    } else if (myUid.equals(from) && to != null) {
                        // 我欠别人（负数）
                        netCents.merge(to,   -cents, Long::sum);
                    }
                }
            }
        }

        // 客户端按 createdAt 降序
        bills.sort((a, b) -> Long.compare(b.dateMs, a.dateMs));

        if (billsAdapter != null) billsAdapter.notifyDataSetChanged();
        bindBalancesList();
    }

    @Override protected void onStop() {
        super.onStop();
        if (tripReg != null) { tripReg.remove(); tripReg = null; }
        if (membersReg != null) { membersReg.remove(); membersReg = null; }
        if (billsReg != null) { billsReg.remove(); billsReg = null; }
    }

    // ===== Header binding =====
    private void bindTripHeader(DocumentSnapshot d) {
        if (tvTitle != null)    tvTitle.setText(nz(d.getString("name"), "Untitled trip"));
        if (tvLocation != null) tvLocation.setText(nz(d.getString("location"), ""));
        if (tvDesc != null)     tvDesc.setText(nz(d.getString("description"), ""));
        if (tvDate != null)     tvDate.setText(buildDateText(d.get("startDate"), d.get("endDate"), d.get("date")));
    }

    private void updateOwnerMenuFlag() {
        boolean isOwnerByTrip = (myUid != null && myUid.equals(ownerId));
        if (isOwnerByTrip != allowOwnerMenu) {
            allowOwnerMenu = isOwnerByTrip;
            invalidateOptionsMenu();
        }
    }

    // ===== Balances binding (Tripmates section) =====
    private void bindBalancesList() {
        if (rvBalances == null || balancesAdapter == null) return;

        // 用成员表“补零”，保证每个队友都显示出来
        Map<String, Long> merged = new HashMap<>();
        for (Member m : membersList) {
            if (m.uid == null || m.uid.equals(myUid)) continue;
            long v = netCents.getOrDefault(m.uid, 0L);
            merged.put(m.uid, v);
        }

        List<BalanceRow> tmp = new ArrayList<>();
        for (Map.Entry<String, Long> e : merged.entrySet()) {
            tmp.add(new BalanceRow(e.getKey(), e.getValue()));
        }

        // 从大到小（先显示“欠你”的）
        tmp.sort((a, b) -> Long.compare(b.cents, a.cents));

        balanceRows.clear();
        balanceRows.addAll(tmp);
        balancesAdapter.notifyDataSetChanged();
    }

    // ===== Utilities =====
    private static String asString(Object o) { return (o == null) ? null : String.valueOf(o); }
    private static long toCents(Object amount) {
        if (amount == null) return 0L;
        if (amount instanceof Number) {
            double d = ((Number) amount).doubleValue();
            return Math.round(d * 100.0);
        }
        try { return Math.round(Double.parseDouble(String.valueOf(amount)) * 100.0); }
        catch (Exception ignore) { return 0L; }
    }

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

    // ===== ViewHolder for balances =====
    static class BalanceVH extends RecyclerView.ViewHolder {
        TextView tvWho, tvBalance;
        BalanceVH(@NonNull android.view.View itemView) {
            super(itemView);
            tvWho     = itemView.findViewById(R.id.tvWho);
            tvBalance = itemView.findViewById(R.id.tvBalance);
        }
    }

    // ===== Owner-only menu =====
    @Override public boolean onCreateOptionsMenu(android.view.Menu menu) {
        if (!allowOwnerMenu) return true;
        getMenuInflater().inflate(R.menu.menu_trip_details, menu);
        return true;
    }
    @Override public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit_trip) {
            Intent i = new Intent(this, EditTripActivity.class);
            i.putExtra("tripId", tripId);
            startActivity(i);
            return true;
        } else if (id == R.id.action_delete_trip) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Delete trip?")
                    .setMessage("This action cannot be undone.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (d, w) ->
                            db.collection("trips").document(tripId)
                                    .delete()
                                    .addOnSuccessListener(v -> { Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show(); finish(); })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show())
                    ).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static String nz(@Nullable String v, @NonNull String def){ return (v==null||v.trim().isEmpty())?def:v; }
}

