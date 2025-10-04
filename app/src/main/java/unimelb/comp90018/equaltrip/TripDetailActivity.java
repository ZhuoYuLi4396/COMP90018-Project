
package unimelb.comp90018.equaltrip;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

import android.content.Intent;      // +++
import android.widget.Button;       // +++
import android.widget.Toast;        // +++

public class TripDetailActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_detail);

        // 顶部返回
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 从 intent 取出 tripId
        String tripId = getIntent().getStringExtra("tripId");

        // 简单显示
        TextView tv = findViewById(R.id.tvTripInfo);
        tv.setText("Trip detail page\nTripId: " + tripId);

        // 绑定你刚加的按钮（确保 XML 里的 id 是 btn_add_bill）
        Button btnAddBill = findViewById(R.id.btn_add_bill);
        btnAddBill.setOnClickListener(v -> {
            if (tripId == null || tripId.isEmpty()) {
                Toast.makeText(this, "Missing tripId", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(TripDetailActivity.this, AddBillActivity.class);
            i.putExtra("tripId", tripId);   // 传给 AddBillActivity
            startActivity(i);
        });
    }
}

*/
package unimelb.comp90018.equaltrip;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import android.content.Intent;
import android.widget.Button;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TripDetailActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String currentUserUid;
    private Map<String, String> uidToUserId = new HashMap<>(); // uid → userId
    private ListenerRegistration billsListener;

    private TextView tvTripInfo;
    private TextView tvDebts; // 用来展示模拟的欠款结果

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_detail);

        db = FirebaseFirestore.getInstance();
        currentUserUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // 顶部返回
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 从 intent 取出 tripId
        String tripId = getIntent().getStringExtra("tripId");

        // 简单显示
        tvTripInfo = findViewById(R.id.tvTripInfo);
        tvTripInfo.setText("Trip detail page\nTripId: " + tripId);

        // 新增：展示欠款情况的 TextView（你 XML 要加一个 id=tvDebts 的 TextView）
        tvDebts = findViewById(R.id.tvDebts);

        // 绑定加账单按钮
        Button btnAddBill = findViewById(R.id.btn_add_bill);
        btnAddBill.setOnClickListener(v -> {
            if (tripId == null || tripId.isEmpty()) {
                Toast.makeText(this, "Missing tripId", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(TripDetailActivity.this, AddBillActivity.class);
            i.putExtra("tripId", tripId);
            startActivity(i);
        });

        if (tripId != null && !tripId.isEmpty()) {
            loadMembers(tripId, () -> listenToBills(tripId));
        }
    }

    private void loadMembers(String tripId, Runnable onComplete) {
        db.collection("trips")
                .document(tripId)
                .collection("members")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    uidToUserId.clear();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String uid = doc.getString("uid");
                        String userId = doc.getString("userId");
                        if (uid != null && userId != null) {
                            uidToUserId.put(uid, userId);
                        }
                    }
                    if (onComplete != null) onComplete.run();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error loading members: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void listenToBills(String tripId) {
        if (billsListener != null) billsListener.remove();

        billsListener = db.collection("trips")
                .document(tripId)
                .collection("bills")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Error listening to bills: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 用来存储当前用户和其他人的净欠款情况
                    Map<String, Double> netDebts = new HashMap<>();

                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            String payerUid = doc.getString("paidBy");
                            List<Map<String, Object>> debts = (List<Map<String, Object>>) doc.get("debts");

                            if (debts == null) continue;

                            for (Map<String, Object> debt : debts) {
                                String fromUid = (String) debt.get("from");
                                String toUid = (String) debt.get("to");
                                Double amount = debt.get("amount") instanceof Number ? ((Number) debt.get("amount")).doubleValue() : 0.0;

                                if (currentUserUid.equals(toUid)) {
                                    // 别人欠我钱 → 正数
                                    netDebts.put(fromUid, netDebts.getOrDefault(fromUid, 0.0) + amount);
                                } else if (currentUserUid.equals(fromUid)) {
                                    // 我欠别人钱 → 负数
                                    netDebts.put(toUid, netDebts.getOrDefault(toUid, 0.0) - amount);
                                }
                            }
                        }
                    }

                    // 输出结果
                    StringBuilder sb = new StringBuilder("Your debts summary:\n");

                    // 输出当前用户 userId
                    String currentUserId = uidToUserId.getOrDefault(currentUserUid, currentUserUid);
                    sb.append("Current test account: ").append(currentUserId).append("\n\n");

                    for (Map.Entry<String, Double> entry : netDebts.entrySet()) {
                        String otherUid = entry.getKey();
                        String userId = uidToUserId.getOrDefault(otherUid, otherUid);
                        double balance = entry.getValue();

                        if (balance > 0) {
                            sb.append(String.format(Locale.getDefault(), "%s owes you %.2f\n", userId, balance));
                        } else if (balance < 0) {
                            sb.append(String.format(Locale.getDefault(), "You owe %s %.2f\n", userId, -balance));
                        } else {
                            sb.append(String.format(Locale.getDefault(), "You and %s are settled up\n", userId));
                        }
                    }

                    tvDebts.setText(sb.toString());
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (billsListener != null) billsListener.remove();
    }
}

