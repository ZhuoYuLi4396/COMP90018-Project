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
import android.provider.MediaStore;
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

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

// === Receipts (multi-images) ===
import android.net.Uri;
import android.os.Environment;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.*;

// Firebase
import android.content.pm.PackageManager;
import com.google.android.gms.location.Granularity;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.material.appbar.MaterialToolbar;
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

// JSON 解析
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

// ML Kit - Text Recognition
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import android.util.Base64;
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
    private ImageButton btnReceiptCamera, btnReceiptGallery, btnRemoveReceipt;
    private RadioButton rbEqual, rbCustomize;
    private RadioGroup rgSplitMethod;

    // Split amount views
    private LinearLayout layoutSplitDetails;
    private RecyclerView rvSplitAmounts;
    private SplitAmountAdapter splitAdapter;

    // Category buttons
    private LinearLayout btnDining, btnTransport, btnShopping, btnOther;
    private String selectedCategory = null; // Default category

    // Receipt views
    private LinearLayout receiptPlaceholder;
    private RelativeLayout receiptPreview;
    private ImageView ivReceiptPreview;
    private FrameLayout layoutReceipt;

    private Calendar calendar = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private String selectedPayerUid = null;      // 用于存储到数据库
    private String selectedPayerUserId = null;   // 用于 UI 显示
    private ArrayList<String> selectedParticipantUids = new ArrayList<>();      // 存数据库用
    private ArrayList<String> selectedParticipantUserIds = new ArrayList<>();   // UI展示用
    private ArrayList<ParticipantSplit> participantSplits = new ArrayList<>();
    private boolean hasReceipt = false;
    private Bitmap receiptBitmap = null;
    private double totalAmount = 0.0;

    // 用于传输给后面Payer selection和Participants selection的全局变量tripId
    private String tripId;

    // === Receipts (multi-images) ===
    private RecyclerView rvReceipts;
    private ReceiptsAdapter receiptsAdapter;

    // 改成多图：用列表保存所有照片的 Uri
    private final List<Uri> receiptUris = new ArrayList<>();

    // 拍照用的临时输出 Uri（启动相机前生成）
    private Uri pendingCameraOutputUri = null;

    // 经纬度，由 GPS 组件写入然后给Nominatim API用
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

    // Java is the best language.py
    // This createBill() keep call the void to execute search category and auto-fill
    // write boolean to avoid search again.
    private boolean categoryLocked = false;
    private boolean isAmountValid = true;

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

    // —— 位置选择校验 —— //
    // === 位置选择校验与来源标记 ===
    private boolean locationPickedFromSuggestion = false;  // 只有点了候选或GPS反填才会置 true
    private boolean usingOsmAutocomplete = true;           // 直接只用 OSM
    // 防止旧网络回调把新结果清空
    private int osmGen = 0;

    // 距离排序的基准（优先用 HomeActivity 传来的）
    private double lastKnownLat = Double.NaN;
    private double lastKnownLon = Double.NaN;

    // 抑制 TextWatcher 的一次性开关：当我们程序化 setText() 时置 true
    private boolean suppressLocationWatcher = false;


    private boolean isActive = false;
    @Override protected void onStart() {
        super.onStart();
        isActive = true;
    }
    @Override protected void onStop() {
        super.onStop();
        isActive = false;
    }





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_bill);

        // === OCR Views ===
        ocrOverlay     = findViewById(R.id.ocr_overlay);
        tvOcrStatus    = findViewById(R.id.tv_ocr_status);
        layoutOcrResult= findViewById(R.id.layout_ocr_result);
        tvOcrResult    = findViewById(R.id.tv_ocr_result);
        btnRetryOcr    = findViewById(R.id.btn_retry_ocr);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle("Add Bill");
            toolbar.setNavigationOnClickListener(v ->
                    getOnBackPressedDispatcher().onBackPressed()
            );
        }


        // === ML Kit Recognizers ===
        latinRecognizer   = TextRecognition.getClient(new TextRecognizerOptions.Builder().build());
        chineseRecognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());


        // 在这里会拿到来自trip detail page中对应trip的tid
        // 逻辑是trip detail page点击某个卡片后一定会保存它的tid
        // 然后拿到这个tid用于避免孤儿bill的出现
        // 向全局变量tripId赋值
        tripId = getIntent().getStringExtra("tripId");

        if (tripId == null || tripId.isEmpty()) {
            Toast.makeText(this, "Missing tripId", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 测试完毕可以删除
        // 这里是测试tid确实被拿到后的测试输出
        Toast.makeText(this, "Received tripId: " + tripId, Toast.LENGTH_LONG).show();

        // ① 从 HomeActivity 的 Intent 读入个人坐标（用于候选距离排序）
        if (getIntent() != null) {
            if (getIntent().hasExtra("base_lat") && getIntent().hasExtra("base_lon")) {
                lastKnownLat = getIntent().getDoubleExtra("base_lat", Double.NaN);
                lastKnownLon = getIntent().getDoubleExtra("base_lon", Double.NaN);
            }
        }
// ② 兜底：从 SharedPreferences 取（HomeActivity 已写过）
        if (Double.isNaN(lastKnownLat) || Double.isNaN(lastKnownLon)) {
            String plat = getSharedPreferences("baseline_loc", MODE_PRIVATE).getString("lat", null);
            String plon = getSharedPreferences("baseline_loc", MODE_PRIVATE).getString("lon", null);
            if (plat != null && plon != null) {
                try {
                    lastKnownLat = Double.parseDouble(plat);
                    lastKnownLon = Double.parseDouble(plon);
                } catch (Exception ignored) {}
            }
        }

        initializeViews();
        setupListeners();
        setupSpinners();
        setupCategoryButtons();
        setupSplitRecyclerView();


        // Set default date
        tvDate.setText(dateFormat.format(calendar.getTime()));
        loadTripDateRange();

        // ====== 初始化 Places & 定位 ======
//        if (!Places.isInitialized()) {
//            Places.initialize(getApplicationContext(), getString(R.string.PLACES_API_KEY));
//        }
//        placesClient = Places.createClient(this);
//        sessionToken = AutocompleteSessionToken.newInstance();

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
                if (suppressLocationWatcher) {
                    // 程序化回填文本，不要把“已从下拉选择”的标志清掉
                    return;
                }

                locationPickedFromSuggestion = false;  // ← 仅在真正手动输入时清掉
                if (pendingSearch != null) uiHandler.removeCallbacks(pendingSearch);

                String q = s == null ? "" : s.toString().trim();
                if (q.isEmpty()) {
                    suggestions.clear();
                    suggestionPlaceIds.clear();
                    if (addrAdapter != null) addrAdapter.notifyDataSetChanged();
                    etLocation.dismissDropDown();
                    return;
                }
                pendingSearch = () -> queryAutocompleteOSM(q);
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

            // 1) 告诉 TextWatcher：接下来是程序化回填，不要把标记清掉
            suppressLocationWatcher = true;
            locationPickedFromSuggestion = true;

            // 2) 文本回填（部分机型不会自动回填，手动设置更保险）
            if (position < suggestions.size()) {
                String chosen = suggestions.get(position);
                etLocation.setText(chosen);
                etLocation.setSelection(chosen.length());
            }

            // 3) 解析坐标（我们把 placeId 用 "lat,lon" 存的）
            String latlon = suggestionPlaceIds.get(position);
            String[] parts = latlon.split(",");
            if (parts.length == 2) {
                try {
                    latitude  = Double.parseDouble(parts[0]);
                    longitude = Double.parseDouble(parts[1]);

                    // 更新“距离排序基准”，后续输入仍然就近
                    lastKnownLat = latitude;
                    lastKnownLon = longitude;

                    // 4) 先确保“未锁”，让自动分类能生效
                    categoryLocked = false;

                    // 5) 拉 Nominatim 反查并自动分类；完成后再把分类锁住，避免后续被覆盖
                    fetchCategoryFromNominatimDebounced(latitude, longitude, () -> {
                        // 回到主线程后再锁
                        runOnUiThread(() -> categoryLocked = true);
                    });
                } catch (NumberFormatException ignored) {}
            }

            // 6) 让下拉收起更自然，并在下一帧恢复 watcher
            etLocation.dismissDropDown();
            etLocation.post(() -> suppressLocationWatcher = false);
        });









        // GPS按钮
        btnUseGps.setOnClickListener(v -> {
            categoryLocked = false;
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
                        // 获取 startDate 和 endDate（存储为 Long 毫秒）
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

                        // 如果获取到了日期范围，将默认日期设置为 trip 开始日期
                        if (tripStartDateMs != null) {
                            calendar.setTimeInMillis(tripStartDateMs);
                            tvDate.setText(dateFormat.format(calendar.getTime()));
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load trip dates: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
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
        //btnRemoveReceipt = findViewById(R.id.btn_remove_receipt);

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

        // Receipt views（你原来就有）
        receiptPlaceholder = findViewById(R.id.receipt_placeholder);
        receiptPreview = findViewById(R.id.receipt_preview);
        layoutReceipt = findViewById(R.id.layout_receipt);

        // 新增：绑定 RecyclerView（XML 补丁里会新增这个 id）
        rvReceipts = findViewById(R.id.rv_receipts);
        //        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        //        rvReceipts.setLayoutManager(lm);
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvReceipts.setLayoutManager(lm);
        receiptsAdapter = new ReceiptsAdapter(receiptUris, uri -> removeOneReceipt(uri));
        rvReceipts.setAdapter(receiptsAdapter);

        // 新增：GPS按钮
        btnUseGps = findViewById(R.id.btn_use_gps);
    }

    private void setupSplitRecyclerView() {
        rvSplitAmounts.setLayoutManager(new LinearLayoutManager(this));
        splitAdapter = new SplitAmountAdapter(participantSplits, new SplitAmountAdapter.OnAmountChangeListener() {
            @Override
            public void onAmountChanged(String participant, double newAmount) {
                updateParticipantAmount(participant, newAmount);
                updateTotalDisplay();
            }
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

        // Receipt handling
//        layoutReceipt.setOnClickListener(v -> {
//            if (!hasReceipt) {
//                showReceiptOptions();
//            }
//        });
        layoutReceipt.setOnClickListener(v -> showReceiptOptions());
//        btnReceiptCamera.setOnClickListener(v -> tryOpenCamera());
//        btnReceiptGallery.setOnClickListener(v -> openGallery());
        // ② 顶部按钮别再直达相机/相册，而是同样弹出选择
// 原来：btnReceiptCamera.setOnClickListener(v -> tryOpenCamera());
        btnReceiptCamera.setOnClickListener(v -> showReceiptOptions());

// 原来：btnReceiptGallery.setOnClickListener(v -> openGallery());
        btnReceiptGallery.setOnClickListener(v -> showReceiptOptions());


        // Amount input validation and split calculation
        etAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String input = s.toString().trim();
                // 验证小数位数
                if (!validateAmountDecimalPlaces(input)) {
                    etAmount.setError("Maximum 2 decimal places allowed");
                    isAmountValid = false;
                    btnCreateBill.setEnabled(false);
                    btnCreateBill.setAlpha(0.5f); // 视觉上显示禁用状态
                    totalAmount = 0.0;
                    updateSplitAmounts();
                    return;
                }

                // 清除错误提示
                etAmount.setError(null);
                isAmountValid = true;
                btnCreateBill.setEnabled(true);
                btnCreateBill.setAlpha(1.0f);

                if (!s.toString().isEmpty()) {
                    try {
                        totalAmount = Double.parseDouble(s.toString());
                        updateSplitAmounts();
                    } catch (NumberFormatException e) {
                        totalAmount = 0.0;
                        etAmount.setError("Invalid amount format");
                        isAmountValid = false;
                        btnCreateBill.setEnabled(false);
                        btnCreateBill.setAlpha(0.5f);
                    }
                } else {
                    totalAmount = 0.0;
                    updateSplitAmounts();
                }
            }
        });

        // Paid by - navigate to payer selection
        findViewById(R.id.layout_paid_by).setOnClickListener(v -> {
            Intent intent = new Intent(AddBillActivity.this, PayerSelectionActivity.class);
            intent.putExtra("current_payer", selectedPayerUserId);
            // 向PayerSelection赋值tid
            intent.putExtra("tripId", tripId);
            startActivityForResult(intent, 1001);
        });

        // Participants - navigate to participants selection
        findViewById(R.id.layout_participants).setOnClickListener(v -> {
            Intent intent = new Intent(AddBillActivity.this, ParticipantsSelectionActivity.class);
            intent.putStringArrayListExtra("selected_participants", selectedParticipantUids);
            // 向PayerSelection赋值tid
            intent.putExtra("tripId", tripId);
            startActivityForResult(intent, 1002);
        });

        // Split method radio buttons
        rgSplitMethod.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isCustomize = checkedId == R.id.rb_customize;
            splitAdapter.setEditableMode(isCustomize);
            updateSplitAmounts();
        });

        // Create Bill button
        btnCreateBill.setOnClickListener(v -> createBill());

        btnRetryOcr.setOnClickListener(v -> {
            if (receiptUris.isEmpty()) {
                Toast.makeText(this, "No receipt image to recognize", Toast.LENGTH_SHORT).show();
                return;
            }
            // 这里选择“最后一张”或你想要的目标图
            Uri target = receiptUris.get(receiptUris.size() - 1);
            runOcrOnUri(target);
        });

    }

    // 高精度分账算法
    private Map<String, Double> calculateSplits(String payer, List<String> participants, double amount) {
        Map<String, Double> result = new HashMap<>();

        if (participants == null || participants.isEmpty()) return result;
        java.math.BigDecimal total = new java.math.BigDecimal(amount);
        java.math.BigDecimal headcount = new java.math.BigDecimal(participants.size());

        // 原始每人份额
        java.math.BigDecimal rawShare = total.divide(headcount, 6, java.math.RoundingMode.HALF_UP);

        // 暂存（四舍五入到分）
        List<java.math.BigDecimal> shares = new ArrayList<>();
        java.math.BigDecimal sumRounded = java.math.BigDecimal.ZERO;

        for (int i = 0; i < participants.size(); i++) {
            java.math.BigDecimal s = rawShare.setScale(2, java.math.RoundingMode.HALF_UP);
            shares.add(s);
            sumRounded = sumRounded.add(s);
        }

        // 校正误差（确保总额等于原始金额）
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

        // 转换为 Map<String, Double>
        for (int i = 0; i < participants.size(); i++) {
            result.put(participants.get(i), shares.get(i).doubleValue());
        }

        return result;
    }
    /*

     */
    private void updateSplitAmounts() {
        if (selectedParticipantUids.isEmpty()) {
            layoutSplitDetails.setVisibility(View.GONE);
            return;
        }

        layoutSplitDetails.setVisibility(View.VISIBLE);
        participantSplits.clear();   // 必须清空，不然列表不会刷新

        if (rbEqual.isChecked()) {
            // ✅ Equal 模式：高精度分账 + payer 置顶
            Map<String, Double> splitMap = calculateSplits(
                    selectedPayerUserId,
                    selectedParticipantUserIds,
                    totalAmount
            );

            // 先放 payer
            if (splitMap.containsKey(selectedPayerUserId)) {
                participantSplits.add(new ParticipantSplit(
                        selectedPayerUserId,
                        splitMap.get(selectedPayerUserId)
                ));
            }

            // 再放其他人
            for (String userId : selectedParticipantUserIds) {
                if (userId.equals(selectedPayerUserId)) continue;
                participantSplits.add(new ParticipantSplit(userId, splitMap.get(userId)));
            }

        } else {
            // ✅ Customize 模式：初始化为 0，让用户自己填
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

    private boolean validateAmountDecimalPlaces(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return true; // 空值由其他验证处理
        }

        // 检查是否包含小数点
        if (amountStr.contains(".")) {
            String[] parts = amountStr.split("\\.");
            if (parts.length == 2) {
                // 检查小数部分位数
                String decimalPart = parts[1];
                if (decimalPart.length() > 2) {
                    return false; // 超过两位小数
                }
            }
        }
        return true;
    }

    private void updateTotalDisplay() {
        double splitTotal = 0;
        for (ParticipantSplit split : participantSplits) {
            splitTotal += split.getAmount();
        }

        String currency = spinnerCurrency.getSelectedItem() != null ?
                spinnerCurrency.getSelectedItem().toString().substring(0, 1) : "$";

        tvTotalSplit.setText(String.format(Locale.getDefault(), "Total: %s %.2f", currency, splitTotal));

        // Show warning if totals don't match in customize mode
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
            categoryLocked = true;
            categoryManuallyChosen = true;

            // Reset all category buttons
            resetCategoryButtons();

            // Set selected state
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

        // Set dining as default selected
        // setCategorySelected(btnDining, true);

        // ✅ 启动时强制清空所有按钮，避免 Dining 被默认点亮
        // Dining按钮一直在被默认选中？用reset把它关了
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
            // Set blue background for selected state
            button.setBackgroundResource(R.drawable.category_selected_bg);
            // Set white text and icon color
            TextView textView = (TextView) button.getChildAt(1);
            ImageView imageView = (ImageView) button.getChildAt(0);
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            imageView.setColorFilter(ContextCompat.getColor(this, android.R.color.white));
        } else {
            // Set gray background for unselected state
            button.setBackgroundResource(R.drawable.category_unselected_bg);
            // Set gray text and icon color
            TextView textView = (TextView) button.getChildAt(1);
            ImageView imageView = (ImageView) button.getChildAt(0);
            textView.setTextColor(ContextCompat.getColor(this, R.color.gray_text));
            imageView.setColorFilter(ContextCompat.getColor(this, R.color.gray_text));
        }
    }

    private void setupSpinners() {
        // Currency spinner
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
        spinnerCurrency.setSelection(0); // Default to AUD
    }

    private void showDatePicker() {
        // 检查是否已加载 trip 日期范围
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

                    // 验证选择的日期是否在 trip 范围内
                    long selectedMs = calendar.getTimeInMillis();
                    if (selectedMs < tripStartDateMs || selectedMs > tripEndDateMs) {
                        Toast.makeText(this,
                                "Please select a date within the trip period",
                                Toast.LENGTH_SHORT).show();
                        // 重置为 trip 开始日期
                        calendar.setTimeInMillis(tripStartDateMs);
                    }

                    tvDate.setText(dateFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        // 设置日期选择器的最小和最大日期
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

    private void validateAmount() {
        String amount = etAmount.getText().toString();
        try {
            double value = Double.parseDouble(amount);
            // Format to 2 decimal places
            etAmount.setText(String.format(Locale.getDefault(), "%.2f", value));
        } catch (NumberFormatException e) {
            // Invalid amount, clear field
            etAmount.setText("");
        }
    }

    //    private void showReceiptOptions() {
//        new AlertDialog.Builder(this)
//                .setTitle("Add Receipt")
//                .setItems(new String[]{"Take Photo", "Choose from Gallery"},
//                        (dialog, which) -> {
//                            if (which == 0) {
//                                openCamera();
//                            } else {
//                                openGallery();
//                            }
//                        })
//                .setNegativeButton("Cancel", null)
//                .show();
//    }
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
                        case 3: // 相册并识图（单选即可）
                            ocrAfterNextGalleryPick = true;
                            openGalleryForOcrSingle();
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }



    //    private void openCamera() {
//        // In a real app, you would open camera intent here
//        Toast.makeText(this, "Opening camera...", Toast.LENGTH_SHORT).show();
//        showReceiptPreview();
//    }
    void openCamera() {
        try {
            pendingCameraOutputUri = createImageOutputUri(); // 生成 content:// Uri
            if (pendingCameraOutputUri != null) {
                takePictureLauncher.launch(pendingCameraOutputUri);
            } else {
                Toast.makeText(this, "Failed to create photo file", Toast.LENGTH_SHORT).show();
            }
        }  catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this,
                    "Open camera failed: " + e.getClass().getSimpleName() + " - " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }

//        catch (Exception e) {
//            Toast.makeText(this, "Open camera failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//        }
    }

    // 在 app 专属外部图片目录创建文件，并用 FileProvider 转成 content://
    private Uri createImageOutputUri() throws IOException {
        File picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDir == null) return null;
        if (!picturesDir.exists()) picturesDir.mkdirs();

        String fileName = "receipt_" + System.currentTimeMillis() + ".jpg";
        File imageFile = new File(picturesDir, fileName);
        if (!imageFile.exists()) imageFile.createNewFile();

        // 与 Manifest 里的 authorities 一致：${applicationId}.fileprovider
        return FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                imageFile
        );
    }

    private void openGallery() {
        // In a real app, you would open gallery intent here
        // Toast.makeText(this, "Opening gallery...", Toast.LENGTH_SHORT).show();
        //  showReceiptPreview();
        pickImagesLauncher.launch(new String[]{"image/*"});
    }

    private void showReceiptPreview() {
        hasReceipt = true;
        receiptPlaceholder.setVisibility(View.GONE);
        receiptPreview.setVisibility(View.VISIBLE);

        // In a real app, you would set the actual image here
        // ivReceiptPreview.setImageBitmap(receiptBitmap);
    }

    private void removeReceipt() {
        hasReceipt = false;
        receiptBitmap = null;
        receiptPlaceholder.setVisibility(View.VISIBLE);
        receiptPreview.setVisibility(View.GONE);
    }

    private String getUidFromUserId(String userId) {
        int index = selectedParticipantUserIds.indexOf(userId);
        if (index != -1 && index < selectedParticipantUids.size()) {
            return selectedParticipantUids.get(index);
        }
        return null; // 如果没找到，返回 null（理论上不会出现）
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

    private void createBill() {

        // Validate inputs
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

        if (!isAmountValid || !validateAmountDecimalPlaces(etAmount.getText().toString().trim())) {
            etAmount.setError("Maximum 2 decimal places allowed");
            Toast.makeText(this, "Amount can only have up to 2 decimal places", Toast.LENGTH_SHORT).show();
            return;
        }

        // 禁止输入负数，为0的amount
        double value = Double.parseDouble(etAmount.getText().toString().trim());
        if (value <= 0) {
            Toast.makeText(this, "Amount must be greater than 0", Toast.LENGTH_SHORT).show();
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

        // 防止只有 A 自己参与（不允许 payer 和唯一的 participant 是同一个人）
        if (selectedParticipantUids.size() == 1 && selectedParticipantUids.get(0).equals(selectedPayerUid)) {
            new AlertDialog.Builder(this)
                    .setTitle("Invalid Split")
                    .setMessage("Payer cannot be the only participant.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        // 禁止输入负数，为0的amount
//        double value = Double.parseDouble(etAmount.getText().toString().trim());
        if (value <= 0) {
            Toast.makeText(this, "Amount must be greater than 0", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if customize split totals match
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
        if (!locationPickedFromSuggestion) {
            etLocation.setError("请从下拉列表选择一个关联地址");
            new AlertDialog.Builder(this)
                    .setTitle("请选择关联地址")
                    .setMessage("为了确保位置与分类准确，请从下拉建议中选择一个地址，而不是只输入文本。")
                    .setPositiveButton("知道了", (d, w) -> {
                        etLocation.requestFocus();
                        etLocation.showDropDown();
                    })
                    .show();
            return;
        }

        // 规则：如果看起来像完整地址（含逗号/街道号），但用户没有从候选选择，就强制提醒
        String addrTxt = etLocation.getText().toString().trim();
        boolean looksLikeFullAddress =
                addrTxt.matches(".*\\d+.*\\s+.*") // 有门牌 + 空格（粗判）
                        || addrTxt.contains(",");           // 或包含逗号（像“街道, 城市”）
        if (!locationPickedFromSuggestion && looksLikeFullAddress) {
            new AlertDialog.Builder(this)
                    .setTitle("Pick from suggestions")
                    .setMessage("Please pick a matched address from the dropdown so we can geo-locate and categorize it correctly.")
                    .setPositiveButton("OK", (d,w) -> {
                        etLocation.requestFocus();
                        etLocation.post(etLocation::showDropDown);
                    })
                    .show();
            return; // 阻止创建
        }


        final String address = etLocation.getText().toString().trim();

        // ★ 若已通过联想/定位拿到经纬度，就不再调用 Nominatim 前向地理编码
        /*
        if (latitude != 0.0 || longitude != 0.0) {
            fetchCategoryFromNominatimDebounced(latitude, longitude, this::uploadBillWithGeoThenReturn);
            return;
        }
        */
        if (latitude != 0.0 || longitude != 0.0) {
            // ✅ 直接跳过第二次分类查询
            uploadBillWithGeoThenReturn();
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

    /** OSM 自动补全（附近 + 关键词），把经纬度作为“placeId”返回 */
    /** OSM 自动补全（附近 + 关键词）：带三档范围、429重试、详尽日志、强制弹出下拉 */
    /** OSM 自动补全（附近 + 关键词）：
     *  - 纯数字：自动附加本地上下文（suburb→city→state），若仍无则不加bounded兜底
     *  - 带国家码 countrycodes
     *  - 三档范围 + 429 限流重试 + 关键日志 + 强制下拉
     */
    /** OSM 自动补全（附近 + 关键词）：
     *  - 纯数字输入会自动附加本地上下文（suburb→city→state→Melbourne）
     *  - 带 countrycodes=AU
     *  - 三档范围 + 429 限流重试 + 强制下拉
     */
    /** OSM 自动补全（附近 + 关键词）：三档范围、429重试、竞态防护、强制弹出 */
    /** OSM 自动补全（附近 + 关键词）：三档范围、429重试、竞态防护、窗口附着检查、强制弹出 */
    /** OSM 自动补全（附近 + 关键词）：分步 bounded→unbounded、429 重试、去重与就近排序、强制显示下拉 */
    private void queryAutocompleteOSM(String query) {
        // 本次查询的代次（用于丢弃旧回调）
        final int myGen = ++osmGen;

        // 基准坐标：优先当前 Activity 已获坐标，其次 HomeActivity 传入的 lastKnown，再次墨尔本
        final double baseLat = (latitude != 0) ? latitude
                : (!Double.isNaN(lastKnownLat) ? lastKnownLat : -37.8136);
        final double baseLon = (longitude != 0) ? longitude
                : (!Double.isNaN(lastKnownLon) ? lastKnownLon : 144.9631);

        android.util.Log.d("OSM-BASE", "baseLat=" + baseLat + ", baseLon=" + baseLon
                + ", latitude=" + latitude + ", longitude=" + longitude
                + ", lastKnownLat=" + lastKnownLat + ", lastKnownLon=" + lastKnownLon);

        // 三档搜索半径（度）：约 8km / 20km / 50km
        final double[] DELTAS = new double[]{0.08, 0.18, 0.45};

        try {
            final String q = java.net.URLEncoder.encode(query, "UTF-8");

            // 逐步扩大范围；最后一次去掉 bounded
            java.util.function.BiConsumer<Boolean, Integer> searchStep =
                    new java.util.function.BiConsumer<Boolean, Integer>() {
                        @Override public void accept(Boolean noBounded, Integer step) {
                            // 查询已被新一轮覆盖，直接丢弃
                            if (myGen != osmGen) return;

                            int s = Math.max(0, Math.min(step, DELTAS.length - 1));
                            double delta = DELTAS[s];
                            double minLat = baseLat - delta, maxLat = baseLat + delta;
                            double minLon = baseLon - delta, maxLon = baseLon + delta;

                            String url = "https://nominatim.openstreetmap.org/search?"
                                    + "format=json&addressdetails=1&limit=10"
                                    + "&q=" + q;

                            if (!noBounded) {
                                url += "&bounded=1"
                                        + "&viewbox=" + minLon + "," + maxLat + "," + maxLon + "," + minLat;
                            }

                            android.util.Log.d("OSM-REQ", (noBounded ? "[noBounded]" : "[bounded step="+s+"]")
                                    + " URL=" + url);

                            Request request = new Request.Builder()
                                    .url(url)
                                    .header("User-Agent", "EqualTrip/1.0 (contact: dev@example.com)")
                                    .build();

                            http.newCall(request).enqueue(new Callback() {
                                @Override public void onFailure(Call call, IOException e) {
                                    runOnUiThread(() -> {
                                        if (myGen != osmGen) return;
                                        android.util.Log.d("OSM-RESP", "FAIL: " + e.getMessage());
                                    });
                                }

                                @Override public void onResponse(Call call, Response response) throws IOException {
                                    try {
                                        int code = response.code();
                                        String ra = response.header("Retry-After");
                                        String body = (response.body() != null) ? response.body().string() : "[]";
                                        android.util.Log.d("OSM-RESP", "HTTP " + code + " Retry-After=" + ra);

                                        // 429 限流：尊重 Retry-After
                                        if (code == 429) {
                                            long delay = 1500L;
                                            if (ra != null) {
                                                try { delay = Math.max(1000L, Long.parseLong(ra.trim()) * 1000L); } catch (Exception ignored) {}
                                            }
                                            long finalDelay = delay;
                                            new Handler(getMainLooper()).postDelayed(() -> {
                                                if (myGen != osmGen) return;
                                                accept(noBounded, s);  // 同参数重试
                                            }, finalDelay);
                                            return;
                                        }

                                        if (!response.isSuccessful()) {
                                            // 非 2xx：不动 UI（避免清空新结果）
                                            return;
                                        }

                                        org.json.JSONArray arr = new org.json.JSONArray(body);
                                        android.util.Log.d("OSM-RESP", "Results=" + arr.length()
                                                + " (noBounded=" + noBounded + ", step=" + s + ")");

                                        // 0 结果：扩大范围；若已经最大且仍 bounded，则改为不加 bounded；若已不加 bounded 仍 0，才清空
                                        if (arr.length() == 0) {
                                            if (myGen != osmGen) return;

                                            if (!noBounded && s + 1 < DELTAS.length) {
                                                accept(false, s + 1);           // 扩大范围继续找
                                            } else if (!noBounded) {
                                                accept(true, s);                // 去掉 bounded 再试一次
                                            } else {
                                                // 最终仍为 0：清空 UI
                                                runOnUiThread(() -> {
                                                    if (myGen != osmGen) return;
                                                    android.util.Log.d("OSM-UI", "final empty, clear dropdown");
                                                    suggestions.clear();
                                                    suggestionPlaceIds.clear();
                                                    if (addrAdapter != null) addrAdapter.notifyDataSetChanged();
                                                    etLocation.dismissDropDown();
                                                });
                                            }
                                            return;
                                        }

                                        // —— 解析 → 去重 → 距离过滤与排序 —— //
                                        class Item {
                                            String display;
                                            double lat, lon;
                                            double distKm;
                                            boolean exactHouseMatch;
                                            boolean prefixHouseMatch;
                                        }
                                        java.util.List<Item> items = new java.util.ArrayList<>();
                                        java.util.Set<String> seen = new java.util.HashSet<>(); // 去重 key: lat,lon

                                        for (int i = 0; i < arr.length(); i++) {
                                            org.json.JSONObject o = arr.getJSONObject(i);
                                            String display = o.optString("display_name", "");
                                            String latStr = o.optString("lat", "");
                                            String lonStr = o.optString("lon", "");
                                            if (display.isEmpty() || latStr.isEmpty() || lonStr.isEmpty()) continue;

                                            double latV, lonV;
                                            try {
                                                latV = Double.parseDouble(latStr);
                                                lonV = Double.parseDouble(lonStr);
                                            } catch (Exception e) { continue; }

                                            String key = latStr + "," + lonStr;
                                            if (!seen.add(key)) continue; // 去重

                                            Item it = new Item();
                                            it.display = display;
                                            it.lat = latV;
                                            it.lon = lonV;

                                            // 用基准坐标算距离
                                            it.distKm = haversineKm(baseLat, baseLon, latV, lonV);

                                            // 数字门牌优先
                                            if (query.matches("\\d+")) {
                                                org.json.JSONObject addr = o.optJSONObject("address");
                                                String house = (addr != null) ? addr.optString("house_number", "") : "";
                                                it.exactHouseMatch  = house.equals(query);
                                                it.prefixHouseMatch = !it.exactHouseMatch && house.startsWith(query);
                                            }
                                            items.add(it);
                                        }

                                        // 距离上限（去噪）：noBounded 时 50km；bounded 时 30km
                                        double maxKm = noBounded ? 50.0 : 30.0;
                                        java.util.List<Item> filtered = new java.util.ArrayList<>();
                                        for (Item it : items) {
                                            if (Double.isNaN(it.distKm) || it.distKm <= maxKm) filtered.add(it);
                                        }

                                        // 排序：精确门牌 > 前缀门牌 > 距离近 > 文本
                                        java.util.Collections.sort(filtered, (a, b) -> {
                                            if (a.exactHouseMatch != b.exactHouseMatch) return a.exactHouseMatch ? -1 : 1;
                                            if (a.prefixHouseMatch != b.prefixHouseMatch) return a.prefixHouseMatch ? -1 : 1;
                                            int d = Double.compare(a.distKm, b.distKm);
                                            if (d != 0) return d;
                                            return a.display.compareToIgnoreCase(b.display);
                                        });

                                        // 输出到 UI 的数组（如不想显示距离，去掉 " · ~Xkm" 拼接）
                                        java.util.List<String> texts = new java.util.ArrayList<>();
                                        java.util.List<String> ids   = new java.util.ArrayList<>();
                                        for (Item it : filtered) {
                                            texts.add(it.display + "  ·  ~" + (int)Math.round(it.distKm) + "km");
                                            ids.add(it.lat + "," + it.lon);
                                        }

                                        runOnUiThread(() -> {
                                            if (myGen != osmGen) return;

                                            suggestions.clear();
                                            suggestionPlaceIds.clear();
                                            suggestions.addAll(texts);
                                            suggestionPlaceIds.addAll(ids);

                                            // 重新挂一次 adapter（兼容部分机型）
                                            addrAdapter = new ArrayAdapter<>(AddBillActivity.this,
                                                    android.R.layout.simple_dropdown_item_1line, suggestions);
                                            etLocation.setAdapter(addrAdapter);
                                            etLocation.setThreshold(0); // 立刻可弹

                                            boolean focused = etLocation.isFocused();
                                            android.util.Log.d("OSM-UI", "update size=" + suggestions.size() + ", focused=" + focused);

                                            if (!focused) etLocation.requestFocus();
                                            etLocation.dismissDropDown();
                                            etLocation.post(etLocation::showDropDown);
                                            etLocation.postDelayed(etLocation::showDropDown, 60);
                                            etLocation.postDelayed(() -> {
                                                etLocation.showDropDown();
                                                android.util.Log.d("OSM-UI", "popupShowing=" + etLocation.isPopupShowing());
                                            }, 140);
                                        });

                                    } catch (Exception parseEx) {
                                        android.util.Log.d("OSM-RESP", "parse error: " + parseEx.getMessage());
                                        // 解析异常：不动 UI（避免覆盖新结果）
                                    } finally {
                                        response.close();
                                    }
                                }
                            });
                        }
                    };

            // 从最小范围开始检索
            searchStep.accept(false, 0);

        } catch (Exception encEx) {
            android.util.Log.d("OSM-REQ", "encode error: " + encEx.getMessage());
            // 构造 URL 出错：不动 UI
        }
    }









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

    /** 计算两点球面距离（千米） */
    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2)*Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
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
        if (categoryLocked) return;

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
            categoryLocked = true;
            return;
        }

        // Shopping
        if (s.contains("class:shop") || s.contains("shop:") ||
                s.contains("mall") || s.contains("supermarket") || s.contains("retail")) {
            selectedCategory = "shopping";
            setCategorySelected(btnShopping, true);
            categoryLocked = true;
            return;
        }

        // Transport
        if (s.contains("public_transport") || s.contains("railway") ||
                s.contains("bus_station")      || s.contains("bus_stop") ||
                s.contains("aeroway")          || s.contains("highway")  ||
                s.contains("transport")) {
            selectedCategory = "transport";
            setCategorySelected(btnTransport, true);
            categoryLocked = true;
            return;
        }

        // Other
        selectedCategory = "other";
        setCategorySelected(btnOther, true);
        categoryLocked = true;
    }
    private void uploadBillWithGeoThenReturn() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String billId = db.collection("trips").document(tripId)
                .collection("bills").document().getId();

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

        billData.put("currency", spinnerCurrency.getSelectedItem().toString());
        billData.put("paidBy", selectedPayerUid);
        billData.put("participants", new ArrayList<>(selectedParticipantUids));
        billData.put("createdAt", new Timestamp(calendar.getTime()));
        billData.put("date", new Timestamp(calendar.getTime()));

        // ======= debts =======
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

        // ✅ Base64 逻辑核心：把图片转成 Base64 字符串
        if (receiptUris.isEmpty()) {
            // 没有收据，直接上传 Firestore
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
            // 有收据，压缩 + 编码
            List<String> base64List = new ArrayList<>();
            for (Uri uri : receiptUris) {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos); // 压缩质量
                    bitmap.recycle();
                    String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
                    base64List.add(base64Image);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 添加到 Firestore 文档
            billData.put("receiptsBase64", base64List);

            db.collection("trips").document(tripId)
                    .collection("bills").document(billId)
                    .set(billData)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Bill uploaded with Base64 images!", Toast.LENGTH_SHORT).show();
                        returnMarkerToHomeAndFinish();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Error uploading bill: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == 1001 && data != null) {
                // Payer selection result
                selectedPayerUid = data.getStringExtra("selected_payer_uid");
                selectedPayerUserId = data.getStringExtra("selected_payer_userid");
                tvPaidBy.setText(selectedPayerUserId);  // UI 只显示用户名
                updateSplitAmounts();
            } else if (requestCode == 1002 && data != null) {
                // Participants selection result
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

    // 拍照结果回调（成功则把 pendingCameraOutputUri 放进列表）
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

    // 删除“某一张”
    void removeOneReceipt(Uri uri) {
        // 先从列表移除
        if (receiptUris.remove(uri)) {
            // 如果是我们创建在 app 专属目录的临时文件，顺手删掉实际文件
            cleanupIfTempFile(uri);
            updateReceiptUI();
        }
    }

    // 删除“全部”
    void removeAllReceipts() {
        // 清理所有本地临时文件
        for (Uri uri : new ArrayList<>(receiptUris)) {
            cleanupIfTempFile(uri);
        }
        receiptUris.clear();
        updateReceiptUI();
    }

    // 多选相册图片（Storage Access Framework，支持永久读权限）
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
                // ★ 不再自动识图
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

    // 简单封装一下调用
    private void openGalleryForOcrSingle() {
        pickOneImageForOcr.launch("image/*");
    }




    // 如果是 app 专属目录文件，把它删掉（相册里不会有）
    private void cleanupIfTempFile(Uri uri) {
        if (uri == null) return;
        try {
            // 仅删除我们自己目录下的文件：/Android/data/<pkg>/files/Pictures/
            File picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (picturesDir != null) {
                File maybeFile = new File(picturesDir, new File(uri.getPath()).getName());
                if (maybeFile.exists()) {
                    // 安全起见，再次确认路径归属
                    if (maybeFile.getAbsolutePath().startsWith(picturesDir.getAbsolutePath())) {
                        //noinspection ResultOfMethodCallIgnored
                        maybeFile.delete();
                    }
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
            // 简单起见直接 setImageURI；后续可换 Glide 以适配大图/旋转
            holder.ivThumb.setImageURI(uri);
            holder.btnDelete.setOnClickListener(v -> onRemove.onRemove(uri));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    // 供“Create Bill”时使用：拿到所有要上传的图片 Uri
    public List<Uri> getReceiptUris() {
        return new ArrayList<>(receiptUris);
    }

    // 启动识别时的 UI
    private void startOcrUi() {
        if (ocrOverlay != null) ocrOverlay.setVisibility(View.VISIBLE);
        if (tvOcrStatus != null) tvOcrStatus.setText(getString(R.string.ocr_recognizing));
        if (layoutOcrResult != null) layoutOcrResult.setVisibility(View.GONE);
    }

    // 成功后的 UI & 回填
//    private void finishOcrUiSuccess(ReceiptOcrParser.OcrResult r) {
//        if (ocrOverlay != null) ocrOverlay.setVisibility(View.GONE);
//        if (layoutOcrResult != null) layoutOcrResult.setVisibility(View.VISIBLE);
//        if (tvOcrResult != null) tvOcrResult.setText(r.toString());
//
//        // 回填三个字段（有值才填，避免覆盖用户输入）
//        if (r.merchant != null && !r.merchant.isEmpty()) etMerchant.setText(r.merchant);
//        if (r.totalAmount != null && !r.totalAmount.isEmpty()) {
//            etAmount.setText(r.totalAmount);
//            try { totalAmount = Double.parseDouble(r.totalAmount); } catch (Exception ignored) {}
//            updateSplitAmounts();
//        }
//        if (r.date != null && !r.date.isEmpty()) tvDate.setText(r.date);
//
//        Toast.makeText(this, getString(R.string.ocr_success), Toast.LENGTH_SHORT).show();
//    }
    private void finishOcrUiSuccess(ReceiptOcrParser.OcrResult r) {
        if (ocrOverlay != null) ocrOverlay.setVisibility(View.GONE);
        if (layoutOcrResult != null) {
            layoutOcrResult.setVisibility(View.VISIBLE);
            layoutOcrResult.setOnClickListener(v -> layoutOcrResult.setVisibility(View.GONE)); // ← 点击收起
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

        // ← 自动隐藏（2.5 秒）
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> {
                    if (!isFinishing() && !isDestroyed() && layoutOcrResult != null)
                        layoutOcrResult.setVisibility(View.GONE);
                }, 2500);
    }


    // 失败时的 UI
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
            // 尝试从 Uri 构造 InputImage；EXIF 旋转 ML Kit 会处理
            InputImage image = InputImage.fromFilePath(this, uri);

            // 先用拉丁识别；若文本很空再尝试中文识别
            latinRecognizer.process(image)
                    .addOnSuccessListener(text -> {
                        String full = (text != null) ? text.getText() : "";
                        if (full == null || full.trim().isEmpty()) {
                            // 备用：中文识别
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

    private interface GeoCallback {
        void onSuccess(double lat, double lon);
        void onError(String msg);
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
                        .setQuery(query)
                        // 更兼容 Android 13+，允许地标、店铺、POI
                        .setCountries(getLikelyCountry());  // 限制国家（用你原函数）


        if (bias != null) builder.setLocationBias(bias);

        List<String> countries = getLikelyCountry();
        if (!countries.isEmpty()) builder.setCountries(countries);

        placesClient.findAutocompletePredictions(builder.build())
                .addOnSuccessListener(resp -> {
                    suggestions.clear();
                    suggestionPlaceIds.clear();
                    for (AutocompletePrediction p : resp.getAutocompletePredictions()) {
                        suggestions.add(p.getFullText(null).toString());
                        suggestionPlaceIds.add(p.getPlaceId());
                    }
                    addrAdapter.notifyDataSetChanged();
                    if (!suggestions.isEmpty()) etLocation.showDropDown();
                })
                .addOnFailureListener(e -> {
                    // 静默失败即可，避免打断用户
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
                    if (latLng != null) {
                        fetchCategoryFromNominatimDebounced(latitude, longitude, null);
                        categoryLocked = true; // 避免后续误改
                    }
                    locationPickedFromSuggestion = true;
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
                        fetchCategoryFromNominatimDebounced(lat, lon, null);

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
                    fetchCategoryFromNominatimDebounced(latitude, longitude, null);

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

    /** 无计费：OSM 自动补全（附近 + 关键词），把经纬度作为“placeId”返回 */

    /** 程序化回填地址（不会触发手动输入的警告） */
    private void fillLocationProgrammatically(@NonNull String text) {
        suppressLocationWatcher = true;                // 关掉 TextWatcher 的“手动输入”判定
        locationPickedFromSuggestion = true;           // 视为已从候选/系统选择的有效地址
        etLocation.setText(text);
        etLocation.setSelection(text.length());
        etLocation.post(() -> suppressLocationWatcher = false);
    }

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
                        fillLocationProgrammatically(line);
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
                    fillLocationProgrammatically(fallback);
                });
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        runOnUiThread(() -> {
                            String fallback = lat + ", " + lon;
                            fillLocationProgrammatically(fallback);
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
                        fillLocationProgrammatically(fallback);
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
}