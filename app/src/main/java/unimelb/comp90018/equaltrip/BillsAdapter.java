package unimelb.comp90018.equaltrip;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class BillsAdapter extends RecyclerView.Adapter<BillsAdapter.VH> {
    private final List<Bill> data;
    private final String meUid;
    private final DisplayNameResolver nameResolver;
    private OnBillClickListener onBillClickListener; // 添加监听器

    public interface DisplayNameResolver { String nameOf(String uid); }

    // 添加接口以供 TripDetailActivity 处理点击事件
    public interface OnBillClickListener {
        void onBillClicked(String bid); // 当点击某个账单时触发此方法
    }

    // 设置监听器
    public void setOnBillClickListener(OnBillClickListener listener) {
        this.onBillClickListener = listener;
    }

    public BillsAdapter(List<Bill> data, String meUid, DisplayNameResolver resolver) {
        this.data = data; this.meUid = meUid; this.nameResolver = resolver;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) {
        Bill b = data.get(position);
        return (b != null && b.id != null) ? b.id.hashCode() : position;
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        View item = LayoutInflater.from(p.getContext()).inflate(R.layout.item_bill, p, false);
        return new VH(item);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Bill b = data.get(pos);
        if (b == null) return;

        h.tvTitle.setText(s(b.title, "Untitled bill"));

        String date = b.dateMs == null ? "" :
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new java.util.Date(b.dateMs));
        String cat  = s(b.category, "Misc");
        h.tvSub.setText(date.isEmpty() ? cat : (date + " | " + cat));

        String payer = (nameResolver != null && b.payerUid != null) ? nameResolver.nameOf(b.payerUid) : "Someone";
        h.tvWhoPaid.setText(meUid != null && meUid.equals(b.payerUid) ? "You paid" : payer + " paid");

        long cents = b.totalCents == null ? 0L : b.totalCents;
        h.tvAmount.setText(NumberFormat.getCurrencyInstance().format(cents / 100.0));

        // 设置点击事件
        h.itemView.setOnClickListener(v -> {
            if (onBillClickListener != null) {
                onBillClickListener.onBillClicked(b.id); // 点击账单时触发监听器
            }
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSub, tvWhoPaid, tvAmount;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle   = itemView.findViewById(R.id.tvTitle);
            tvSub     = itemView.findViewById(R.id.tvSub);
            tvWhoPaid = itemView.findViewById(R.id.tvWhoPaid);
            tvAmount  = itemView.findViewById(R.id.tvAmount);
        }
    }

    private static String s(String v, String def) {
        return (v == null || v.trim().isEmpty()) ? def : v;
    }
}
