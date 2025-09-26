package unimelb.comp90018.equaltrip;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
public class PayerSelectionActivity extends AppCompatActivity{
    private RadioGroup radioGroupTripmates;
    private RadioButton radioA, radioB, radioC;
    private Button btnChangePayer;
    private TextView tvTitle;
    private String currentPayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payer_selection);

        initializeViews();
        setupListeners();

        // Get current payer from intent
        currentPayer = getIntent().getStringExtra("current_payer");
        if (currentPayer == null) currentPayer = "A";

        // Set current selection
        setCurrentSelection();
    }

    private void initializeViews() {
        tvTitle = findViewById(R.id.tv_title);
        radioGroupTripmates = findViewById(R.id.radio_group_tripmates);
        radioA = findViewById(R.id.radio_a);
        radioB = findViewById(R.id.radio_b);
        radioC = findViewById(R.id.radio_c);
        btnChangePayer = findViewById(R.id.btn_change_payer);

        tvTitle.setText("Tripmates in the trip");
    }

    private void setupListeners() {
        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Change payer button
        btnChangePayer.setOnClickListener(v -> {
            String selectedPayer = getSelectedPayer();
            Intent resultIntent = new Intent();
            resultIntent.putExtra("selected_payer", selectedPayer);
            setResult(RESULT_OK, resultIntent);
            finish();
        });

        // Radio group listener
        radioGroupTripmates.setOnCheckedChangeListener((group, checkedId) -> {
            // Update UI when selection changes
            updateButtonState();
        });
    }

    private void setCurrentSelection() {
        switch (currentPayer) {
            case "A":
                radioA.setChecked(true);
                break;
            case "B":
                radioB.setChecked(true);
                break;
            case "C":
                radioC.setChecked(true);
                break;
        }
    }

    private String getSelectedPayer() {
        int checkedId = radioGroupTripmates.getCheckedRadioButtonId();
        if (checkedId == R.id.radio_a) {
            return "A";
        } else if (checkedId == R.id.radio_b) {
            return "B";
        } else if (checkedId == R.id.radio_c) {
            return "C";
        }
        return "A"; // Default
    }

    private void updateButtonState() {
        // Enable button only if selection changed
        String selected = getSelectedPayer();
        btnChangePayer.setEnabled(!selected.equals(currentPayer));
    }
}