package unimelb.comp90018.equaltrip;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
public class ParticipantsSelectionActivity extends AppCompatActivity{
    private CheckBox checkBoxA, checkBoxB, checkBoxC;
    private Button btnConfirmParticipants;
    private TextView tvTitle;
    private ArrayList<String> selectedParticipants;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_participants_selection);

        initializeViews();
        setupListeners();

        // Get selected participants from intent
        selectedParticipants = getIntent().getStringArrayListExtra("selected_participants");
        if (selectedParticipants == null) {
            selectedParticipants = new ArrayList<>();
        }

        // Set current selections
        setCurrentSelections();
    }

    private void initializeViews() {
        tvTitle = findViewById(R.id.tv_title);
        checkBoxA = findViewById(R.id.checkbox_a);
        checkBoxB = findViewById(R.id.checkbox_b);
        checkBoxC = findViewById(R.id.checkbox_c);
        btnConfirmParticipants = findViewById(R.id.btn_confirm_participants);

        tvTitle.setText("Tripmates in the trip");
        btnConfirmParticipants.setText("Confirm Selection");
    }

    private void setupListeners() {
        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Confirm button
        btnConfirmParticipants.setOnClickListener(v -> {
            ArrayList<String> selected = getSelectedParticipants();
            Intent resultIntent = new Intent();
            resultIntent.putStringArrayListExtra("selected_participants", selected);
            setResult(RESULT_OK, resultIntent);
            finish();
        });

        // Checkbox listeners to update button state
        checkBoxA.setOnCheckedChangeListener((buttonView, isChecked) -> updateButtonState());
        checkBoxB.setOnCheckedChangeListener((buttonView, isChecked) -> updateButtonState());
        checkBoxC.setOnCheckedChangeListener((buttonView, isChecked) -> updateButtonState());
    }

    private void setCurrentSelections() {
        checkBoxA.setChecked(selectedParticipants.contains("A"));
        checkBoxB.setChecked(selectedParticipants.contains("B"));
        checkBoxC.setChecked(selectedParticipants.contains("C"));
    }

    private ArrayList<String> getSelectedParticipants() {
        ArrayList<String> selected = new ArrayList<>();
        if (checkBoxA.isChecked()) selected.add("A");
        if (checkBoxB.isChecked()) selected.add("B");
        if (checkBoxC.isChecked()) selected.add("C");
        return selected;
    }

    private void updateButtonState() {
        // Enable button only if at least one participant is selected
        ArrayList<String> selected = getSelectedParticipants();
        btnConfirmParticipants.setEnabled(!selected.isEmpty());
    }

}
