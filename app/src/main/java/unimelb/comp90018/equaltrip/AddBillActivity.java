package unimelb.comp90018.equaltrip;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.*;

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
    private String selectedCategory = "dining"; // Default category

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_bill);

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

        // Set default date
        tvDate.setText(dateFormat.format(calendar.getTime()));
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
        btnRemoveReceipt = findViewById(R.id.btn_remove_receipt);

        // Category buttons
        btnDining = findViewById(R.id.btn_dining);
        btnTransport = findViewById(R.id.btn_transport);
        btnShopping = findViewById(R.id.btn_shopping);
        btnOther = findViewById(R.id.btn_other);

        // Receipt views
        receiptPlaceholder = findViewById(R.id.receipt_placeholder);
        receiptPreview = findViewById(R.id.receipt_preview);
        ivReceiptPreview = findViewById(R.id.iv_receipt_preview);
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

        btnReceiptCamera.setOnClickListener(v -> openCamera());
        btnReceiptGallery.setOnClickListener(v -> openGallery());
        btnRemoveReceipt.setOnClickListener(v -> removeReceipt());

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
    }

    /*
    private void updateSplitAmounts() {
        if (selectedParticipantUids.isEmpty()) {
            layoutSplitDetails.setVisibility(View.GONE);
            return;
        }

        layoutSplitDetails.setVisibility(View.VISIBLE);

        participantSplits.clear();

        if (rbEqual.isChecked()) {
            // Equal split
            double amountPerPerson = selectedParticipantUids.isEmpty() ? 0 : totalAmount / selectedParticipantUids.size();
            for (int i = 0; i < selectedParticipantUserIds.size(); i++) {
                String userId = selectedParticipantUserIds.get(i);  // 用于 UI 显示
                participantSplits.add(new ParticipantSplit(userId, amountPerPerson));
            }
        } else {
            // Customize split - initialize with equal amounts
            double amountPerPerson = selectedParticipantUids.isEmpty() ? 0 : totalAmount / selectedParticipantUids.size();
            for (int i = 0; i < selectedParticipantUserIds.size(); i++) {
                String userId = selectedParticipantUserIds.get(i);  // 用于 UI 显示
                participantSplits.add(new ParticipantSplit(userId, amountPerPerson));
            }
        }

        splitAdapter.updateData(participantSplits);
        updateTotalDisplay();
    }
    */

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
        participantSplits.clear();   // ⬅️ 必须清空，不然列表不会刷新

        if (rbEqual.isChecked()) {
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
            // Customize：沿用原逻辑
            double amountPerPerson = totalAmount / selectedParticipantUids.size();
            for (String userId : selectedParticipantUserIds) {
                participantSplits.add(new ParticipantSplit(userId, amountPerPerson));
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
        setCategorySelected(btnDining, true);
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

    private void showReceiptOptions() {
        new AlertDialog.Builder(this)
                .setTitle("Add Receipt")
                .setItems(new String[]{"Take Photo", "Choose from Gallery"},
                        (dialog, which) -> {
                            if (which == 0) {
                                openCamera();
                            } else {
                                openGallery();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openCamera() {
        // In a real app, you would open camera intent here
        Toast.makeText(this, "Opening camera...", Toast.LENGTH_SHORT).show();
        showReceiptPreview();
    }

    private void openGallery() {
        // In a real app, you would open gallery intent here
        Toast.makeText(this, "Opening gallery...", Toast.LENGTH_SHORT).show();
        showReceiptPreview();
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

        // Get split method
        String splitMethod = rbEqual.isChecked() ? "Equal" : "Customize";

        // Build split details
        StringBuilder splitDetails = new StringBuilder();
        for (ParticipantSplit split : participantSplits) {
            splitDetails.append(String.format(Locale.getDefault(),
                    "\n%s: %.2f", split.getName(), split.getAmount()));
        }

        // Create bill logic here
        String message = "Bill created successfully!\n" +
                "Name: " + etBillName.getText().toString() + "\n" +
                "Merchant: " + etMerchant.getText().toString() + "\n" +
                "Category: " + selectedCategory + "\n" +
                "Amount: " + spinnerCurrency.getSelectedItem().toString().substring(0, 1) +
                etAmount.getText().toString() + "\n" +
                "Paid by: " + selectedPayerUserId + "\n" +
                "Split: " + splitMethod +
                "\n\nSplit Details:" + splitDetails.toString();

        new AlertDialog.Builder(this)
                .setTitle("Success")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> {
                    // Return to previous screen or main screen
                    finish();
                })
                .show();
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
                    updateSplitAmounts();
                } else {
                    tvParticipants.setText("None selected");
                    layoutSplitDetails.setVisibility(View.GONE);
                }
            }
        }
    }
