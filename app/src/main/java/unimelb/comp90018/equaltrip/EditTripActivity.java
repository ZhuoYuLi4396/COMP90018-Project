package unimelb.comp90018.equaltrip;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EditTripActivity extends AppCompatActivity {

    private String tripId;
    private FirebaseFirestore db;

    private TextInputEditText etTripName, etLocation, etDesc;
    private MaterialButton btnStartDate, btnEndDate, btnCreateTrip; // 复用 id
    private Long startMillis, endMillis;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_trip); // 复用新增页布局

        db = FirebaseFirestore.getInstance();
        tripId = getIntent().getStringExtra("tripId");
        if (TextUtils.isEmpty(tripId)) { finish(); return; }

        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setTitle("Edit Trip");
        tb.setNavigationOnClickListener(v -> finish());

        etTripName   = findViewById(R.id.etTripName);
        etLocation   = findViewById(R.id.etLocation);
        etDesc       = findViewById(R.id.etDesc);
        btnStartDate = findViewById(R.id.btnStartDate);
        btnEndDate   = findViewById(R.id.btnEndDate);
        btnCreateTrip= findViewById(R.id.btnCreateTrip);
        btnCreateTrip.setText("Save changes");

        // 预填
        db.collection("trips").document(tripId).get().addOnSuccessListener(d -> {
            if (!d.exists()) { Toast.makeText(this, "Trip not found", Toast.LENGTH_SHORT).show(); finish(); return; }
            etTripName.setText(d.getString("name"));
            etLocation.setText(d.getString("location"));
            etDesc.setText(d.getString("description"));

            Long s = d.getLong("startDate");
            Long e = d.getLong("endDate");
            startMillis = s; endMillis = e;
            if (s != null) btnStartDate.setText(fmt(s));
            if (e != null) btnEndDate.setText(fmt(e));
        });

        // 日期选择（简单沿用范围选择器：任选一个按钮都弹范围）
        android.view.View.OnClickListener openRange = v -> {
            MaterialDatePicker.Builder<androidx.core.util.Pair<Long, Long>> b =
                    MaterialDatePicker.Builder.dateRangePicker().setTitleText("Select trip dates");
            if (startMillis != null && endMillis != null) b.setSelection(new androidx.core.util.Pair<>(startMillis, endMillis));
            MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker = b.build();
            picker.addOnPositiveButtonClickListener(sel -> {
                if (sel != null) {
                    startMillis = sel.first;
                    endMillis   = sel.second;
                    btnStartDate.setText(fmt(startMillis));
                    btnEndDate.setText(fmt(endMillis));
                }
            });
            picker.show(getSupportFragmentManager(), "edit_range");
        };
        btnStartDate.setOnClickListener(openRange);
        btnEndDate.setOnClickListener(openRange);

        btnCreateTrip.setOnClickListener(v -> save());
    }

    private void save() {
        String name = s(etTripName), loc = s(etLocation), desc = s(etDesc);
        if (name.isEmpty()) { etTripName.setError("Required"); return; }
        if (loc.isEmpty())  { etLocation.setError("Required"); return; }
        if (startMillis == null || endMillis == null || endMillis < startMillis) {
            Toast.makeText(this, "Invalid date range", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> up = new HashMap<>();
        up.put("name", name);
        up.put("location", loc);
        up.put("description", desc);
        up.put("startDate", startMillis);
        up.put("endDate", endMillis);
        Map<String, Object> dateMap = new HashMap<>();
        dateMap.put("start", ymd(startMillis));
        dateMap.put("end",   ymd(endMillis));
        up.put("date", dateMap);
        up.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        btnCreateTrip.setEnabled(false);
        db.collection("trips").document(tripId)
                .update(up)
                .addOnSuccessListener(v -> { Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show(); finish(); })
                .addOnFailureListener(e -> { btnCreateTrip.setEnabled(true);
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show(); });
    }

    private static String s(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
    private static String fmt(long ms){
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date(ms));
    }
    private static String ymd(long ms){
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(ms));
    }
}
