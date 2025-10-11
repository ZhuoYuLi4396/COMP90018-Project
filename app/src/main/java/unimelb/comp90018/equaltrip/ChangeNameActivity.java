package unimelb.comp90018.equaltrip;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChangeNameActivity extends AppCompatActivity {

    private TextInputEditText etName;
    private View btnSave, btnCancel;
    private ProgressBar progress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 👇 确保文件名与实际一致
        setContentView(R.layout.activity_change_name);

        etName   = findViewById(R.id.etName);
        btnSave  = findViewById(R.id.btnSave);
        btnCancel= findViewById(R.id.btnCancel);
        progress = findViewById(R.id.progress);

        // 预填当前名字（可选）
        String current = getIntent().getStringExtra("current_name");
        if (current != null && !current.isEmpty()) etName.setText(current);

        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveName());
    }

    private void saveName() {
        String newName = etName.getText() == null ? "" : etName.getText().toString().trim();
        if (newName.isEmpty()) {
            Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (uid == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("userId", newName)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Name updated", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!loading);
        btnCancel.setEnabled(!loading);
        etName.setEnabled(!loading);
    }
}