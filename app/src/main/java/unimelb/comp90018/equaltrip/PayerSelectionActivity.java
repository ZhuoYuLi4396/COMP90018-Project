package unimelb.comp90018.equaltrip;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import androidx.appcompat.app.AppCompatActivity;

public class PayerSelectionActivity extends AppCompatActivity{
    private RadioGroup radioGroupTripmates;
    //private RadioButton radioA, radioB, radioC;
    private Button btnChangePayer;
    private TextView tvTitle;
    private String currentPayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payer_selection);

        //初始化控件
        initializeViews();
        setupListeners();

        // 从 Intent 拿 tripId
        String tripId = getIntent().getStringExtra("tripId");

        if (tripId == null || tripId.isEmpty()) {
            Toast.makeText(this, "Missing tripId", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 当前 payer (uid)
        currentPayer = getIntent().getStringExtra("current_payer");
        if (currentPayer == null) currentPayer = "";

        // 加载成员并动态添加 RadioButton
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("trips")
                .document(tripId)
                .collection("members")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String userId = doc.getString("userId");
                        String uid = doc.getString("uid");

                        RadioButton radioButton = new RadioButton(this);
                        radioButton.setText(userId);
                        radioButton.setTag(uid);
                        radioButton.setTextSize(16);
                        radioButton.setPadding(16, 16, 16, 16);

                        // 默认选中
                        if (uid.equals(currentPayer)) {
                            radioButton.setChecked(true);
                        }

                        radioGroupTripmates.addView(radioButton);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load members", Toast.LENGTH_SHORT).show();
                });
    }


    private void initializeViews() {
        tvTitle = findViewById(R.id.tv_title);
        radioGroupTripmates = findViewById(R.id.radio_group_tripmates);

        btnChangePayer = findViewById(R.id.btn_change_payer);

        tvTitle.setText("Tripmates in the trip");
    }

    private void setupListeners() {
        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Change payer button
        btnChangePayer.setOnClickListener(v -> {
            int checkedId = radioGroupTripmates.getCheckedRadioButtonId();
            RadioButton selectedBtn = findViewById(checkedId);

            if (selectedBtn != null) {
                String selectedUid = selectedBtn.getTag().toString();   // UID
                String selectedUserId = selectedBtn.getText().toString(); // userId

                Intent resultIntent = new Intent();
                resultIntent.putExtra("selected_payer_uid", selectedUid);
                resultIntent.putExtra("selected_payer_userid", selectedUserId);
                setResult(RESULT_OK, resultIntent);
            }
            finish();
        });

        // Radio group listener
        radioGroupTripmates.setOnCheckedChangeListener((group, checkedId) -> {
            // Update UI when selection changes
            updateButtonState();
        });
    }



    private String getSelectedPayer() {
        int checkedId = radioGroupTripmates.getCheckedRadioButtonId();
        RadioButton selectedBtn = findViewById(checkedId);
        if (selectedBtn != null && selectedBtn.getTag() != null) {
            return selectedBtn.getTag().toString();  // 返回 uid（或 userId）
        }
        return currentPayer; // fallback
    }

    private void updateButtonState() {
        // Enable button only if selection changed
        String selected = getSelectedPayer();
        btnChangePayer.setEnabled(!selected.equals(currentPayer));
    }
}