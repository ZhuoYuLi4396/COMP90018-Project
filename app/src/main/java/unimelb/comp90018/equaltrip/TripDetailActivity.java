package unimelb.comp90018.equaltrip;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

import android.content.Intent;      // +++
import android.widget.Button;       // +++
import android.widget.Toast;        // +++

public class TripDetailActivity extends AppCompatActivity {


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_detail);

        // 顶部返回
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 从 intent 取出 tripId
        String tripId = getIntent().getStringExtra("tripId");

        // 简单显示
        TextView tv = findViewById(R.id.tvTripInfo);
        tv.setText("Trip detail page\nTripId: " + tripId);

        // 绑定你刚加的按钮（确保 XML 里的 id 是 btn_add_bill）
        Button btnAddBill = findViewById(R.id.btn_add_bill);
        btnAddBill.setOnClickListener(v -> {
            if (tripId == null || tripId.isEmpty()) {
                Toast.makeText(this, "Missing tripId", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(TripDetailActivity.this, AddBillActivity.class);
            i.putExtra("tripId", tripId);   // 传给 AddBillActivity
            startActivity(i);
        });
    }
}
