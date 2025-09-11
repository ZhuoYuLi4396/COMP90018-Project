package unimelb.comp90018.equaltrip;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

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
    }
}
