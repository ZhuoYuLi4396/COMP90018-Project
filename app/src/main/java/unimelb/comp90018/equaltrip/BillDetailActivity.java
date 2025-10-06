package unimelb.comp90018.equaltrip;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

public class BillDetailActivity extends AppCompatActivity {

    private static final String TAG = "BillDetail";
    private static final int REQUEST_EDIT_BILL = 2001;

    public static final String EXTRA_TID = "tid";
    public static final String EXTRA_BID = "bid";

    private String tid, bid, currentUid;
    private FirebaseFirestore db;

    // views
    private TextView tvTitle, tvSubtitle, tvDate, tvNote, tvPayerName, tvPayerPaidAmount;
    private ImageView ivMap, ivPayerAvatar, ivReceipt;
    private ProgressBar progress;
    private MaterialCardView cardReceipt;
    private ParticipantBalanceAdapter adapter;

    // cache
    private final Map<String, TripMember> memberMap = new HashMap<>();
    private Bill bill;
    private boolean isCurrentUserPayer = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bill_detail);

        tid = getIntent().getStringExtra(EXTRA_TID);
        bid = getIntent().getStringExtra(EXTRA_BID);

        Log.d(TAG, "onCreate: tid=" + tid + ", bid=" + bid);

        if (TextUtils.isEmpty(tid) || TextUtils.isEmpty(bid)) {
            Toast.makeText(this, "Missing trip or bill ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUid = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        db = FirebaseFirestore.getInstance();

        // Init views
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvDate = findViewById(R.id.tvDate);
        tvNote = findViewById(R.id.tvNote);
        ivMap = findViewById(R.id.ivMap);
        ivReceipt = findViewById(R.id.ivReceipt);
        tvPayerName = findViewById(R.id.tvPayerName);
        tvPayerPaidAmount = findViewById(R.id.tvPayerPaidAmount);
        ivPayerAvatar = findViewById(R.id.ivPayerAvatar);
        progress = findViewById(R.id.progress);
//        cardReceipt = findViewById(R.id.cardReceipt);

        androidx.recyclerview.widget.RecyclerView rv = findViewById(R.id.rvParticipants);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ParticipantBalanceAdapter();
        rv.setAdapter(adapter);

        loadAll();
    }

    private void loadAll() {
        showLoading(true);

        // Concurrently load members and bill
        Task<QuerySnapshot> membersTask = db.collection("trips")
                .document(tid).collection("members").get();

        Task<DocumentSnapshot> billTask = db.collection("trips")
                .document(tid).collection("bills").document(bid).get();

        Tasks.whenAllSuccess(membersTask, billTask)
                .addOnSuccessListener(results -> {
                    // Member Analysis
                    QuerySnapshot membersSnap = (QuerySnapshot) results.get(0);
                    if (membersSnap != null && !membersSnap.isEmpty()) {
                        for (DocumentSnapshot d : membersSnap.getDocuments()) {
                            String uid = d.getString("uid");
                            if (uid == null || uid.isEmpty()) {
                                uid = d.getId();
                            }

                            String userId = d.getString("userId");
                            String displayName = d.getString("displayName");
                            String name = displayName != null ? displayName : userId;
                            if (name == null) name = uid;

                            String photo = d.getString("photoUrl");

                            Log.d(TAG, "Loaded member: uid=" + uid + ", name=" + name);
                            memberMap.put(uid, new TripMember(uid, name, photo));
                        }
                    }

                    // 解析 bill
                    DocumentSnapshot billDoc = (DocumentSnapshot) results.get(1);
                    if (billDoc != null && billDoc.exists()) {
                        bill = billDoc.toObject(Bill.class);
                        if (bill != null) {
                            bill.id = billDoc.getId();
                            Log.d(TAG, "Loaded bill: " + bill.getTitle() +
                                    ", payer=" + bill.getPayerUid() +
                                    ", amount=" + bill.amount);

                            // Check if current user is the payer
                            String payerUid = bill.getPayerUid();
                            isCurrentUserPayer = (currentUid != null && currentUid.equals(payerUid));
                            invalidateOptionsMenu(); // Refresh menu
                        }
                    }

                    if (bill == null) {
                        Toast.makeText(this, "Bill not found", Toast.LENGTH_SHORT).show();
                        showLoading(false);
                        finish();
                        return;
                    }

                    render();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Load failed", e);
                    Toast.makeText(this, "Error loading data: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    showLoading(false);
                });
    }

    private void render() {
        if (bill == null) {
            showLoading(false);
            return;
        }

        tvTitle.setText(bill.getTitle());

        // Build subtitle: Category | Merchant | Location
        String subtitle = "";

        // Add category first
        if (bill.category != null && !bill.category.isEmpty()) {
            String emoji = getCategoryEmoji(bill.category);
            subtitle = emoji + " " + bill.category;
        }

        // Add merchant second
        if (bill.merchant != null && !bill.merchant.isEmpty()) {
            subtitle += (subtitle.isEmpty() ? "" : " | ") + bill.merchant;
        }

        // Add location third
        String location = bill.getLocation();
        if (!location.isEmpty()) {
            subtitle += (subtitle.isEmpty() ? "" : " | ") + location;
        }

        tvSubtitle.setText(subtitle.isEmpty() ? "Bill" : subtitle);

        // Date
        if (bill.createdAt != null) {
            tvDate.setText(formatDate(bill.createdAt));
        } else if (bill.date != null) {
            tvDate.setText(formatDate(bill.date));
        } else {
            tvDate.setText("");
        }

        // Note
        if (bill.note != null && !bill.note.isEmpty()) {
            tvNote.setText(bill.note);
            tvNote.setVisibility(View.VISIBLE);
        } else {
            tvNote.setVisibility(View.GONE);
        }

        // 地图
        Double lat = bill.getLat();
        Double lon = bill.getLon();
        if (lat != null && lon != null && lat != 0.0 && lon != 0.0) {
            ivMap.setVisibility(View.VISIBLE);
        } else {
            ivMap.setVisibility(View.GONE);
        }

        // 付款人信息
        String payerUid = bill.getPayerUid();
        TripMember payer = memberMap.get(payerUid);
        String payerName = payer != null ? payer.displayName :
                (payerUid != null ? payerUid : "Unknown");

        tvPayerName.setText(payerName);

        // 金额格式化
        String currency = extractCurrency(bill.currency);
        tvPayerPaidAmount.setText("Paid " + currency + String.format("%.2f", bill.amount));

        // 付款人头像
        if (payer != null && payer.photoUrl != null && !payer.photoUrl.isEmpty()) {
            Glide.with(this).load(payer.photoUrl)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .circleCrop().into(ivPayerAvatar);
        } else {
            ivPayerAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
        }

        // 收据
//        String receiptUrl = bill.receiptUrl;
//        if (bill.receiptUrls != null && !bill.receiptUrls.isEmpty()) {
//            receiptUrl = bill.receiptUrls.get(0);
//        }
//
//        if (receiptUrl != null && !receiptUrl.isEmpty()) {
//            Glide.with(this).load(receiptUrl)
//                    .placeholder(R.drawable.ic_image_placeholder)
//                    .into(ivReceipt);
//            cardReceipt.setVisibility(View.VISIBLE);
        ivReceipt.setImageResource(R.drawable.ic_image_placeholder);
//        } else {
//            cardReceipt.setVisibility(View.GONE);
//        }

        // 参与者列表
        List<ParticipantBalance> rows = buildParticipantRows(bill, payerName, currency);
        adapter.submit(rows);

        showLoading(false);
    }

    private List<ParticipantBalance> buildParticipantRows(Bill bill, String payerName, String currency) {
        List<ParticipantBalance> rows = new ArrayList<>();

        String payerUid = bill.getPayerUid();

        // 首先添加付款人
        TripMember payerMember = memberMap.get(payerUid);
        rows.add(new ParticipantBalance(
                payerUid,
                payerName + (payerUid != null && payerUid.equals(currentUid) ? " (YOU)" : ""),
                payerMember != null ? payerMember.photoUrl : null,
                true,
                "Paid " + currency + String.format("%.2f", bill.amount)
        ));

        // 从 debts 数组添加欠款人
        if (bill.debts != null && !bill.debts.isEmpty()) {
            for (Map<String, Object> debt : bill.debts) {
                String fromUid = (String) debt.get("from");
                String toUid = (String) debt.get("to");
                Object amountObj = debt.get("amount");

                if (fromUid != null && toUid != null && amountObj != null) {
                    double amount = 0;
                    if (amountObj instanceof Number) {
                        amount = ((Number) amountObj).doubleValue();
                    }

                    TripMember fromMember = memberMap.get(fromUid);
                    String fromName = fromMember != null ? fromMember.displayName : fromUid;

                    rows.add(new ParticipantBalance(
                            fromUid,
                            fromName + (fromUid.equals(currentUid) ? " (YOU)" : ""),
                            fromMember != null ? fromMember.photoUrl : null,
                            false,
                            "owes " + payerName + " " + currency + String.format("%.2f", amount)
                    ));
                }
            }
        }

        // 如果没有debts，使用 participants 平分
        if ((bill.debts == null || bill.debts.isEmpty()) &&
                bill.participants != null && !bill.participants.isEmpty()) {

            double sharePerPerson = bill.amount / bill.participants.size();

            for (String uid : bill.participants) {
                if (uid.equals(payerUid)) continue;

                TripMember member = memberMap.get(uid);
                String name = member != null ? member.displayName : uid;

                rows.add(new ParticipantBalance(
                        uid,
                        name + (uid.equals(currentUid) ? " (YOU)" : ""),
                        member != null ? member.photoUrl : null,
                        false,
                        "owes " + payerName + " " + currency + String.format("%.2f", sharePerPerson)
                ));
            }
        }

        return rows;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Only show menu if current user is the payer
        if (isCurrentUserPayer) {
            getMenuInflater().inflate(R.menu.menu_bill_detail, menu);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_edit_bill) {
            editBill();
            return true;
        } else if (id == R.id.action_delete_bill) {
            confirmDeleteBill();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void editBill() {
        if (bill == null) return;

        Intent intent = new Intent(this, EditBillActivity.class);
        intent.putExtra("tripId", tid);
        intent.putExtra("billId", bid);
        startActivityForResult(intent, REQUEST_EDIT_BILL);
    }

    private void confirmDeleteBill() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Bill")
                .setMessage("Are you sure you want to delete this bill? This action cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteBill())
                .show();
    }

    private void deleteBill() {
        showLoading(true);

        db.collection("trips")
                .document(tid)
                .collection("bills")
                .document(bid)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Bill deleted successfully", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to delete bill: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Delete bill failed", e);
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EDIT_BILL && resultCode == RESULT_OK) {
            // Reload the bill data after edit
            loadAll();
        }
    }

    private String getCategoryEmoji(String category) {
        if (category == null) return "📄";
        switch (category.toLowerCase()) {
            case "dining": case "food": return "🍽";
            case "transport": case "transportation": return "🚗";
            case "shopping": return "🛍";
            case "accommodation": case "hotel": return "🏨";
            case "entertainment": return "🎭";
            default: return "📄";
        }
    }

    private String extractCurrency(String currencyFull) {
        if (currencyFull == null || currencyFull.isEmpty()) return "$";

        String[] parts = currencyFull.split(" ");
        if (parts.length > 0 && !parts[0].isEmpty()) {
            return parts[0];
        }
        return "$";
    }

    private void showLoading(boolean show) {
        progress.setVisibility(show ? View.VISIBLE : View.GONE);
        findViewById(R.id.content).setAlpha(show ? 0.3f : 1f);
    }

    private String formatDate(Timestamp ts) {
        if (ts == null) return "";
        Date d = ts.toDate();
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d);
    }
}