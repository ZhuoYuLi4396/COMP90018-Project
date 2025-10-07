package unimelb.comp90018.equaltrip;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

// Firebase
import android.content.pm.PackageManager;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

// OkHttp
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

// JSON
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

// Activity Result
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

// ML Kit - Text Recognition
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

public class AddBillActivity extends AppCompatActivity {
    private EditText etBillName, etMerchant, etLocation, etAmount;
    private TextView tvDate, tvPaidBy, tvParticipants, tvTotalSplit;
    private Spinner spinnerCurrency;
    private Button btnCreateBill;
    private ImageButton btnReceiptCamera, btnReceiptGallery;
    private RadioButton rbEqual, rbCustomize;
    private RadioGroup rgSplitMethod;

    // Split amount views
    private LinearLayout layoutSplitDetails;
    private RecyclerView rvSplitAmounts;
    private SplitAmountAdapter splitAdapter;

    // Category buttons
    private LinearLayout btnDining, btnTransport, btnShopping, btnOther;
    private String selectedCategory = null; // 默认不选，让自动分类来点亮

    // Receipt views
    private LinearLayout receiptPlaceholder;
    private RelativeLayout receiptPreview;
    private FrameLayout layoutReceipt;

    private Calendar calendar = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private String selectedPayerUid = null;      // 存数据库
    private String selectedPayerUserId = null;   // UI 展示
    private ArrayList<String> selectedParticipantUids = new ArrayList<>();      // 存数据库
    private ArrayList<String> selectedParticipantUserIds = new ArrayList<>();   // UI 展示
    private ArrayList<ParticipantSplit> participantSplits = new ArrayList<>();
    private boolean hasReceipt = false;
    private Bitmap receiptBitmap = null;
    private double totalAmount = 0.0;

    // tripId
    private String tripId;

    // Receipts (multi-images)
    private RecyclerView rvReceipts;
    private ReceiptsAdapter receiptsAdapter;
    private final List<Uri> receiptUris = new ArrayList<>();
    private Uri pendingCameraOutputUri = null;

    // 经纬度（由地理编码得到）
    private double latitude = 0.0;
    private double longitude = 0.0;
    private Long tripStartDateMs = null;
    private Long tripEndDateMs = null;

    // === OCR Views ===
    private View ocrOverlay;                // 半透明遮罩
    private TextView tvOcrStatus;           // 遮罩上的“Recognizing…”
    private LinearLayout layoutOcrResult;   // 蓝色结果条容器
    private TextView tvOcrResult;           // 结果条文本
    private Button btnRetryOcr;             // “Retry OCR”按钮

    // === ML Kit Recognizers ===
    private TextRecognizer latinRecognizer;
    private TextRecognizer chineseRecognizer; // 备选（中文/混排更稳）

    // 控制下一次操作是否要自动识图
    private boolean ocrAfterNextCapture = false;
    private boolean ocrAfterNextGalleryPick = false;

    // —— 返回给 HomeActivity 的常量 —— //
    public static final String EXTRA_MARKER_LAT   = "extra_marker_lat";
    public static final String EXTRA_MARKER_LNG   = "extra_marker_lng";
    public static final String EXTRA_MARKER_TITLE = "extra_marker_title";

    // —— Nominatim HTTP 客户端 —— //
    private final OkHttpClient http = new OkHttpClient();

    // —— 频率限制：去抖/冷却控制 —— //
    private static final long MIN_NOMINATIM_INTERVAL_MS = 800; // 建议 800~1000ms
    private long lastNominatimCallMs = 0L;
    private Double pendingLat = null, pendingLon = null;
    private Runnable pendingAfter = null;

