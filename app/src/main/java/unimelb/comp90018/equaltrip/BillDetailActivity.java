package unimelb.comp90018.equaltrip;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BillDetailActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "BillDetail";
    private static final int REQUEST_EDIT_BILL = 2001;
    private static final String MAP_BUNDLE_KEY = "bill_detail_map_bundle";

    public static final String EXTRA_TID = "tid";
    public static final String EXTRA_BID = "bid";

    private String tid, bid, currentUid;
    private FirebaseFirestore db;

    // views
    private TextView tvTitle, tvSubtitle, tvDate, tvNote, tvPayerName, tvPayerPaidAmount;
    private ImageView ivPayerAvatar;
    private ProgressBar progress;
    private ParticipantBalanceAdapter adapter;

    // Map
    private MapView mapView;
    private GoogleMap gmap;

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

        currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

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
        tvPayerName = findViewById(R.id.tvPayerName);
        tvPayerPaidAmount = findViewById(R.id.tvPayerPaidAmount);
        ivPayerAvatar = findViewById(R.id.ivPayerAvatar);
        progress = findViewById(R.id.progress);

        // MapView（替代原来的 ivMap）
        mapView = findViewById(R.id.mapBill);
        if (mapView != null) {
            Bundle mapBundle = null;
            if (savedInstanceState != null) {
                mapBundle = savedInstanceState.getBundle(MAP_BUNDLE_KEY);
            }
            mapView.onCreate(mapBundle);
            mapView.getMapAsync(this);
        }

        androidx.recyclerview.widget.RecyclerView rv = findViewById(R.id.rvParticipants);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ParticipantBalanceAdapter();
        rv.setAdapter(adapter);

        loadAll();
    }

    private void loadAll() {
        showLoading(true);

        Task<QuerySnapshot> membersTask = db.collection("trips")
                .document(tid).collection("members").get();

        Task<DocumentSnapshot> billTask = db.collection("trips")
                .document(tid).collection("bills").document(bid).get();

        Tasks.whenAllSuccess(membersTask, billTask)
                .addOnSuccessListener(results -> {
                    // members
                    QuerySnapshot membersSnap = (QuerySnapshot) results.get(0);
                    if (membersSnap != null && !membersSnap.isEmpty()) {
                        for (DocumentSnapshot d : membersSnap.getDocuments()) {
                            String uid = d.getString("uid");
                            if (uid == null || uid.isEmpty()) uid = d.getId();

                            String userId = d.getString("userId");
                            String displayName = d.getString("displayName");
                            String name = displayName != null ? displayName : userId;
                            if (name == null) name = uid;

                            String photo = d.getString("photoUrl");
                            memberMap.put(uid, new TripMember(uid, name, photo));
                        }
                    }

                    // bill
                    DocumentSnapshot billDoc = (DocumentSnapshot) results.get(1);
                    if (billDoc != null && billDoc.exists()) {
                        bill = billDoc.toObject(Bill.class);
                        if (bill != null) {
                            bill.id = billDoc.getId();
                            String payerUid = bill.getPayerUid();
                            isCurrentUserPayer = (currentUid != null && currentUid.equals(payerUid));
                            invalidateOptionsMenu();
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

        // subtitle: category | merchant | location
        String subtitle = "";
        if (!TextUtils.isEmpty(bill.category)) {
            String emoji = getCategoryEmoji(bill.category);
            subtitle = emoji + " " + bill.category;
        }
        if (!TextUtils.isEmpty(bill.merchant)) {
            subtitle += (subtitle.isEmpty() ? "" : " | ") + bill.merchant;
        }
        String location = bill.getLocation();
        if (!TextUtils.isEmpty(location)) {
            subtitle += (subtitle.isEmpty() ? "" : " | ") + location;
        }
        tvSubtitle.setText(subtitle.isEmpty() ? "Bill" : subtitle);

        // date
        if (bill.createdAt != null) {
            tvDate.setText(formatDate(bill.createdAt));
        } else if (bill.date != null) {
            tvDate.setText(formatDate(bill.date));
        } else {
            tvDate.setText("");
        }

        // note
        if (!TextUtils.isEmpty(bill.note)) {
            tvNote.setText(bill.note);
            tvNote.setVisibility(View.VISIBLE);
        } else {
            tvNote.setVisibility(View.GONE);
        }

        // payer
        String payerUid = bill.getPayerUid();
        TripMember payer = memberMap.get(payerUid);
        String payerName = payer != null ? payer.displayName : (payerUid != null ? payerUid : "Unknown");
        tvPayerName.setText(payerName);

        String currency = extractCurrency(bill.currency);
        tvPayerPaidAmount.setText("Paid " + currency + String.format(Locale.getDefault(),"%.2f", bill.amount));

        if (payer != null && !TextUtils.isEmpty(payer.photoUrl)) {
            Glide.with(this).load(payer.photoUrl)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .circleCrop().into(ivPayerAvatar);
        } else {
            ivPayerAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
        }

        // receipts（保留你原逻辑）
        LinearLayout receiptContainer = findViewById(R.id.receiptContainer);
        receiptContainer.removeAllViews();
        if (bill.receiptsBase64 != null && !bill.receiptsBase64.isEmpty()) {
            for (String base64Str : bill.receiptsBase64) {
                try {
                    byte[] imageBytes = Base64.decode(base64Str, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                    ImageView imageView = new ImageView(this);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(240, 240);
                    params.setMargins(12, 0, 12, 0);
                    imageView.setLayoutParams(params);
                    imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imageView.setImageBitmap(bitmap);
                    imageView.setBackgroundResource(R.drawable.bg_rounded);
                    imageView.setClickable(true);
                    imageView.setAdjustViewBounds(true);

                    imageView.setOnClickListener(v -> {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        ImageView fullImage = new ImageView(this);
                        fullImage.setImageBitmap(bitmap);
                        fullImage.setAdjustViewBounds(true);
                        fullImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        builder.setView(fullImage)
                                .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                                .show();
                    });

                    receiptContainer.addView(imageView);
                } catch (Exception ignore) {}
            }
        } else {
            TextView placeholder = new TextView(this);
            placeholder.setText("No receipts uploaded");
            placeholder.setTextColor(getResources().getColor(android.R.color.darker_gray));
            receiptContainer.addView(placeholder);
        }

        // participants
        List<ParticipantBalance> rows = buildParticipantRows(bill, payerName, currency);
        adapter.submit(rows);

        // 地图：如果有坐标就显示并打点，否则隐藏 MapView
        if (mapView != null) {
            Double lat = bill.getLat();
            Double lon = bill.getLon();
            boolean hasGeo = lat != null && lon != null && Math.abs(lat) > 1e-8 && Math.abs(lon) > 1e-8;
            mapView.setVisibility(hasGeo ? View.VISIBLE : View.GONE);
            if (hasGeo && gmap != null) {
                updateMapMarker(new LatLng(lat, lon), bill.getTitle());
            }
        }

        showLoading(false);
    }

    private void updateMapMarker(@NonNull LatLng pos, @Nullable String title) {
        if (gmap == null) return;
        gmap.clear();
        String t = (title == null || title.trim().isEmpty()) ? "Bill" : title.trim();
        gmap.addMarker(new MarkerOptions().position(pos).title(t));
        gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f));
        gmap.getUiSettings().setZoomControlsEnabled(true);
        gmap.getUiSettings().setMapToolbarEnabled(false);
        gmap.getUiSettings().setCompassEnabled(true);
    }

    private List<ParticipantBalance> buildParticipantRows(Bill bill, String payerName, String currency) {
        List<ParticipantBalance> rows = new ArrayList<>();
        String payerUid = bill.getPayerUid();

        TripMember payerMember = memberMap.get(payerUid);
        rows.add(new ParticipantBalance(
                payerUid,
                payerName + (payerUid != null && payerUid.equals(currentUid) ? " (YOU)" : ""),
                payerMember != null ? payerMember.photoUrl : null,
                true,
                "Paid " + currency + String.format(Locale.getDefault(),"%.2f", bill.amount)
        ));

        if (bill.debts != null && !bill.debts.isEmpty()) {
            for (Map<String, Object> debt : bill.debts) {
                String fromUid = (String) debt.get("from");
                String toUid = (String) debt.get("to");
                Object amountObj = debt.get("amount");
                if (fromUid != null && toUid != null && amountObj instanceof Number) {
                    double amount = ((Number) amountObj).doubleValue();
                    TripMember fromMember = memberMap.get(fromUid);
                    String fromName = fromMember != null ? fromMember.displayName : fromUid;
                    rows.add(new ParticipantBalance(
                            fromUid,
                            fromName + (fromUid.equals(currentUid) ? " (YOU)" : ""),
                            fromMember != null ? fromMember.photoUrl : null,
                            false,
                            "owes " + payerName + " " + currency + String.format(Locale.getDefault(),"%.2f", amount)
                    ));
                }
            }
        }

        if ((bill.debts == null || bill.debts.isEmpty())
                && bill.participants != null && !bill.participants.isEmpty()) {
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
                        "owes " + payerName + " " + currency + String.format(Locale.getDefault(),"%.2f", sharePerPerson)
                ));
            }
        }
        return rows;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
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
        db.collection("trips").document(tid)
                .collection("bills").document(bid)
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
            loadAll();
        }
    }

    private String getCategoryEmoji(String category) {
        if (category == null) return "📄";
        switch (category.toLowerCase()) {
            case "dining":
            case "food": return "🍽";
            case "transport":
            case "transportation": return "🚗";
            case "shopping": return "🛍";
            case "accommodation":
            case "hotel": return "🏨";
            case "entertainment": return "🎭";
            default: return "📄";
        }
    }

    private String extractCurrency(String currencyFull) {
        if (currencyFull == null || currencyFull.isEmpty()) return "$";
        String[] parts = currencyFull.split(" ");
        return (parts.length > 0 && !parts[0].isEmpty()) ? parts[0] : "$";
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

    /* ---------------- MapView lifecycle forwarding ---------------- */
    @Override protected void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause() { if (mapView != null) mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { if (mapView != null) mapView.onStop(); super.onStop(); }
    @Override protected void onDestroy() { if (mapView != null) mapView.onDestroy(); super.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            Bundle mapBundle = outState.getBundle(MAP_BUNDLE_KEY);
            if (mapBundle == null) {
                mapBundle = new Bundle();
                outState.putBundle(MAP_BUNDLE_KEY, mapBundle);
            }
            mapView.onSaveInstanceState(mapBundle);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        gmap = googleMap;
        if (bill != null) {
            Double lat = bill.getLat();
            Double lon = bill.getLon();
            if (lat != null && lon != null && Math.abs(lat) > 1e-8 && Math.abs(lon) > 1e-8) {
                updateMapMarker(new LatLng(lat, lon), bill.getTitle());
            }
        }
    }
}