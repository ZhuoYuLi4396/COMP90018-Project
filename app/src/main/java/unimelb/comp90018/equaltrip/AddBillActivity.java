package unimelb.comp90018.equaltrip;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import com.google.android.gms.location.Granularity;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
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

// ML Kit - Text Recognition
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

// Google Places & Location
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FetchPlaceResponse;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import android.annotation.SuppressLint;


public class AddBillActivity extends AppCompatActivity {
    // ====== 原有字段 ======
    private EditText etBillName, etMerchant, etAmount;
    private AutoCompleteTextView etLocation; // ← 改成 AutoCompleteTextView
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
    private String selectedCategory = null;

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

    // 经纬度（用于保存/回传；优先使用联想/定位得到的值）
    private double latitude = 0.0;
    private double longitude = 0.0;
    private Long tripStartDateMs = null;
    private Long tripEndDateMs = null;

    // === OCR Views ===
    private View ocrOverlay;
    private TextView tvOcrStatus;
    private LinearLayout layoutOcrResult;
    private TextView tvOcrResult;
    private Button btnRetryOcr;

    // === ML Kit Recognizers ===
    private TextRecognizer latinRecognizer;
    private TextRecognizer chineseRecognizer;

    // OCR 控制
    private boolean ocrAfterNextCapture = false;
    private boolean ocrAfterNextGalleryPick = false;

    // —— 返回给 HomeActivity 的常量 —— //
    public static final String EXTRA_MARKER_LAT   = "extra_marker_lat";
    public static final String EXTRA_MARKER_LNG   = "extra_marker_lng";
    public static final String EXTRA_MARKER_TITLE = "extra_marker_title";

    // —— Nominatim HTTP 客户端 —— //
    private final OkHttpClient http = new OkHttpClient();

    // —— 频率限制（Nominatim） —— //
    private static final long MIN_NOMINATIM_INTERVAL_MS = 800;
    private long lastNominatimCallMs = 0L;
    private Double pendingLat = null, pendingLon = null;
    private Runnable pendingAfter = null;

    // —— 分类：用户是否手动选择过 —— //
    private boolean categoryManuallyChosen = false;

    // ====== 新增：Places 自动补全 & 定位 ======
    private PlacesClient placesClient;
    private AutocompleteSessionToken sessionToken;
    private final List<String> suggestions = new ArrayList<>();
    private final List<String> suggestionPlaceIds = new ArrayList<>();
    private ArrayAdapter<String> addrAdapter;
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch = null;

    private FusedLocationProviderClient fusedClient;
    private final CancellationTokenSource cts = new CancellationTokenSource();
    private ImageButton btnUseGps; // 右侧 GPS 按钮

