package unimelb.comp90018.equaltrip;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.*;

public class AddBillActivity extends AppCompatActivity {
    private EditText etBillName, etMerchant, etLocation, etAmount;
    private TextView tvDate, tvPaidBy, tvParticipants;
    private Spinner spinnerCurrency;
    private Button btnCreateBill;
    private ImageButton btnReceiptCamera, btnReceiptGallery, btnRemoveReceipt;
    private RadioButton rbEqual, rbCustomize;
    private RadioGroup rgSplitMethod;

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
    private String selectedPayer = "A"; // Default payer
    private ArrayList<String> selectedParticipants = new ArrayList<>();
    private boolean hasReceipt = false;
    private Bitmap receiptBitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_bill);

        initializeViews();
        setupListeners();
        setupSpinners();
        setupCategoryButtons();

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

        // Back button
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());
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

        // Amount input validation
        etAmount.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !etAmount.getText().toString().isEmpty()) {
                validateAmount();
            }
        });

        // Paid by - navigate to payer selection
        findViewById(R.id.layout_paid_by).setOnClickListener(v -> {
            Intent intent = new Intent(AddBillActivity.this, PayerSelectionActivity.class);
            intent.putExtra("current_payer", selectedPayer);
            startActivityForResult(intent, 1001);
        });

        // Participants - navigate to participants selection
        findViewById(R.id.layout_participants).setOnClickListener(v -> {
            Intent intent = new Intent(AddBillActivity.this, ParticipantsSelectionActivity.class);
            intent.putStringArrayListExtra("selected_participants", selectedParticipants);
            startActivityForResult(intent, 1002);
        });

        // Split method radio buttons
        rgSplitMethod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_customize) {
                // Handle customize split method
                Toast.makeText(this, "Custom split selected", Toast.LENGTH_SHORT).show();
            }
        });

        // Create Bill button
        btnCreateBill.setOnClickListener(v -> createBill());
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

        if (selectedParticipants.isEmpty()) {
            Toast.makeText(this, "Please select participants", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get split method
        String splitMethod = rbEqual.isChecked() ? "Equal" : "Customize";

        // Create bill logic here
        String message = "Bill created successfully!\n" +
                "Name: " + etBillName.getText().toString() + "\n" +
                "Merchant: " + etMerchant.getText().toString() + "\n" +
                "Category: " + selectedCategory + "\n" +
                "Amount: " + spinnerCurrency.getSelectedItem().toString().substring(0, 1) +
                etAmount.getText().toString() + "\n" +
                "Paid by: " + selectedPayer + "\n" +
                "Split: " + splitMethod;

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
                selectedPayer = data.getStringExtra("selected_payer");
                tvPaidBy.setText(selectedPayer);
            } else if (requestCode == 1002 && data != null) {
                // Participants selection result
                selectedParticipants = data.getStringArrayListExtra("selected_participants");
                if (selectedParticipants != null && !selectedParticipants.isEmpty()) {
                    if (selectedParticipants.size() == 1) {
                        tvParticipants.setText("1 selected");
                    } else {
                        tvParticipants.setText(selectedParticipants.size() + " selected");
                    }
                } else {
                    tvParticipants.setText("None selected");
                }
            }
        }
    }
}