package unimelb.comp90018.equaltrip;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class ParticipantsSelectionActivity extends AppCompatActivity{
    private Button btnConfirmParticipants;
    private TextView tvTitle;
    private ArrayList<String> selectedParticipants;
    private ArrayList<CheckBox> checkBoxes = new ArrayList<>();
    private ArrayList<String> memberUids = new ArrayList<>();

    ArrayList<String> selectedUids = new ArrayList<>();
    ArrayList<String> selectedUserIds = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_participants_selection);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v ->
                    getOnBackPressedDispatcher().onBackPressed()
            );
        }

        String tripId = getIntent().getStringExtra("tripId");
        if (tripId == null || tripId.isEmpty()) {
            Toast.makeText(this, "Missing tripId", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        initializeViews();
        setupListeners();

        // 从 Firebase 加载成员
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("trips")
                .document(tripId)
                .collection("members")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String userId = doc.getString("userId");
                        String uid = doc.getString("uid");

                        CheckBox checkBox = new CheckBox(this);
                        checkBox.setText(userId);     // 显示名
                        checkBox.setTag(uid);         // 实际 id
                        checkBox.setTextSize(16);
                        checkBox.setPadding(16, 16, 16, 16);
                        checkBox.setChecked(selectedParticipants.contains(uid));

                        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> updateButtonState());

                        // 加入到视图中和列表中
                        ((android.widget.LinearLayout) findViewById(R.id.checkbox_container)).addView(checkBox);
                        checkBoxes.add(checkBox);
                        memberUids.add(uid);
                    }

                    // 初次判断是否启用按钮
                    updateButtonState();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load members", Toast.LENGTH_SHORT).show();
                });

        // Get selected participants from intent
        selectedParticipants = getIntent().getStringArrayListExtra("selected_participants");
        if (selectedParticipants == null) {
            selectedParticipants = new ArrayList<>();
        }
    }

    private void initializeViews() {
        tvTitle = findViewById(R.id.tv_title);
        btnConfirmParticipants = findViewById(R.id.btn_confirm_participants);

        tvTitle.setText("Tripmates in the trip");
        btnConfirmParticipants.setText("Confirm Selection");
    }

    private void setupListeners() {


        // Confirm button
        btnConfirmParticipants.setOnClickListener(v -> {
            getSelectedParticipants(); // 更新 selectedUids 和 selectedUserIds

            Intent resultIntent = new Intent();
            resultIntent.putStringArrayListExtra("selected_participant_uids", selectedUids);
            resultIntent.putStringArrayListExtra("selected_participant_userids", selectedUserIds);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }

    private ArrayList<String> getSelectedParticipants() {
        // 每次调用先清空两个列表
        selectedUids.clear();
        selectedUserIds.clear();

        for (CheckBox cb : checkBoxes) {
            if (cb.isChecked() && cb.getTag() != null) {
                selectedUids.add(cb.getTag().toString());          // UID
                selectedUserIds.add(cb.getText().toString());      // User ID
            }
        }
        // 这里返回 UID 列表给 updateButtonState 用
        return new ArrayList<>(selectedUids);
    }

    private void updateButtonState() {
        // Enable button only if at least one participant is selected
        ArrayList<String> selected = getSelectedParticipants();
        btnConfirmParticipants.setEnabled(!selected.isEmpty());
    }

}