    // 位置权限（一次性多权限）
    private final ActivityResultLauncher<String[]> requestLocationPerms =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fine = Boolean.TRUE.equals(result.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false));
                boolean coarse = Boolean.TRUE.equals(result.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false));
                if (fine || coarse) {
                    checkLocationSettingsThenUse();
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    // 摄像头权限
    private final ActivityResultLauncher<String> requestCameraPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openCamera();
                else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            });

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

        // ====== 初始化 Places & 定位 ======
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.PLACES_API_KEY));
        }
        placesClient = Places.createClient(this);
        sessionToken = AutocompleteSessionToken.newInstance();

        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        // 地址下拉适配器
        addrAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, suggestions);
        etLocation.setAdapter(addrAdapter);
        etLocation.setThreshold(1);

        // 文本变化 -> 防抖 -> 自动补全
        etLocation.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                // 用户重新编辑，清空已有坐标，避免错位
                latitude = 0; longitude = 0;
                if (pendingSearch != null) uiHandler.removeCallbacks(pendingSearch);
                String q = s.toString().trim();
                if (q.isEmpty()) {
                    suggestions.clear();
                    suggestionPlaceIds.clear();
                    addrAdapter.notifyDataSetChanged();
                    return;
                }
                pendingSearch = () -> queryAutocomplete(q);
                uiHandler.postDelayed(pendingSearch, 250);
            }
        });

        // 键盘搜索也可触发一次
        etLocation.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                queryAutocomplete(etLocation.getText().toString().trim());
                return true;
            }
            return false;
        });

        // 选择候选 -> 拉详情（经纬度+标准地址）
        etLocation.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= suggestionPlaceIds.size()) return;
            fetchPlaceDetail(suggestionPlaceIds.get(position));
        });

        // GPS按钮
        btnUseGps.setOnClickListener(v -> {
            if (hasLocationPermission()) {
                checkLocationSettingsThenUse();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Enable location?")
                        .setMessage("To autofill nearby addresses, allow EqualTrip to access your location.")
                        .setPositiveButton("Allow", (d, w) -> requestLocationPerms.launch(new String[]{
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                        }))
                        .setNegativeButton("Not now", null)
                        .show();
            }
        });

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
        etLocation = findViewById(R.id.et_location); // AutoCompleteTextView
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

        // 新增：GPS按钮
        btnUseGps = findViewById(R.id.btn_use_gps);
    }

    private void setupSplitRecyclerView() {
        rvSplitAmounts.setLayoutManager(new LinearLayoutManager(this));
        splitAdapter = new SplitAmountAdapter(participantSplits, (participant, newAmount) -> {
            updateParticipantAmount(participant, newAmount);
            updateTotalDisplay();
        });
        rvSplitAmounts.setAdapter(splitAdapter);
    }

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
            categoryManuallyChosen = true;
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
     *   创建账单（若已有坐标则跳过前向地理编码）
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

        // ★ 若已通过联想/定位拿到经纬度，就不再调用 Nominatim 前向地理编码
        if (latitude != 0.0 || longitude != 0.0) {
            fetchCategoryFromNominatimDebounced(latitude, longitude, this::uploadBillWithGeoThenReturn);
            return;
        }

        // 否则按你原逻辑：Nominatim 正向地理编码
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

    /** 正向地理编码（Nominatim） */
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
        });
    }

    /** 去抖 + 冷却（Nominatim 反向） */
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

    /** 反向地理编码（Nominatim） */
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

    /** 自动分类（除非用户手动点过） */
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

    /** 上传 Firestore（含收据上传）后回传坐标并结束 */
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

                    if (ocrAfterNextCapture) {
                        ocrAfterNextCapture = false;
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
        try { fusedClient.removeLocationUpdates(new LocationCallback() {}); } catch (Exception ignored) {}
        cts.cancel();
    }

    // —— 地理编码回调接口 —— //
    private interface GeoCallback {
        void onSuccess(double lat, double lon);
        void onError(String msg);
    }

    // ====== 下面是新增：Places 自动补全 + 定位实现 ======

    /** 自动补全查询 */

    private void applyCountryIfSafe(FindAutocompletePredictionsRequest.Builder builder) {
        // 1) 已有定位：只用 locationBias，不要 setCountries（避免冲突/误限）
        if (latitude != 0 && longitude != 0) return;

        // 2) 无定位，用系统 Locale，而不是 SIM
        String region = Locale.getDefault().getCountry();
        if (region != null && !region.isEmpty()) {
            builder.setCountries(Collections.singletonList(region.toUpperCase(Locale.ROOT)));
        }
        // 3) 否则不设国家（全球）
    }
    private void queryAutocomplete(String query) {
        RectangularBounds bias = null;
        if (latitude != 0 && longitude != 0) {
            double d = 0.08;
            bias = RectangularBounds.newInstance(
                    new LatLng(latitude - d, longitude - d),
                    new LatLng(latitude + d, longitude + d)
            );
        }

        FindAutocompletePredictionsRequest.Builder builder =
                FindAutocompletePredictionsRequest.builder()
                        .setSessionToken(sessionToken)
                        .setQuery(query);

        if (bias != null) builder.setLocationBias(bias);

        // 🚫 不再直接 setCountries(getLikelyCountry())，改用更安全的方法
        applyCountryIfSafe(builder);

        placesClient.findAutocompletePredictions(builder.build())
                .addOnSuccessListener(resp -> {
                    suggestions.clear();
                    suggestionPlaceIds.clear();
                    for (AutocompletePrediction p : resp.getAutocompletePredictions()) {
                        suggestions.add(p.getFullText(null).toString());
                        suggestionPlaceIds.add(p.getPlaceId());
                    }
                    addrAdapter.notifyDataSetChanged();
                    if (!suggestions.isEmpty() && etLocation.hasFocus()) {
                        etLocation.post(etLocation::showDropDown);
                    }
                    // 额外：打点看有多少返回
                    Log.d("Places", "predictions=" + suggestions.size());
                })
                .addOnFailureListener(e -> {
                    Log.e("Places", "autocomplete failed", e);
                    Toast.makeText(this, "Place 自动补全失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /** 根据 placeId 拉取经纬度与标准地址 */
    private void fetchPlaceDetail(String placeId) {
        List<Place.Field> fields = Arrays.asList(
                Place.Field.ID, Place.Field.ADDRESS, Place.Field.LAT_LNG, Place.Field.NAME
        );

        FetchPlaceRequest req = FetchPlaceRequest.builder(placeId, fields)
                .setSessionToken(sessionToken).build();

        placesClient.fetchPlace(req)
                .addOnSuccessListener((FetchPlaceResponse res) -> {
                    Place place = res.getPlace();
                    LatLng latLng = place.getLatLng();
                    if (latLng != null) {
                        latitude = latLng.latitude;
                        longitude = latLng.longitude;
                    }
                    String addr = place.getAddress() != null ? place.getAddress() : place.getName();
                    if (addr == null) addr = "";
                    etLocation.setText(addr);
                    etLocation.setSelection(addr.length());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to fetch place detail", Toast.LENGTH_SHORT).show());
    }

    /** 获取当前定位并反向地理编码成地址 */
    private void checkLocationSettingsThenUse() {
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build();
        LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(req).setAlwaysShow(true).build();

        SettingsClient sc = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> t = sc.checkLocationSettings(settingsRequest);
        t.addOnSuccessListener(r -> useCurrentLocation());
        t.addOnFailureListener(e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ((ResolvableApiException) e).startResolutionForResult(this, 9911);
                } catch (Exception ignored) {
                    Toast.makeText(this, "Please enable location services", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Location settings unavailable", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void useCurrentLocation() {
        if (!hasLocationPermission()) {
            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 🧹 1️⃣ 强制清除缓存
            fusedClient.flushLocations();
            fusedClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        // 强制丢弃缓存的粗定位
                        if (location != null && location.hasAccuracy() && location.getAccuracy() > 50) {
                            Log.w("GPS", "Discarding cached coarse location...");
                        }
                    });

            // 🧩 2️⃣ 手动清空 LocationManager 缓存（这是触发 cold start 的关键）
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            try {
                lm.removeUpdates(dummyListener); // 移除旧监听器（防止缓存复用）
            } catch (Exception ignored) {}

            // 🕐 3️⃣ 延迟执行，让系统有时间“丢弃缓存”
            new Handler(Looper.getMainLooper()).postDelayed(() -> {

                // 🔧 4️⃣ 构建请求：不允许任何缓存，强制唤醒卫星芯片
                LocationRequest req = new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        2000 // 每 2 秒更新
                )
                        .setGranularity(Granularity.GRANULARITY_FINE)
                        .setWaitForAccurateLocation(true)
                        .setMaxUpdateAgeMillis(0)          // ❗不允许缓存
                        .setMinUpdateIntervalMillis(500)
                        .setMaxUpdates(5)
                        .build();

                // 🚀 5️⃣ 启动监听
                fusedClient.requestLocationUpdates(req, new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult result) {
                        if (result == null) return;
                        Location loc = result.getLastLocation();
                        if (loc == null) return;

                        float accuracy = loc.hasAccuracy() ? loc.getAccuracy() : Float.MAX_VALUE;
                        String provider = loc.getProvider();
                        double lat = loc.getLatitude();
                        double lon = loc.getLongitude();

                        Log.d("GPS", "Provider=" + provider + " acc=" + accuracy);

                        // 📏 若仍是粗糙（network provider），继续强制 GPS 取一次
                        if ((provider == null || provider.equals("network") || accuracy > 50)) {
                            Log.w("GPS", "Fallback to raw GPS provider...");
                            requestRawGpsLocation(); // 👈 这里调用原生 GPS 获取
                            fusedClient.removeLocationUpdates(this);
                            return;
                        }

                        // ✅ 足够精确，停止监听
                        fusedClient.removeLocationUpdates(this);

                        latitude = lat;
                        longitude = lon;
                        reverseGeocodeAndFill(lat, lon);
                        fetchCategoryFromNominatim(lat, lon);

                        Toast.makeText(AddBillActivity.this,
                                String.format(Locale.getDefault(),
                                        "Got location (±%.0fm): %.6f, %.6f",
                                        accuracy, lat, lon),
                                Toast.LENGTH_SHORT).show();
                    }
                }, Looper.getMainLooper());

            }, 1200); // 延迟 1.2 秒确保缓存清除

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Location error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 👇 当 Fused Provider 退化为 network 定位时，强制再走一次原生 GPS
    @SuppressLint("MissingPermission")
    private void requestRawGpsLocation() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;

        try {
            lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location loc) {
                    latitude = loc.getLatitude();
                    longitude = loc.getLongitude();

                    float acc = loc.getAccuracy();
                    Log.d("GPS", "Raw GPS acc=" + acc);

                    reverseGeocodeAndFill(latitude, longitude);
                    fetchCategoryFromNominatim(latitude, longitude);

                    Toast.makeText(AddBillActivity.this,
                            String.format(Locale.getDefault(),
                                    "Got precise GPS (±%.0fm): %.6f, %.6f",
                                    acc, latitude, longitude),
                            Toast.LENGTH_SHORT).show();
                }
            }, Looper.getMainLooper());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 用于清除旧监听器的空实现
    private final LocationListener dummyListener = new LocationListener() {
        @Override public void onLocationChanged(@NonNull Location location) {}
    };




    private void reverseGeocodeAndFill(double lat, double lon) {
        // 优先用系统 Geocoder
        if (Geocoder.isPresent()) {
            try {
                Geocoder geo = new Geocoder(this, Locale.getDefault());
                List<Address> list = geo.getFromLocation(lat, lon, 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    String line = a.getAddressLine(0);
                    if (line != null && !line.trim().isEmpty() && !line.equalsIgnoreCase("Melbourne, Victoria, Australia")) {
                        etLocation.setText(line);
                        etLocation.setSelection(line.length());
                        return; // ✅ 成功，直接返回
                    }
                }
            } catch (IOException ignored) {}
        }

        // 如果系统 Geocoder 结果太粗，用 Nominatim
        String url = "https://nominatim.openstreetmap.org/reverse?format=json"
                + "&lat=" + lat + "&lon=" + lon
                + "&zoom=18&addressdetails=1";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "EqualTrip/1.0")
                .build();

        http.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    String fallback = lat + ", " + lon;
                    etLocation.setText(fallback);
                    etLocation.setSelection(fallback.length());
                });
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        runOnUiThread(() -> {
                            String fallback = lat + ", " + lon;
                            etLocation.setText(fallback);
                            etLocation.setSelection(fallback.length());
                        });
                        return;
                    }

                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONObject addr = json.optJSONObject("address");

                    StringBuilder sb = new StringBuilder();
                    if (addr != null) {
                        // 优先拼接门牌号、街道、郊区、城市等
                        if (addr.has("house_number")) sb.append(addr.optString("house_number")).append(" ");
                        if (addr.has("road")) sb.append(addr.optString("road")).append(", ");
                        if (addr.has("suburb")) sb.append(addr.optString("suburb")).append(", ");
                        if (addr.has("city")) sb.append(addr.optString("city")).append(", ");
                        if (addr.has("state")) sb.append(addr.optString("state")).append(", ");
                        if (addr.has("postcode")) sb.append(addr.optString("postcode")).append(", ");
                        if (addr.has("country")) sb.append(addr.optString("country"));
                    }

                    final String fullAddr = sb.length() > 0 ? sb.toString().trim() : (lat + ", " + lon);
                    runOnUiThread(() -> {
                        etLocation.setText(fullAddr);
                        etLocation.setSelection(fullAddr.length());
                    });

                } catch (Exception ex) {
                    runOnUiThread(() -> {
                        String fallback = lat + ", " + lon;
                        etLocation.setText(fallback);
                        etLocation.setSelection(fallback.length());
                    });
                } finally {
                    response.close();
                }
            }
        });
    }

    private String compactAddress(Address a) {
        List<String> parts = new ArrayList<>();
        if (a.getSubThoroughfare() != null) parts.add(a.getSubThoroughfare());
        if (a.getThoroughfare() != null) parts.add(a.getThoroughfare());
        if (a.getLocality() != null) parts.add(a.getLocality());
        if (a.getAdminArea() != null) parts.add(a.getAdminArea());
        if (a.getPostalCode() != null) parts.add(a.getPostalCode());
        if (a.getCountryName() != null) parts.add(a.getCountryName());
        return String.join(", ", parts);
    }

    /** 根据 SIM/Locale 推断国家，避免硬编码 */
    private List<String> getLikelyCountry() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            String simIso = tm != null ? tm.getSimCountryIso() : null;
            if (simIso != null && !simIso.isEmpty()) {
                return Collections.singletonList(simIso.toUpperCase(Locale.ROOT));
            }
        } catch (Exception ignored) {}
        String region = Locale.getDefault().getCountry();
        if (region != null && !region.isEmpty()) {
            return Collections.singletonList(region.toUpperCase(Locale.ROOT));
        }
        return Collections.emptyList();
    }

    // 封装后的nominatim
    private void fetchCategoryFromNominatim(double lat, double lon) {
        String url = "https://nominatim.openstreetmap.org/reverse?format=json&lat="
                + lat + "&lon=" + lon + "&zoom=18&addressdetails=1";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "EqualTrip/1.0") // Nominatim 必须有 UA
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(AddBillActivity.this, "API 请求失败", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) return;

                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);

                    String cls = json.optString("class", "");
                    String typ = json.optString("type", "");

                    String category;
                    if (!cls.isEmpty() && !typ.isEmpty()) {
                        category = cls + " · " + typ;
                    } else if (!cls.isEmpty()) {
                        category = cls;
                    } else if (!typ.isEmpty()) {
                        category = typ;
                    } else {
                        category = "other"; // 默认值
                    }

                    // 在 UI 线程更新分类（比如自动选按钮）
                    runOnUiThread(() -> {
                        autoSelectCategory_nominatim(category);
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
    private void autoSelectCategory_nominatim(String category) {
        category = category.toLowerCase();

        if (category.contains("restaurant") || category.contains("food") || category.contains("cafe")) {
            selectedCategory = "dining";
            // setCategorySelected(btnDining, true);
        } else if (category.contains("shop") || category.contains("mall")) {
            selectedCategory = "shopping";
            setCategorySelected(btnShopping, true);
        } else if (category.contains("bus") || category.contains("railway") || category.contains("transport")) {
            selectedCategory = "transport";
            setCategorySelected(btnTransport, true);
        } else {
            selectedCategory = "other";
            setCategorySelected(btnOther, true);
        }
    }
}