    // —— 分类：用户是否手动选择过，若手动则不再被自动覆盖 —— //
    private boolean categoryManuallyChosen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_bill);

        // === OCR Views ===
        ocrOverlay      = findViewById(R.id.ocr_overlay);
        tvOcrStatus     = findViewById(R.id.tv_ocr_status);
        layoutOcrResult = findViewById(R.id.layout_ocr_result);
        tvOcrResult     = findViewById(R.id.tv_ocr_result);
        btnRetryOcr     = findViewById(R.id.btn_retry_ocr);

        // === ML Kit Recognizers ===
        latinRecognizer   = TextRecognition.getClient(new TextRecognizerOptions.Builder().build());
        chineseRecognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());

        // 从 Trip Detail 带入 tripId
        tripId = getIntent().getStringExtra("tripId");
        if (tripId == null || tripId.isEmpty()) {
            Toast.makeText(this, "Missing tripId", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupListeners();
        setupSpinners();
        setupCategoryButtons();
        setupSplitRecyclerView();

        // 默认日期
        tvDate.setText(dateFormat.format(calendar.getTime()));
        loadTripDateRange();
    }

    private void loadTripDateRange() {
        FirebaseFirestore.getInstance()
                .collection("trips")
                .document(tripId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Object startObj = documentSnapshot.get("startDate");
                        Object endObj   = documentSnapshot.get("endDate");

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

                        if (tripStartDateMs != null) {
                            calendar.setTimeInMillis(tripStartDateMs);
                            tvDate.setText(dateFormat.format(calendar.getTime()));
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load trip dates: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void initializeViews() {
        // Edit texts
        etBillName = findViewById(R.id.et_bill_name);
        etMerchant = findViewById(R.id.et_merchant);
        etLocation = findViewById(R.id.et_location);
        etAmount   = findViewById(R.id.et_amount);

        // Text views
        tvDate         = findViewById(R.id.tv_date);
        tvPaidBy       = findViewById(R.id.tv_paid_by);
        tvParticipants = findViewById(R.id.tv_participants);
        tvTotalSplit   = findViewById(R.id.tv_total_split);
        tvPaidBy.setText("None selected");

        // Spinners
        spinnerCurrency = findViewById(R.id.spinner_currency);

        // Buttons
        btnCreateBill     = findViewById(R.id.btn_create_bill);
        btnReceiptCamera  = findViewById(R.id.btn_receipt_camera);
        btnReceiptGallery = findViewById(R.id.btn_receipt_gallery);

        // Category buttons
        btnDining    = findViewById(R.id.btn_dining);
        btnTransport = findViewById(R.id.btn_transport);
        btnShopping  = findViewById(R.id.btn_shopping);
        btnOther     = findViewById(R.id.btn_other);

        // Receipt views
        receiptPlaceholder = findViewById(R.id.receipt_placeholder);
        receiptPreview     = findViewById(R.id.receipt_preview);
        layoutReceipt      = findViewById(R.id.layout_receipt);

        // Split method radio buttons
        rbEqual     = findViewById(R.id.rb_equal);
        rbCustomize = findViewById(R.id.rb_customize);
        rgSplitMethod = findViewById(R.id.rg_split_method);

        // Split amount views
        layoutSplitDetails = findViewById(R.id.layout_split_details);
        rvSplitAmounts     = findViewById(R.id.rv_split_amounts);

        // Back
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Receipts list
        rvReceipts = findViewById(R.id.rv_receipts);
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvReceipts.setLayoutManager(lm);
        receiptsAdapter = new ReceiptsAdapter(receiptUris, this::removeOneReceipt);
        rvReceipts.setAdapter(receiptsAdapter);
    }

    private void setupSplitRecyclerView() {
        rvSplitAmounts.setLayoutManager(new LinearLayoutManager(this));
        splitAdapter = new SplitAmountAdapter(participantSplits, (participant, newAmount) -> {
            updateParticipantAmount(participant, newAmount);
            updateTotalDisplay();
        });
        rvSplitAmounts.setAdapter(splitAdapter);
    }

    // 摄像头权限
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
        // Date picker
        tvDate.setOnClickListener(v -> showDatePicker());
        findViewById(R.id.layout_date).setOnClickListener(v -> showDatePicker());

        // Receipt handling：统一走弹窗（含 OCR 选项）
        layoutReceipt.setOnClickListener(v -> showReceiptOptions());
        btnReceiptCamera.setOnClickListener(v -> showReceiptOptions());
        btnReceiptGallery.setOnClickListener(v -> showReceiptOptions());

        // Amount input
        etAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
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

        // Paid by
        findViewById(R.id.layout_paid_by).setOnClickListener(v -> {
            Intent intent = new Intent(AddBillActivity.this, PayerSelectionActivity.class);
            intent.putExtra("current_payer", selectedPayerUserId);
            intent.putExtra("tripId", tripId);
            startActivityForResult(intent, 1001);
        });

        // Participants
        findViewById(R.id.layout_participants).setOnClickListener(v -> {
            Intent intent = new Intent(AddBillActivity.this, ParticipantsSelectionActivity.class);
            intent.putStringArrayListExtra("selected_participant_uids", selectedParticipantUids);
            intent.putStringArrayListExtra("selected_participant_userids", selectedParticipantUserIds);
            intent.putExtra("tripId", tripId);
            startActivityForResult(intent, 1002);
        });

        // Split method
        rgSplitMethod.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isCustomize = checkedId == R.id.rb_customize;
            splitAdapter.setEditableMode(isCustomize);
            updateSplitAmounts();
        });

        // Create Bill
        btnCreateBill.setOnClickListener(v -> createBill());

        btnRetryOcr.setOnClickListener(v -> {
            if (receiptUris.isEmpty()) {
                Toast.makeText(this, "No receipt image to recognize", Toast.LENGTH_SHORT).show();
                return;
            }
            Uri target = receiptUris.get(receiptUris.size() - 1);
            runOcrOnUri(target);
        });
    }

    private void showReceiptOptions() {
        new AlertDialog.Builder(this)
                .setTitle("Add Receipt")
                .setItems(new String[]{
                        "Take Photo",
                        "Choose from Gallery",
                        "Scan & Autofill (Camera)",
                        "Scan & Autofill (Gallery)"
                }, (dialog, which) -> {
                    switch (which) {
                        case 0: // 普通拍照（不识图）
                            ocrAfterNextCapture = false;
                            tryOpenCamera();
                            break;
                        case 1: // 普通相册（不识图）
                            ocrAfterNextGalleryPick = false;
                            openGallery();
                            break;
                        case 2: // 拍照并识图
                            ocrAfterNextCapture = true;
                            tryOpenCamera();
                            break;
                        case 3: // 相册并识图（单选）
                            ocrAfterNextGalleryPick = true;
                            openGalleryForOcrSingle();
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ====== 相机/相册 ======
    void openCamera() {
        try {
            pendingCameraOutputUri = createImageOutputUri(); // 生成 content:// Uri
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

    private void openGalleryForOcrSingle() {
        pickOneImageForOcr.launch("image/*");
    }

    private void showReceiptPreview() {
        hasReceipt = true;
        receiptPlaceholder.setVisibility(View.GONE);
        receiptPreview.setVisibility(View.VISIBLE);
    }

    private void removeReceipt() {
        hasReceipt = false;
        receiptBitmap = null;
        receiptPlaceholder.setVisibility(View.VISIBLE);
        receiptPreview.setVisibility(View.GONE);
    }

    // ====== 计算拆分 ======
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
        if (selectedParticipantUserIds.isEmpty()) {
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
        for (ParticipantSplit split : participantSplits) splitTotal += split.getAmount();

        String currency = spinnerCurrency.getSelectedItem() != null ?
                spinnerCurrency.getSelectedItem().toString().substring(0, 1) : "$";

        tvTotalSplit.setText(String.format(Locale.getDefault(), "Total: %s %.2f", currency, splitTotal));

        if (rbCustomize.isChecked() && Math.abs(splitTotal - totalAmount) > 0.01) {
            tvTotalSplit.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            tvTotalSplit.setText(String.format(Locale.getDefault(),
                    "Total: %s %.2f (Should be %s %.2f)", currency, splitTotal, currency, totalAmount));
        } else {
            tvTotalSplit.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        }
    }

    private void setupCategoryButtons() {
        View.OnClickListener categoryClickListener = v -> {
            categoryManuallyChosen = true; // 用户手动选择了分类，后续不再自动覆盖
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

        // 启动时所有分类都熄灭
        resetCategoryButtons();
    }

    private void resetCategoryButtons() {
        setCategorySelected(btnDining, false);
        setCategorySelected(btnTransport, false);
        setCategorySelected(btnShopping, false);
        setCategorySelected(btnOther, false);
    }

    private void setCategorySelected(LinearLayout button, boolean isSelected) {
        if (button == null) return;
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

                    long selectedMs = calendar.getTimeInMillis();
                    if (selectedMs < tripStartDateMs || selectedMs > tripEndDateMs) {
                        Toast.makeText(this,
                                "Please select a date within the trip period",
                                Toast.LENGTH_SHORT).show();
                        calendar.setTimeInMillis(tripStartDateMs);
                    }

                    tvDate.setText(dateFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.getDatePicker().setMinDate(tripStartDateMs);
        datePickerDialog.getDatePicker().setMaxDate(tripEndDateMs);
        datePickerDialog.show();
    }

    /** =========================
     *   创建账单 -> 地理编码(正向+反向自动分类) -> 上传 -> 回传给 Home
     *  ========================= */
    private void createBill() {
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
            for (ParticipantSplit split : participantSplits) splitTotal += split.getAmount();
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

        final String address = etLocation.getText().toString().trim();
        geocodeForward(address, new GeoCallback() {
            @Override public void onSuccess(double lat, double lon) {
                latitude = lat;
                longitude = lon;
                fetchCategoryFromNominatimDebounced(lat, lon, AddBillActivity.this::uploadBillWithGeoThenReturn);
            }
            @Override public void onError(String msg) {
                latitude = 0.0;
                longitude = 0.0;
                Toast.makeText(AddBillActivity.this, "Locate failed: " + msg, Toast.LENGTH_SHORT).show();
                uploadBillWithGeoThenReturn();
            }
        });
    }

    /** 正向地理编码 */
    private void geocodeForward(String query, GeoCallback cb) {
        final String q;
        try {
            q = java.net.URLEncoder.encode(query, "UTF-8");
        } catch (Exception e) {
            runOnUiThread(() -> cb.onError("Encode error: " + e.getMessage()));
            return;
        }

        String url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + q;

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "EqualTrip/1.0")
                .build();

        http.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                runOnUiThread(() -> cb.onError(e.getMessage()));
            }

            @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        runOnUiThread(() -> cb.onError("HTTP " + response.code()));
                        return;
                    }
                    String body = (response.body() != null) ? response.body().string() : "";
                    try {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() == 0) {
                            runOnUiThread(() -> cb.onError("No result"));
                            return;
                        }

                        JSONObject o = arr.getJSONObject(0);

                        String latStr = o.optString("lat", null);
                        String lonStr = o.optString("lon", null);
                        double lat = (latStr != null && !latStr.isEmpty()) ? Double.parseDouble(latStr) : o.optDouble("lat", Double.NaN);
                        double lon = (lonStr != null && !lonStr.isEmpty()) ? Double.parseDouble(lonStr) : o.optDouble("lon", Double.NaN);
                        if (Double.isNaN(lat) || Double.isNaN(lon)) {
                            runOnUiThread(() -> cb.onError("Bad coordinates"));
                            return;
                        }

                        String cls = o.optString("class", "");
                        String typ = o.optString("type", "");
                        String shop = o.optString("shop", "");
                        String amenity = o.optString("amenity", "");

                        String raw = ("class:" + cls + " type:" + typ
                                + (shop.isEmpty() ? "" : " shop:" + shop)
                                + (amenity.isEmpty() ? "" : " amenity:" + amenity));

                        runOnUiThread(() -> {
                            autoSelectCategory(raw);
                            cb.onSuccess(lat, lon);
                        });

                    } catch (JSONException | NumberFormatException e) {
                        runOnUiThread(() -> cb.onError("Parse JSON failed"));
                    }
                } finally {
                    if (response != null) response.close();
                }
            }

            private boolean isEmpty(String s) { return s == null || s.isEmpty(); }
        });
    }

    /** 去抖 + 冷却 */
    private void fetchCategoryFromNominatimDebounced(double lat, double lon, Runnable after) {
        long now = System.currentTimeMillis();
        long since = now - lastNominatimCallMs;

        if (since < MIN_NOMINATIM_INTERVAL_MS) {
            pendingLat = lat; pendingLon = lon; pendingAfter = after;
            long delay = MIN_NOMINATIM_INTERVAL_MS - since;
            new Handler(getMainLooper()).postDelayed(() -> {
                if (pendingLat != null && pendingLon != null) {
                    double pl = pendingLat, pn = pendingLon;
                    Runnable pa = pendingAfter;
                    pendingLat = pendingLon = null; pendingAfter = null;
                    fetchCategoryFromNominatimInternal(pl, pn, pa);
                }
            }, delay);
            return;
        }
        fetchCategoryFromNominatimInternal(lat, lon, after);
    }

    /** 反向地理编码（含重试） */
    private void fetchCategoryFromNominatimInternal(double lat, double lon, Runnable after) {
        lastNominatimCallMs = System.currentTimeMillis();

        String url = "https://nominatim.openstreetmap.org/reverse?format=json"
                + "&lat=" + lat + "&lon=" + lon
                + "&zoom=18&addressdetails=1&extratags=1";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "EqualTrip/1.0")
                .build();

        http.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> { if (after != null) after.run(); });
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    int code = response.code();

                    if (code == 429 || code == 503) {
                        long retryDelayMs = 1000;
                        String ra = response.header("Retry-After");
                        if (ra != null) {
                            try { retryDelayMs = Long.parseLong(ra.trim()) * 1000L; } catch (Exception ignored) {}
                        }
                        response.close();
                        long delay = Math.max(retryDelayMs, MIN_NOMINATIM_INTERVAL_MS);
                        new Handler(getMainLooper()).postDelayed(
                                () -> fetchCategoryFromNominatimDebounced(lat, lon, after),
                                delay
                        );
                        return;
                    }

                    if (!response.isSuccessful()) {
                        response.close();
                        runOnUiThread(() -> { if (after != null) after.run(); });
                        return;
                    }

                    String body = response.body().string();
                    response.close();

                    JSONObject json = new JSONObject(body);
                    String cls = json.optString("class", "");
                    String typ = json.optString("type", "");
                    JSONObject extra = json.optJSONObject("extratags");
                    String amenity = extra != null ? extra.optString("amenity", "") : "";
                    String shop    = extra != null ? extra.optString("shop", "")    : "";
                    String raw = ("class:" + cls + " type:" + typ
                            + (shop.isEmpty() ? "" : " shop:" + shop)
                            + (amenity.isEmpty() ? "" : " amenity:" + amenity));

                    runOnUiThread(() -> {
                        autoSelectCategory(raw);
                        if (after != null) after.run();
                    });
                } catch (Exception ex) {
                    runOnUiThread(() -> { if (after != null) after.run(); });
                }
            }
        });
    }

    /** 根据 Nominatim 返回的字段自动分类（除非用户手动点过） */
    private void autoSelectCategory(String raw) {
        if (categoryManuallyChosen) return;
        if (raw == null) raw = "";
        String s = raw.toLowerCase(Locale.ROOT);

        resetCategoryButtons();

        // Dining
        if (s.contains("amenity:restaurant") || s.contains("amenity:cafe") ||
                s.contains("amenity:fast_food")  || s.contains("amenity:bar")  ||
                s.contains("amenity:pub")        || s.contains("restaurant")   ||
                s.contains("cafe")                || s.contains("food")) {
            selectedCategory = "dining";
            setCategorySelected(btnDining, true);
            return;
        }

        // Shopping
        if (s.contains("class:shop") || s.contains("shop:") ||
                s.contains("mall") || s.contains("supermarket") || s.contains("retail")) {
            selectedCategory = "shopping";
            setCategorySelected(btnShopping, true);
            return;
        }

        // Transport
        if (s.contains("public_transport") || s.contains("railway") ||
                s.contains("bus_station")      || s.contains("bus_stop") ||
                s.contains("aeroway")          || s.contains("highway")  ||
                s.contains("transport")) {
            selectedCategory = "transport";
            setCategorySelected(btnTransport, true);
            return;
        }

        // Other
        selectedCategory = "other";
        setCategorySelected(btnOther, true);
    }

    /** 上传 Firestore（含收据上传到 Firebase Storage），成功后回传坐标再关闭 */
    private void uploadBillWithGeoThenReturn() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String billId = db.collection("trips").document(tripId)
                .collection("bills").document().getId();

        List<String> receiptUrlList = new ArrayList<>();

        Map<String, Object> billData = new HashMap<>();
        billData.put("billName", etBillName.getText().toString().trim());
        billData.put("merchant", etMerchant.getText().toString().trim());
        billData.put("location", etLocation.getText().toString().trim());
        billData.put("category", selectedCategory);
        billData.put("amount", totalAmount);

        Map<String, Object> geoPoint = new HashMap<>();
        geoPoint.put("lat", latitude);
        geoPoint.put("lon", longitude);
        billData.put("geo", geoPoint);

        billData.put("receiptUrls", receiptUrlList);
        billData.put("currency", spinnerCurrency.getSelectedItem().toString());
        billData.put("paidBy", selectedPayerUid);
        billData.put("participants", new ArrayList<>(selectedParticipantUids));
        billData.put("createdAt", new Timestamp(calendar.getTime()));
        billData.put("date", new Timestamp(calendar.getTime()));

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

        StorageReference storageRef = FirebaseStorage.getInstance().getReference();

        if (receiptUris.isEmpty()) {
            db.collection("trips").document(tripId)
                    .collection("bills").document(billId)
                    .set(billData)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Bill uploaded successfully!", Toast.LENGTH_SHORT).show();
                        returnMarkerToHomeAndFinish();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Error uploading bill: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        } else {
            List<String> uploaded = new ArrayList<>();
            for (Uri uri : receiptUris) {
                StorageReference fileRef = storageRef.child("receipts/" + tripId + "/" + System.currentTimeMillis() + ".jpg");
                fileRef.putFile(uri)
                        .addOnSuccessListener(taskSnapshot ->
                                fileRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                                    uploaded.add(downloadUri.toString());
                                    if (uploaded.size() == receiptUris.size()) {
                                        billData.put("receiptUrls", uploaded);
                                        db.collection("trips").document(tripId)
                                                .collection("bills").document(billId)
                                                .set(billData)
                                                .addOnSuccessListener(aVoid -> {
                                                    Toast.makeText(this, "Bill uploaded with images!", Toast.LENGTH_SHORT).show();
                                                    returnMarkerToHomeAndFinish();
                                                })
                                                .addOnFailureListener(e ->
                                                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                                                );
                                    }
                                })
                        )
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                        );
            }
        }
    }

    private String getUidFromUserId(String userId) {
        int index = selectedParticipantUserIds.indexOf(userId);
        if (index != -1 && index < selectedParticipantUids.size()) {
            return selectedParticipantUids.get(index);
        }
        return null;
    }

    /** 回传坐标和标题给 HomeActivity，并结束页面 */
    private void returnMarkerToHomeAndFinish() {
        String title = etBillName.getText().toString().trim();
        if (title.isEmpty()) title = etMerchant.getText().toString().trim();

        Intent data = new Intent();
        data.putExtra(EXTRA_MARKER_LAT, latitude);
        data.putExtra(EXTRA_MARKER_LNG, longitude);
        data.putExtra(EXTRA_MARKER_TITLE, title);
        setResult(RESULT_OK, data);
        finish();
    }

    // ======= Activity results for payer/participants =======
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

    // ======= Camera/Gallery results =======
    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && pendingCameraOutputUri != null) {
                    Uri justTaken = pendingCameraOutputUri;
                    receiptUris.add(justTaken);
                    pendingCameraOutputUri = null;
                    updateReceiptUI();

                    // ★ 只有当选择了“Scan & Autofill (Camera)”时才识图
                    if (ocrAfterNextCapture) {
                        ocrAfterNextCapture = false; // 用完即清
                        runOcrOnUri(justTaken);
                    }
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
                // 不自动识图
            });

    private final ActivityResultLauncher<String> pickOneImageForOcr =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (Exception ignored) {}
                receiptUris.add(uri);
                updateReceiptUI();

                if (ocrAfterNextGalleryPick) {
                    ocrAfterNextGalleryPick = false;
                    runOcrOnUri(uri);
                }
            });

    // 根据列表是否为空切换 placeholder/preview，并刷新列表
    void updateReceiptUI() {
        hasReceipt = !receiptUris.isEmpty();
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

    void removeAllReceipts() {
        for (Uri uri : new ArrayList<>(receiptUris)) cleanupIfTempFile(uri);
        receiptUris.clear();
        updateReceiptUI();
    }

    // 如果是 app 专属目录文件，把它删掉（相册里不会有）
    private void cleanupIfTempFile(Uri uri) {
        if (uri == null) return;
        try {
            File picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (picturesDir != null) {
                File maybeFile = new File(picturesDir, new File(uri.getPath()).getName());
                if (maybeFile.exists() && maybeFile.getAbsolutePath().startsWith(picturesDir.getAbsolutePath())) {
                    //noinspection ResultOfMethodCallIgnored
                    maybeFile.delete();
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<String> saved = new ArrayList<>();
        for (Uri u : receiptUris) saved.add(u.toString());
        outState.putStringArrayList("receipt_uris", saved);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        ArrayList<String> saved = savedInstanceState.getStringArrayList("receipt_uris");
        if (saved != null) {
            receiptUris.clear();
            for (String s : saved) receiptUris.add(Uri.parse(s));
            updateReceiptUI();
        }
    }

    // ======= Adapter for receipts =======
    private static class ReceiptVH extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        ImageButton btnDelete;
        ReceiptVH(View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.iv_thumb);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }

    private interface OnReceiptRemove { void onRemove(Uri uri); }

    private class ReceiptsAdapter extends RecyclerView.Adapter<ReceiptVH> {
        private final List<Uri> data;
        private final OnReceiptRemove onRemove;

        ReceiptsAdapter(List<Uri> data, OnReceiptRemove onRemove) {
            this.data = data;
            this.onRemove = onRemove;
        }

        @Override public ReceiptVH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_receipt_thumbnail, parent, false);
            return new ReceiptVH(v);
        }

        @Override public void onBindViewHolder(ReceiptVH holder, int position) {
            Uri uri = data.get(position);
            holder.ivThumb.setImageURI(uri);
            holder.btnDelete.setOnClickListener(v -> onRemove.onRemove(uri));
        }

        @Override public int getItemCount() { return data.size(); }
    }

    public List<Uri> getReceiptUris() {
        return new ArrayList<>(receiptUris);
    }

    // ======= OCR 流程 =======

    private void startOcrUi() {
        if (ocrOverlay != null) ocrOverlay.setVisibility(View.VISIBLE);
        if (tvOcrStatus != null) tvOcrStatus.setText(getString(R.string.ocr_recognizing));
        if (layoutOcrResult != null) layoutOcrResult.setVisibility(View.GONE);
    }

    private void finishOcrUiSuccess(ReceiptOcrParser.OcrResult r) {
        if (ocrOverlay != null) ocrOverlay.setVisibility(View.GONE);
        if (layoutOcrResult != null) {
            layoutOcrResult.setVisibility(View.VISIBLE);
            layoutOcrResult.setOnClickListener(v -> layoutOcrResult.setVisibility(View.GONE));
        }
        if (tvOcrResult != null) tvOcrResult.setText(r.toString());

        if (r.merchant != null && !r.merchant.isEmpty()) etMerchant.setText(r.merchant);
        if (r.totalAmount != null && !r.totalAmount.isEmpty()) {
            etAmount.setText(r.totalAmount);
            try { totalAmount = Double.parseDouble(r.totalAmount); } catch (Exception ignored) {}
            updateSplitAmounts();
        }
        if (r.date != null && !r.date.isEmpty()) tvDate.setText(r.date);

        Toast.makeText(this, getString(R.string.ocr_success), Toast.LENGTH_SHORT).show();

        new Handler(getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !isDestroyed() && layoutOcrResult != null)
                layoutOcrResult.setVisibility(View.GONE);
        }, 2500);
    }

    private void finishOcrUiFailure(Exception e) {
        if (ocrOverlay != null) ocrOverlay.setVisibility(View.GONE);
        if (layoutOcrResult != null) layoutOcrResult.setVisibility(View.GONE);
        Toast.makeText(this, getString(R.string.ocr_failed), Toast.LENGTH_SHORT).show();
        if (e != null) e.printStackTrace();
    }

    private void runOcrOnUri(Uri uri) {
        if (uri == null) {
            Toast.makeText(this, "Image Uri is null", Toast.LENGTH_SHORT).show();
            return;
        }
        startOcrUi();

        try {
            InputImage image = InputImage.fromFilePath(this, uri);

            latinRecognizer.process(image)
                    .addOnSuccessListener(text -> {
                        String full = (text != null) ? text.getText() : "";
                        if (full == null || full.trim().isEmpty()) {
                            chineseRecognizer.process(image)
                                    .addOnSuccessListener(textCN -> {
                                        String fullCN = (textCN != null) ? textCN.getText() : "";
                                        if (fullCN == null || fullCN.trim().isEmpty()) {
                                            finishOcrUiFailure(null);
                                        } else {
                                            ReceiptOcrParser.OcrResult r = ReceiptOcrParser.parse(fullCN);
                                            finishOcrUiSuccess(r);
                                        }
                                    })
                                    .addOnFailureListener(this::finishOcrUiFailure);
                        } else {
                            ReceiptOcrParser.OcrResult r = ReceiptOcrParser.parse(full);
                            finishOcrUiSuccess(r);
                        }
                    })
                    .addOnFailureListener(this::finishOcrUiFailure);

        } catch (Exception e) {
            finishOcrUiFailure(e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (latinRecognizer != null) latinRecognizer.close(); } catch (Exception ignored) {}
        try { if (chineseRecognizer != null) chineseRecognizer.close(); } catch (Exception ignored) {}
    }

    // —— 地理编码回调接口 —— //
    private interface GeoCallback {
        void onSuccess(double lat, double lon);
        void onError(String msg);
    }
}
