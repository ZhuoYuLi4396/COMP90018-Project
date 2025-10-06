package unimelb.comp90018.equaltrip;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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

// JSON 解析
import org.json.JSONObject;

// ML Kit - Text Recognition
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;




public class AddBillActivity extends AppCompatActivity {
    private EditText etBillName, etMerchant, etLocation, etAmount;
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

    // === OCR Views ===
    private View ocrOverlay;                // 半透明遮罩
    private TextView tvOcrStatus;           // 遮罩上的“Recognizing…”
    private LinearLayout layoutOcrResult;   // 蓝色结果条容器
    private TextView tvOcrResult;           // 结果条文本
    private Button btnRetryOcr;             // “Retry OCR”按钮

    // === ML Kit Recognizers ===
    private TextRecognizer latinRecognizer;
    private TextRecognizer chineseRecognizer; // 备选（中文/混排更稳）



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

        initializeViews();
        setupListeners();
        setupSpinners();
        setupCategoryButtons();
        setupSplitRecyclerView();

        // 这里是测试代码用的，测试Nominatim API能不能用
        double testLat = -37.8183;
        double testLon = 144.9671;
        fetchCategoryFromNominatim(testLat, testLon);

        // Set default date
        tvDate.setText(dateFormat.format(calendar.getTime()));
    }

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
                        autoSelectCategory(category);
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void autoSelectCategory(String category) {
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

    private void initializeViews() {
        // Edit texts
        etBillName = findViewById(R.id.et_bill_name);
        etMerchant = findViewById(R.id.et_merchant);
        etLocation = findViewById(R.id.et_location);
        etAmount = findViewById(R.id.et_amount);

        // Text views
        tvDate = findViewById(R.id.tv_date);
        tvPaidBy = findViewById(R.id.tv_paid_by);
        tvParticipants = findViewById(R.id.tv_participants);
        tvTotalSplit = findViewById(R.id.tv_total_split);

        tvPaidBy.setText("None selected");

        // Spinners
        spinnerCurrency = findViewById(R.id.spinner_currency);

        // Buttons
        btnCreateBill = findViewById(R.id.btn_create_bill);
        btnReceiptCamera = findViewById(R.id.btn_receipt_camera);
        btnReceiptGallery = findViewById(R.id.btn_receipt_gallery);
        //btnRemoveReceipt = findViewById(R.id.btn_remove_receipt);

        // Category buttons
        btnDining = findViewById(R.id.btn_dining);
        btnTransport = findViewById(R.id.btn_transport);
        btnShopping = findViewById(R.id.btn_shopping);
        btnOther = findViewById(R.id.btn_other);

        // Receipt views
        receiptPlaceholder = findViewById(R.id.receipt_placeholder);
        receiptPreview = findViewById(R.id.receipt_preview);
        //ivReceiptPreview = findViewById(R.id.iv_receipt_preview);
        layoutReceipt = findViewById(R.id.layout_receipt);

        // Split method radio buttons
        rbEqual = findViewById(R.id.rb_equal);
        rbCustomize = findViewById(R.id.rb_customize);
        rgSplitMethod = findViewById(R.id.rg_split_method);

        // Split amount views
        layoutSplitDetails = findViewById(R.id.layout_split_details);
        rvSplitAmounts = findViewById(R.id.rv_split_amounts);

        // Back button
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

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

    // 放到成员变量
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

        // Receipt handling
        layoutReceipt.setOnClickListener(v -> {
            if (!hasReceipt) {
                showReceiptOptions();
            }
        });

        btnReceiptCamera.setOnClickListener(v -> tryOpenCamera());
        //btnReceiptCamera.setOnClickListener(v -> openCamera());
        btnReceiptGallery.setOnClickListener(v -> openGallery());
        //btnRemoveReceipt.setOnClickListener(v -> removeReceipt());
        //btnRemoveReceipt.setOnClickListener(v -> removeAllReceipts());

        // Amount input validation and split calculation
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
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    tvDate.setText(dateFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
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
                .setItems(new String[]{"Take Photo", "Choose from Gallery"},
                        (dialog, which) -> {
                            if (which == 0) {
                                tryOpenCamera();      // ✅ 改成先请求权限
                            } else {
                                openGallery();
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
        double value = Double.parseDouble(etAmount.getText().toString().trim());
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

        // 先定义一个列表收集 URL
        List<String> receiptUrlList = new ArrayList<>();

        // 开始向后端上传这份bill
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 生成一个新的 billId
        String billId = db.collection("trips")
                .document(tripId)
                .collection("bills")
                .document()
                .getId();


        // 构建 bill 数据
        Map<String, Object> billData = new HashMap<>();
        billData.put("billName", etBillName.getText().toString().trim());
        billData.put("merchant", etMerchant.getText().toString().trim());
        billData.put("location", etLocation.getText().toString().trim());
        billData.put("category", selectedCategory);
        billData.put("amount", totalAmount);
        // 经纬度
        Map<String, Object> geoPoint = new HashMap<>();
        geoPoint.put("lat", latitude);
        geoPoint.put("lon", longitude);
        billData.put("geo", geoPoint);

        // 向firebase发送图片
        billData.put("receiptUrls", receiptUrlList);

        // 获取当前选择的条目
        String currencyFull = spinnerCurrency.getSelectedItem().toString();
        // 拆分，取第二个部分（即 "AUD"、"USD"...）
        String currencyCode = currencyFull.split(" ")[1];
        billData.put("currency", spinnerCurrency.getSelectedItem().toString());  // 存到 Firestore
        billData.put("paidBy", selectedPayerUid); // ⚠️存 uid，而不是 username
        billData.put("participants", new ArrayList<>(selectedParticipantUids));
        billData.put("createdAt", Timestamp.now());

        // 构建 debts 列表
        List<Map<String, Object>> debtsList = new ArrayList<>();
        for (ParticipantSplit split : participantSplits) {
            if (!split.getName().equals(selectedPayerUserId)) { // 跳过 payer 自己
                Map<String, Object> debt = new HashMap<>();
                String fromUid = getUidFromUserId(split.getName());
                if (fromUid != null) {
                    debt.put("from", fromUid);               // 用 uid 存数据库
                    debt.put("to", selectedPayerUid);        // payer 的 uid
                    debt.put("amount", split.getAmount());   // 欠款金额
                    debtsList.add(debt);
                }
            }
        }

        billData.put("debts", debtsList);

        StorageReference storageRef = FirebaseStorage.getInstance().getReference();

        if (receiptUris.isEmpty()) {
            // 没有收据，直接存 Firestore
            db.collection("trips")
                    .document(tripId)
                    .collection("bills")
                    .document(billId)
                    .set(billData)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Bill uploaded successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Error uploading bill: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        } else {
            // 有收据，走异步上传逻辑
            for (Uri uri : receiptUris) {
                StorageReference fileRef = storageRef.child("receipts/" + tripId + "/" + System.currentTimeMillis() + ".jpg");

                fileRef.putFile(uri)
                        .addOnSuccessListener(taskSnapshot ->
                                fileRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                                    // 每次成功上传一张，就加到列表里
                                    receiptUrlList.add(downloadUri.toString());

                                    // 全部上传完后再保存 billData
                                    if (receiptUrlList.size() == receiptUris.size()) {
                                        billData.put("receiptUrls", receiptUrlList);

                                        db.collection("trips")
                                                .document(tripId)
                                                .collection("bills")
                                                .document(billId)
                                                .set(billData)
                                                .addOnSuccessListener(aVoid ->
                                                        Toast.makeText(this, "Bill uploaded with images!", Toast.LENGTH_SHORT).show()
                                                )
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
                    receiptUris.add(pendingCameraOutputUri);
                    pendingCameraOutputUri = null;
                    updateReceiptUI();
                    // 拍完自动识图（只对本次新增的图）
                    runOcrOnUri(receiptUris.get(receiptUris.size() - 1));
                } else {
                    // 失败或用户取消，清掉临时文件（如果有）
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
                // 申请持久读权限（重启后依然可读）
                for (Uri uri : uris) {
                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                    } catch (Exception ignored) {}
                }
                // 加入列表并刷新
                receiptUris.addAll(uris);
                updateReceiptUI();
                // 选图后也自动识图（以最后一张为例）
                runOcrOnUri(receiptUris.get(receiptUris.size() - 1));
            });


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
    private void finishOcrUiSuccess(ReceiptOcrParser.OcrResult r) {
        if (ocrOverlay != null) ocrOverlay.setVisibility(View.GONE);
        if (layoutOcrResult != null) layoutOcrResult.setVisibility(View.VISIBLE);
        if (tvOcrResult != null) tvOcrResult.setText(r.toString());

        // 回填三个字段（有值才填，避免覆盖用户输入）
        if (r.merchant != null && !r.merchant.isEmpty()) etMerchant.setText(r.merchant);
        if (r.totalAmount != null && !r.totalAmount.isEmpty()) {
            etAmount.setText(r.totalAmount);
            try { totalAmount = Double.parseDouble(r.totalAmount); } catch (Exception ignored) {}
            updateSplitAmounts();
        }
        if (r.date != null && !r.date.isEmpty()) tvDate.setText(r.date);

        Toast.makeText(this, getString(R.string.ocr_success), Toast.LENGTH_SHORT).show();
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



}