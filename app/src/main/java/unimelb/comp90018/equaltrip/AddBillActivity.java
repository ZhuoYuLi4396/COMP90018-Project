package unimelb.comp90018.equaltrip;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.*;

public class AddBillActivity extends AppCompatActivity{
    private EditText etBillName, etMerchant, etLocation, etAmount;
    private TextView tvDate, tvPaidBy, tvParticipants, tvReceipt;
    private Spinner spinnerCurrency, spinnerSplitMethod;
    private Button btnCreateBill;
    private ImageButton btnReceiptCamera, btnReceiptGallery;

    private Calendar calendar = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private String selectedPayer = "A"; // Default payer
    private ArrayList<String> selectedParticipants = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_bill);

        initializeViews();
        setupListeners();
        setupSpinners();

        // Set default date
        tvDate.setText(dateFormat.format(calendar.getTime()));
    }

    private void initializeViews() {
        etBillName = findViewById(R.id.et_bill_name);
        etMerchant = findViewById(R.id.et_merchant);
        etLocation = findViewById(R.id.et_location);
        etAmount = findViewById(R.id.et_amount);

        tvDate = findViewById(R.id.tv_date);
        tvPaidBy = findViewById(R.id.tv_paid_by);
        tvParticipants = findViewById(R.id.tv_participants);
        tvReceipt = findViewById(R.id.tv_receipt);

        spinnerCurrency = findViewById(R.id.spinner_currency);
        spinnerSplitMethod = findViewById(R.id.spinner_split_method);

        btnCreateBill = findViewById(R.id.btn_create_bill);
        btnReceiptCamera = findViewById(R.id.btn_receipt_camera);
        btnReceiptGallery = findViewById(R.id.btn_receipt_gallery);

        // Set hints
        etBillName.setHint("e.g. Lunch at Italian restaurant");
        etMerchant.setHint("Pizzeria");
        etLocation.setHint("362 Little Bourke St");
        etAmount.setHint("0.00");
    }

    private void setupListeners() {
        // Date picker
        tvDate.setOnClickListener(v -> showDatePicker());
        findViewById(R.id.layout_date).setOnClickListener(v -> showDatePicker());

        // Receipt buttons
        btnReceiptCamera.setOnClickListener(v -> openCamera());
        btnReceiptGallery.setOnClickListener(v -> openGallery());
        findViewById(R.id.layout_receipt).setOnClickListener(v -> showReceiptOptions());

        // Amount input
        etAmount.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !etAmount.getText().toString().isEmpty()) {
                showAmountConfirmation();
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

        // Create Bill button
        btnCreateBill.setOnClickListener(v -> createBill());
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

        // Split method spinner
        String[] splitMethods = {"Equal", "Customize"};
        ArrayAdapter<String> splitAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, splitMethods
        );
        spinnerSplitMethod.setAdapter(splitAdapter);
        spinnerSplitMethod.setSelection(0); // Default to Equal
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

    private void showAmountConfirmation() {
        String amount = etAmount.getText().toString();
        try {
            double value = Double.parseDouble(amount);
            String currency = spinnerCurrency.getSelectedItem().toString().substring(0, 1);

            new AlertDialog.Builder(this)
                    .setTitle("Amount Confirmation")
                    .setMessage("You entered: " + currency + String.format("%.2f", value))
                    .setPositiveButton("OK", null)
                    .show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show();
        }
    }

    private void showReceiptOptions() {
        new AlertDialog.Builder(this)
                .setTitle("Receipt")
                .setMessage("Choose how to add receipt")
                .setPositiveButton("Camera", (dialog, which) -> openCamera())
                .setNegativeButton("Gallery", (dialog, which) -> openGallery())
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void openCamera() {
        // In a real app, you would open camera intent here
        Toast.makeText(this, "Opening camera...", Toast.LENGTH_SHORT).show();
        tvReceipt.setText("Receipt captured from camera");
    }

    private void openGallery() {
        // In a real app, you would open gallery intent here
        Toast.makeText(this, "Opening gallery...", Toast.LENGTH_SHORT).show();
        tvReceipt.setText("Receipt selected from gallery");
    }

    private void createBill() {
        // Validate inputs
        if (etBillName.getText().toString().isEmpty()) {
            Toast.makeText(this, "Please enter bill name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (etAmount.getText().toString().isEmpty()) {
            Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedParticipants.isEmpty()) {
            Toast.makeText(this, "Please select participants", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create bill logic here
        String message = "Bill created successfully!\n" +
                "Name: " + etBillName.getText().toString() + "\n" +
                "Amount: " + spinnerCurrency.getSelectedItem().toString().substring(0, 1) +
                etAmount.getText().toString() + "\n" +
                "Paid by: " + selectedPayer + "\n" +
                "Split: " + spinnerSplitMethod.getSelectedItem().toString();

        new AlertDialog.Builder(this)
                .setTitle("Success")
                .setMessage(message)
                .setPositiveButton("OK", null)
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
                    tvParticipants.setText(String.join(", ", selectedParticipants));
                } else {
                    tvParticipants.setText("1 selected");
                }
            }
        }
    }
}
