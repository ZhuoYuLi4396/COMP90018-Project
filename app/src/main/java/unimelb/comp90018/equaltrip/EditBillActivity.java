package unimelb.comp90018.equaltrip;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class EditBillActivity extends AppCompatActivity {
    private static final String TAG = "EditBill";

    private EditText etBillName, etMerchant, etLocation, etAmount;
    private TextView tvDate, tvPaidBy, tvParticipants, tvTotalSplit;
    private Spinner spinnerCurrency;
    private Button btnUpdateBill;
    private ImageButton btnReceiptCamera, btnReceiptGallery;
    private RadioButton rbEqual, rbCustomize;
    private RadioGroup rgSplitMethod;

    private LinearLayout layoutSplitDetails;
    private RecyclerView rvSplitAmounts;
    private SplitAmountAdapter splitAdapter;

    private LinearLayout btnDining, btnTransport, btnShopping, btnOther;
    private String selectedCategory = null;

    private LinearLayout receiptPlaceholder;
    private RelativeLayout receiptPreview;
    private FrameLayout layoutReceipt;
    private RecyclerView rvReceipts;
    private ReceiptsAdapter receiptsAdapter;

    private Calendar calendar = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private String selectedPayerUid = null;
    private String selectedPayerUserId = null;
    private ArrayList<String> selectedParticipantUids = new ArrayList<>();
    private ArrayList<String> selectedParticipantUserIds = new ArrayList<>();
    private ArrayList<ParticipantSplit> participantSplits = new ArrayList<>();
    private double totalAmount = 0.0;

    private String tripId;
    private String billId;
    private Bill originalBill;

    private final List<Uri> receiptUris = new ArrayList<>();
    private Uri pendingCameraOutputUri = null;

    private FirebaseFirestore db;
    private Long tripStartDateMs = null;
    private Long tripEndDateMs = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_bill);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle("Edit Bill");
            toolbar.setNavigationOnClickListener(v ->
                    getOnBackPressedDispatcher().onBackPressed()
            );
        }

        tripId = getIntent().getStringExtra("tripId");
        billId = getIntent().getStringExtra("billId");

        if (tripId == null || billId == null) {
            Toast.makeText(this, "Missing trip or bill ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupListeners();
        setupSpinners();
        setupCategoryButtons();
        setupSplitRecyclerView();
        loadTripDateRange();

        loadBillData();
    }

    private void loadBillData() {
        db.collection("trips")
                .document(tripId)
                .collection("bills")
                .document(billId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        originalBill = documentSnapshot.toObject(Bill.class);
                        if (originalBill != null) {
                            originalBill.id = documentSnapshot.getId();
                            populateFields();
                        }
                    } else {
                        Toast.makeText(this, "Bill not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error loading bill: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Load bill failed", e);
                    finish();
                });
    }

    private void loadTripDateRange() {
        FirebaseFirestore.getInstance()
                .collection("trips")
                .document(tripId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Get startDate and endDate (stored as Long milliseconds)
                        Object startObj = documentSnapshot.get("startDate");
                        Object endObj = documentSnapshot.get("endDate");

                        if (startObj instanceof Long) {
                            tripStartDateMs = (Long) startObj;
                        } else if (startObj instanceof Number) {
                            tripStartDateMs = ((Number) startObj).longValue();
                        }

                        if (endObj instanceof Long) {
                            tripEndDateMs = (Long) endObj;
                        } else if (endObj instanceof Number) {
                            tripEndDateMs = ((Number) endObj).longValue();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load trip dates: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void populateFields() {
        if (originalBill == null) return;

        etBillName.setText(originalBill.getTitle());

        // ⭐ Read merchant directly
        if (originalBill.merchant != null) {
            etMerchant.setText(originalBill.merchant);
        }

        // ⭐ Read location directly
        if (originalBill.location != null) {
            etLocation.setText(originalBill.location);
        }

        etAmount.setText(String.format(Locale.getDefault(), "%.2f", originalBill.amount));
        totalAmount = originalBill.amount;

        if (originalBill.createdAt != null) {
            calendar.setTime(originalBill.createdAt.toDate());
            tvDate.setText(dateFormat.format(calendar.getTime()));
        }

        if (originalBill.currency != null) {
            for (int i = 0; i < spinnerCurrency.getCount(); i++) {
                if (spinnerCurrency.getItemAtPosition(i).toString().contains(originalBill.currency)) {
                    spinnerCurrency.setSelection(i);
                    break;
                }
            }
        }

        if (originalBill.category != null) {
            selectedCategory = originalBill.category;
            updateCategorySelection(originalBill.category);
        }

        selectedPayerUid = originalBill.getPayerUid();
        loadPayerName(selectedPayerUid);

        if (originalBill.participants != null) {
            selectedParticipantUids = new ArrayList<>(originalBill.participants);
            loadParticipantNames();
        }

        if (originalBill.receiptUrls != null && !originalBill.receiptUrls.isEmpty()) {
            for (String url : originalBill.receiptUrls) {
                receiptUris.add(Uri.parse(url));
            }
            updateReceiptUI();
        }

        if (originalBill.debts != null && !originalBill.debts.isEmpty()) {
            rbCustomize.setChecked(true);
            loadCustomSplits();
        } else {
            rbEqual.setChecked(true);
        }
    }

    private void loadPayerName(String uid) {
        if (uid == null) return;

        db.collection("trips")
                .document(tripId)
                .collection("members")
                .whereEqualTo("uid", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        String userId = doc.getString("userId");
                        String displayName = doc.getString("displayName");
                        selectedPayerUserId = displayName != null ? displayName : userId;
                        if (selectedPayerUserId == null) selectedPayerUserId = uid;
                        tvPaidBy.setText(selectedPayerUserId);
                    }
                });
    }

    private void loadParticipantNames() {
        if (selectedParticipantUids.isEmpty()) return;

        db.collection("trips")
                .document(tripId)
                .collection("members")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    selectedParticipantUserIds.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String uid = doc.getString("uid");
                        if (uid == null) uid = doc.getId();

                        if (selectedParticipantUids.contains(uid)) {
                            String userId = doc.getString("userId");
                            String displayName = doc.getString("displayName");
                            String name = displayName != null ? displayName : userId;
                            if (name == null) name = uid;
                            selectedParticipantUserIds.add(name);
                        }
                    }
                    tvParticipants.setText(selectedParticipantUserIds.size() + " selected");
                    updateSplitAmounts();
                });
    }

    private void loadCustomSplits() {
        if (originalBill.debts == null) return;

        participantSplits.clear();

        if (selectedPayerUserId != null) {
            participantSplits.add(new ParticipantSplit(selectedPayerUserId, 0.0));
        }

        for (Map<String, Object> debt : originalBill.debts) {
            String fromUid = (String) debt.get("from");
            Object amountObj = debt.get("amount");

            if (fromUid != null && amountObj != null) {
                double amount = ((Number) amountObj).doubleValue();

                int index = selectedParticipantUids.indexOf(fromUid);
                if (index >= 0 && index < selectedParticipantUserIds.size()) {
                    String userName = selectedParticipantUserIds.get(index);
                    participantSplits.add(new ParticipantSplit(userName, amount));
                }
            }
        }

        splitAdapter.updateData(participantSplits);
        updateTotalDisplay();
    }

    private void updateCategorySelection(String category) {
        resetCategoryButtons();

        switch (category.toLowerCase()) {
            case "dining":
                setCategorySelected(btnDining, true);
                break;
            case "transport":
                setCategorySelected(btnTransport, true);
                break;
            case "shopping":
                setCategorySelected(btnShopping, true);
                break;
            case "other":
                setCategorySelected(btnOther, true);
                break;
        }
    }

    private void initializeViews() {
        etBillName = findViewById(R.id.et_bill_name);
        etMerchant = findViewById(R.id.et_merchant);
        etLocation = findViewById(R.id.et_location);
        etAmount = findViewById(R.id.et_amount);

        tvDate = findViewById(R.id.tv_date);
        tvPaidBy = findViewById(R.id.tv_paid_by);
        tvParticipants = findViewById(R.id.tv_participants);
        tvTotalSplit = findViewById(R.id.tv_total_split);

        spinnerCurrency = findViewById(R.id.spinner_currency);

        btnUpdateBill = findViewById(R.id.btn_create_bill);
        btnUpdateBill.setText("Update Bill");

        btnReceiptCamera = findViewById(R.id.btn_receipt_camera);
        btnReceiptGallery = findViewById(R.id.btn_receipt_gallery);

        btnDining = findViewById(R.id.btn_dining);
        btnTransport = findViewById(R.id.btn_transport);
        btnShopping = findViewById(R.id.btn_shopping);
        btnOther = findViewById(R.id.btn_other);

        receiptPlaceholder = findViewById(R.id.receipt_placeholder);
        receiptPreview = findViewById(R.id.receipt_preview);
        layoutReceipt = findViewById(R.id.layout_receipt);

        rbEqual = findViewById(R.id.rb_equal);
        rbCustomize = findViewById(R.id.rb_customize);
        rgSplitMethod = findViewById(R.id.rg_split_method);

        layoutSplitDetails = findViewById(R.id.layout_split_details);
        rvSplitAmounts = findViewById(R.id.rv_split_amounts);

        rvReceipts = findViewById(R.id.rv_receipts);
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvReceipts.setLayoutManager(lm);
        receiptsAdapter = new ReceiptsAdapter(receiptUris, uri -> removeOneReceipt(uri));
        rvReceipts.setAdapter(receiptsAdapter);
    }

    private void setupSplitRecyclerView() {
        rvSplitAmounts.setLayoutManager(new LinearLayoutManager(this));
        splitAdapter = new SplitAmountAdapter(participantSplits,
                (participant, newAmount) -> {
                    updateParticipantAmount(participant, newAmount);
                    updateTotalDisplay();
                });
        rvSplitAmounts.setAdapter(splitAdapter);
    }

    private final ActivityResultLauncher<String> requestCameraPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openCamera();
                else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            });

    private void tryOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            requestCameraPerm.launch(android.Manifest.permission.CAMERA);
        }
    }

    private void setupListeners() {
        tvDate.setOnClickListener(v -> showDatePicker());
        findViewById(R.id.layout_date).setOnClickListener(v -> showDatePicker());

        layoutReceipt.setOnClickListener(v -> {
            if (receiptUris.isEmpty()) {
                showReceiptOptions();
            }
        });

        btnReceiptCamera.setOnClickListener(v -> tryOpenCamera());
        btnReceiptGallery.setOnClickListener(v -> openGallery());

        etAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().isEmpty()) {
                    try {
                        totalAmount = Double.parseDouble(s.toString());
                        updateSplitAmounts();
                    } catch (NumberFormatException e) {
                        totalAmount = 0.0;
                    }
                } else {
                    totalAmount = 0.0;
                    updateSplitAmounts();
                }
            }
        });

        findViewById(R.id.layout_paid_by).setOnClickListener(v -> {
            Intent intent = new Intent(EditBillActivity.this, PayerSelectionActivity.class);
            intent.putExtra("current_payer", selectedPayerUserId);
            intent.putExtra("tripId", tripId);
            startActivityForResult(intent, 1001);
        });

        findViewById(R.id.layout_participants).setOnClickListener(v -> {
            Intent intent = new Intent(EditBillActivity.this, ParticipantsSelectionActivity.class);
            intent.putStringArrayListExtra("selected_participants", selectedParticipantUids);
            intent.putExtra("tripId", tripId);
            startActivityForResult(intent, 1002);
        });

        rgSplitMethod.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isCustomize = checkedId == R.id.rb_customize;
            splitAdapter.setEditableMode(isCustomize);
            updateSplitAmounts();
        });

        btnUpdateBill.setOnClickListener(v -> updateBill());
    }

    private Map<String, Double> calculateSplits(String payer, List<String> participants, double amount) {
        Map<String, Double> result = new HashMap<>();

        if (participants == null || participants.isEmpty()) return result;
        java.math.BigDecimal total = new java.math.BigDecimal(amount);
        java.math.BigDecimal headcount = new java.math.BigDecimal(participants.size());

        java.math.BigDecimal rawShare = total.divide(headcount, 6, java.math.RoundingMode.HALF_UP);

        List<java.math.BigDecimal> shares = new ArrayList<>();
        java.math.BigDecimal sumRounded = java.math.BigDecimal.ZERO;

        for (int i = 0; i < participants.size(); i++) {
            java.math.BigDecimal s = rawShare.setScale(2, java.math.RoundingMode.HALF_UP);
            shares.add(s);
            sumRounded = sumRounded.add(s);
        }

        int cents = total.subtract(sumRounded).movePointRight(2).intValue();
        int idx = 0;
        while (cents != 0 && !shares.isEmpty()) {
            java.math.BigDecimal delta = (cents > 0) ? new java.math.BigDecimal("0.01") : new java.math.BigDecimal("-0.01");
            java.math.BigDecimal newVal = shares.get(idx).add(delta);
            if (newVal.compareTo(java.math.BigDecimal.ZERO) >= 0) {
                shares.set(idx, newVal);
                cents += (cents > 0 ? -1 : 1);
            }
            idx = (idx + 1) % shares.size();
        }

        for (int i = 0; i < participants.size(); i++) {
            result.put(participants.get(i), shares.get(i).doubleValue());
        }

        return result;
    }

    private void updateSplitAmounts() {
        if (selectedParticipantUids.isEmpty()) {
            layoutSplitDetails.setVisibility(View.GONE);
            return;
        }

        layoutSplitDetails.setVisibility(View.VISIBLE);
        participantSplits.clear();

        if (rbEqual.isChecked()) {
            Map<String, Double> splitMap = calculateSplits(
                    selectedPayerUserId,
                    selectedParticipantUserIds,
                    totalAmount
            );

            if (splitMap.containsKey(selectedPayerUserId)) {
                participantSplits.add(new ParticipantSplit(
                        selectedPayerUserId,
                        splitMap.get(selectedPayerUserId)
                ));
            }

            for (String userId : selectedParticipantUserIds) {
                if (userId.equals(selectedPayerUserId)) continue;
                participantSplits.add(new ParticipantSplit(userId, splitMap.get(userId)));
            }

        } else {
            for (String userId : selectedParticipantUserIds) {
                participantSplits.add(new ParticipantSplit(userId, 0.0));
            }
        }

        splitAdapter.updateData(participantSplits);
        updateTotalDisplay();
    }

    private void updateParticipantAmount(String participant, double newAmount) {
        for (ParticipantSplit split : participantSplits) {
            if (split.getName().equals(participant)) {
                split.setAmount(newAmount);
                break;
            }
        }
    }

    private void updateTotalDisplay() {
        double splitTotal = 0;
        for (ParticipantSplit split : participantSplits) {
            splitTotal += split.getAmount();
        }

        String currency = spinnerCurrency.getSelectedItem() != null ?
                spinnerCurrency.getSelectedItem().toString().substring(0, 1) : "$";

        tvTotalSplit.setText(String.format(Locale.getDefault(), "Total: %s %.2f", currency, splitTotal));

        if (rbCustomize.isChecked() && Math.abs(splitTotal - totalAmount) > 0.01) {
            tvTotalSplit.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            tvTotalSplit.setText(String.format(Locale.getDefault(),
                    "Total: %s %.2f (Should be %s %.2f)",
                    currency, splitTotal, currency, totalAmount));
        } else {
            tvTotalSplit.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        }
    }

    private void setupCategoryButtons() {
        View.OnClickListener categoryClickListener = v -> {
            resetCategoryButtons();

            if (v == btnDining) {
                selectedCategory = "dining";
                setCategorySelected(btnDining, true);
            } else if (v == btnTransport) {
                selectedCategory = "transport";
                setCategorySelected(btnTransport, true);
            } else if (v == btnShopping) {
                selectedCategory = "shopping";
                setCategorySelected(btnShopping, true);
            } else if (v == btnOther) {
                selectedCategory = "other";
                setCategorySelected(btnOther, true);
            }
        };

        btnDining.setOnClickListener(categoryClickListener);
        btnTransport.setOnClickListener(categoryClickListener);
        btnShopping.setOnClickListener(categoryClickListener);
        btnOther.setOnClickListener(categoryClickListener);

        resetCategoryButtons();
    }

    private void resetCategoryButtons() {
        setCategorySelected(btnDining, false);
        setCategorySelected(btnTransport, false);
        setCategorySelected(btnShopping, false);
        setCategorySelected(btnOther, false);
    }

    private void setCategorySelected(LinearLayout button, boolean isSelected) {
        if (isSelected) {
            button.setBackgroundResource(R.drawable.category_selected_bg);
            TextView textView = (TextView) button.getChildAt(1);
            ImageView imageView = (ImageView) button.getChildAt(0);
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            imageView.setColorFilter(ContextCompat.getColor(this, android.R.color.white));
        } else {
            button.setBackgroundResource(R.drawable.category_unselected_bg);
            TextView textView = (TextView) button.getChildAt(1);
            ImageView imageView = (ImageView) button.getChildAt(0);
            textView.setTextColor(ContextCompat.getColor(this, R.color.gray_text));
            imageView.setColorFilter(ContextCompat.getColor(this, R.color.gray_text));
        }
    }

    private void setupSpinners() {
        String[] currencies = {
                "$ AUD Australian Dollar",
                "$ USD US Dollar",
                "€ EUR Euro",
                "£ GBP British Pound",
                "¥ JPY Japanese Yen"
        };
        ArrayAdapter<String> currencyAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, currencies
        );
        spinnerCurrency.setAdapter(currencyAdapter);
        spinnerCurrency.setSelection(0);
    }

    private void showDatePicker() {
        // Check if trip date range is loaded
        if (tripStartDateMs == null || tripEndDateMs == null) {
            Toast.makeText(this, "Loading trip dates, please wait...", Toast.LENGTH_SHORT).show();
            return;
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    // Validate the selected date is within trip range
                    long selectedMs = calendar.getTimeInMillis();
                    if (selectedMs < tripStartDateMs || selectedMs > tripEndDateMs) {
                        Toast.makeText(this,
                                "Please select a date within the trip period",
                                Toast.LENGTH_SHORT).show();
                        // Reset to trip start date
                        calendar.setTimeInMillis(tripStartDateMs);
                    }

                    tvDate.setText(dateFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        // Set min and max dates on the date picker
        datePickerDialog.getDatePicker().setMinDate(tripStartDateMs);
        datePickerDialog.getDatePicker().setMaxDate(tripEndDateMs);

        datePickerDialog.show();
//        DatePickerDialog datePickerDialog = new DatePickerDialog(
//                this,
//                (view, year, month, dayOfMonth) -> {
//                    calendar.set(Calendar.YEAR, year);
//                    calendar.set(Calendar.MONTH, month);
//                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
//                    tvDate.setText(dateFormat.format(calendar.getTime()));
//                },
//                calendar.get(Calendar.YEAR),
//                calendar.get(Calendar.MONTH),
//                calendar.get(Calendar.DAY_OF_MONTH)
//        );
//        datePickerDialog.show();
    }

    private void showReceiptOptions() {
        new AlertDialog.Builder(this)
                .setTitle("Add Receipt")
                .setItems(new String[]{"Take Photo", "Choose from Gallery"},
                        (dialog, which) -> {
                            if (which == 0) {
                                tryOpenCamera();
                            } else {
                                openGallery();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void openCamera() {
        try {
            pendingCameraOutputUri = createImageOutputUri();
            if (pendingCameraOutputUri != null) {
                takePictureLauncher.launch(pendingCameraOutputUri);
            } else {
                Toast.makeText(this, "Failed to create photo file", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this,
                    "Open camera failed: " + e.getClass().getSimpleName() + " - " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private Uri createImageOutputUri() throws IOException {
        File picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDir == null) return null;
        if (!picturesDir.exists()) picturesDir.mkdirs();

        String fileName = "receipt_" + System.currentTimeMillis() + ".jpg";
        File imageFile = new File(picturesDir, fileName);
        if (!imageFile.exists()) imageFile.createNewFile();

        return FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                imageFile
        );
    }

    private void openGallery() {
        pickImagesLauncher.launch(new String[]{"image/*"});
    }

    void updateReceiptUI() {
        boolean hasReceipt = !receiptUris.isEmpty();
        if (hasReceipt) {
            receiptPlaceholder.setVisibility(View.GONE);
            receiptPreview.setVisibility(View.VISIBLE);
            receiptsAdapter.notifyDataSetChanged();
        } else {
            receiptPlaceholder.setVisibility(View.VISIBLE);
            receiptPreview.setVisibility(View.GONE);
        }
    }

    void removeOneReceipt(Uri uri) {
        if (receiptUris.remove(uri)) {
            cleanupIfTempFile(uri);
            updateReceiptUI();
        }
    }

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && pendingCameraOutputUri != null) {
                    receiptUris.add(pendingCameraOutputUri);
                    pendingCameraOutputUri = null;
                    updateReceiptUI();
                } else {
                    cleanupIfTempFile(pendingCameraOutputUri);
                    pendingCameraOutputUri = null;
                }
            });

    private final ActivityResultLauncher<String[]> pickImagesLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris == null || uris.isEmpty()) return;
                for (Uri uri : uris) {
                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                    } catch (Exception ignored) {}
                }
                receiptUris.addAll(uris);
                updateReceiptUI();
            });

    private void cleanupIfTempFile(Uri uri) {
        if (uri == null) return;
        try {
            File picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (picturesDir != null) {
                File maybeFile = new File(picturesDir, new File(uri.getPath()).getName());
                if (maybeFile.exists()) {
                    if (maybeFile.getAbsolutePath().startsWith(picturesDir.getAbsolutePath())) {
                        maybeFile.delete();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private String getUidFromUserId(String userId) {
        int index = selectedParticipantUserIds.indexOf(userId);
        if (index != -1 && index < selectedParticipantUids.size()) {
            return selectedParticipantUids.get(index);
        }
        return null;
    }

    private void updateBill() {
        if (etBillName.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please enter bill name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (etMerchant.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please enter merchant name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (etLocation.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please enter location", Toast.LENGTH_SHORT).show();
            return;
        }

        if (etAmount.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedParticipantUids.isEmpty()) {
            Toast.makeText(this, "Please select participants", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedPayerUid == null || selectedPayerUserId == null || selectedPayerUserId.equals("None selected")) {
            Toast.makeText(this, "Please select a payer", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedParticipantUids.size() == 1 && selectedParticipantUids.get(0).equals(selectedPayerUid)) {
            new AlertDialog.Builder(this)
                    .setTitle("Invalid Split")
                    .setMessage("Payer cannot be the only participant.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        double value = Double.parseDouble(etAmount.getText().toString().trim());
        if (value <= 0) {
            Toast.makeText(this, "Amount must be greater than 0", Toast.LENGTH_SHORT).show();
            return;
        }

        if (rbCustomize.isChecked()) {
            double splitTotal = 0;
            for (ParticipantSplit split : participantSplits) {
                splitTotal += split.getAmount();
            }

            if (Math.abs(splitTotal - totalAmount) > 0.01) {
                new AlertDialog.Builder(this)
                        .setTitle("Split Amount Mismatch")
                        .setMessage(String.format(Locale.getDefault(),
                                "The total split amount (%.2f) doesn't match the bill amount (%.2f). Please adjust the individual amounts.",
                                splitTotal, totalAmount))
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }
        }

        Map<String, Object> billData = new HashMap<>();
        billData.put("billName", etBillName.getText().toString().trim());
        billData.put("merchant", etMerchant.getText().toString().trim());
        billData.put("location", etLocation.getText().toString().trim());
        billData.put("category", selectedCategory);
        billData.put("amount", totalAmount);
        billData.put("currency", spinnerCurrency.getSelectedItem().toString());
        billData.put("paidBy", selectedPayerUid);
        billData.put("participants", new ArrayList<>(selectedParticipantUids));
        billData.put("createdAt", new Timestamp(calendar.getTime()));
        billData.put("date", new Timestamp(calendar.getTime()));
//        billData.put("updatedAt", Timestamp.now());

        List<Map<String, Object>> debtsList = new ArrayList<>();
        for (ParticipantSplit split : participantSplits) {
            if (!split.getName().equals(selectedPayerUserId)) {
                Map<String, Object> debt = new HashMap<>();
                String fromUid = getUidFromUserId(split.getName());
                if (fromUid != null) {
                    debt.put("from", fromUid);
                    debt.put("to", selectedPayerUid);
                    debt.put("amount", split.getAmount());
                    debtsList.add(debt);
                }
            }
        }
        billData.put("debts", debtsList);

        btnUpdateBill.setEnabled(false);
        db.collection("trips")
                .document(tripId)
                .collection("bills")
                .document(billId)
                .update(billData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Bill updated successfully!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnUpdateBill.setEnabled(true);
                    Toast.makeText(this, "Error updating bill: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Update failed", e);
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == 1001 && data != null) {
                selectedPayerUid = data.getStringExtra("selected_payer_uid");
                selectedPayerUserId = data.getStringExtra("selected_payer_userid");
                tvPaidBy.setText(selectedPayerUserId);
                updateSplitAmounts();
            } else if (requestCode == 1002 && data != null) {
                selectedParticipantUids = data.getStringArrayListExtra("selected_participant_uids");
                selectedParticipantUserIds = data.getStringArrayListExtra("selected_participant_userids");
                if (selectedParticipantUserIds != null && !selectedParticipantUserIds.isEmpty()) {
                    tvParticipants.setText(selectedParticipantUserIds.size() + " selected");
                    updateSplitAmounts();
                } else {
                    tvParticipants.setText("None selected");
                    layoutSplitDetails.setVisibility(View.GONE);
                }
            }
        }
    }

    private static class ReceiptVH extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        ImageButton btnDelete;
        ReceiptVH(View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.iv_thumb);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }

    private interface OnReceiptRemove {
        void onRemove(Uri uri);
    }

    private class ReceiptsAdapter extends RecyclerView.Adapter<ReceiptVH> {
        private final List<Uri> data;
        private final OnReceiptRemove onRemove;

        ReceiptsAdapter(List<Uri> data, OnReceiptRemove onRemove) {
            this.data = data;
            this.onRemove = onRemove;
        }

        @Override
        public ReceiptVH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_receipt_thumbnail, parent, false);
            return new ReceiptVH(v);
        }

        @Override
        public void onBindViewHolder(ReceiptVH holder, int position) {
            Uri uri = data.get(position);
            holder.ivThumb.setImageURI(uri);
            holder.btnDelete.setOnClickListener(v -> onRemove.onRemove(uri));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }
}