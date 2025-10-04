package unimelb.comp90018.equaltrip;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.VH> {

    public interface OnTripClick {
        void onClick(Trip t);
    }

    private final List<Trip> data;
    private final OnTripClick onTripClick;

    public TripAdapter(List<Trip> data, OnTripClick click) {
        this.data = data;
        this.onTripClick = click;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trip_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        // 越界/空对象保护
        if (pos < 0 || pos >= data.size()) {
            bindEmpty(h);
            return;
        }
        Trip t = data.get(pos);
        if (t == null) {
            bindEmpty(h);
            return;
        }

        // 文本兜底
        h.tvTitle.setText(nz(t.name, "Untitled Trip"));
        h.tvCity.setText(nz(t.location, "Unknown"));

        // 日期与人数兜底（允许为 null）
        String datePart = formatRangeSafe(t.startDate, t.endDate);
        int matesCount = (t.tripmates == null) ? 0 : t.tripmates.size();
        h.tvMeta.setText(datePart + " | Tripmates: " + matesCount);

        // 动态小圆点
        bindChips(h, t);

        // 点击事件
        h.itemView.setOnClickListener(v -> {
            if (onTripClick != null) onTripClick.onClick(t);
        });
    }

    private void bindChips(@NonNull VH h, Trip t) {
        if (h.chips == null) return; //兜底
        h.chips.removeAllViews();

        if (t.tripmates == null || t.tripmates.isEmpty()) return;

        // 如果担心过多挤爆一行，可以设一个上限（比如 10）
        // 这里不设上限，全部显示
        for (int i = 0; i < t.tripmates.size(); i++) {
            View dot = new View(h.itemView.getContext());
            int size = dp(h.itemView, 18);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i != t.tripmates.size() - 1) {
                lp.setMarginEnd(dp(h.itemView, 6));
            }
            dot.setLayoutParams(lp);
            dot.setBackground(h.itemView.getContext().getDrawable(R.drawable.bg_chip_circle));
            h.chips.addView(dot);
        }
    }

    // ---------- helpers ----------
    private static String nz(String s, String def) {
        return (s == null || s.trim().isEmpty()) ? def : s;
    }

    private static String formatRangeSafe(Long startMs, Long endMs) {
        java.text.SimpleDateFormat f =
                new java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault());
        String left  = (startMs == null) ? "?" : f.format(new java.util.Date(startMs));
        String right = (endMs   == null) ? "?" : f.format(new java.util.Date(endMs));
        return left + " - " + right;
    }

    private void bindEmpty(@NonNull VH h) {
        h.tvTitle.setText("Untitled Trip");
        h.tvCity.setText("Unknown");
        h.tvMeta.setText("? - ? | Tripmates: 0");
        h.chips.removeAllViews();
        h.itemView.setOnClickListener(null);
    }

    private int dp(@NonNull View v, int dps) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dps, v.getResources().getDisplayMetrics()));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTitle, tvCity, tvMeta;
        final LinearLayout chips;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvCity  = itemView.findViewById(R.id.tv_city);
            tvMeta  = itemView.findViewById(R.id.tv_meta);
            chips   = itemView.findViewById(R.id.chips_container);

            // 水波纹点击效果
            if (itemView instanceof MaterialCardView) {
                itemView.setClickable(true);
                itemView.setForeground(itemView.getContext()
                        .getDrawable(android.R.drawable.list_selector_background));
            }
        }
    }

    //（未使用）若要显示“年”格式：
    private static String formatRange(Long s, Long e) {
        if (s == null || e == null) return "";
        Date sd = new Date(s), ed = new Date(e);
        SimpleDateFormat y = new SimpleDateFormat("yyyy", Locale.getDefault());
        SimpleDateFormat mdy = new SimpleDateFormat("MMM d", Locale.getDefault());
        String year = y.format(ed);
        return mdy.format(sd) + " - " + mdy.format(ed) + ", " + year;
    }
}
