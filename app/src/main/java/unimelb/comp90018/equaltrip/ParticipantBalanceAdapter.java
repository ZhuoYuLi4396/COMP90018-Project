package unimelb.comp90018.equaltrip;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class ParticipantBalanceAdapter extends RecyclerView.Adapter<ParticipantBalanceAdapter.VH> {

    private List<ParticipantBalance> data = new ArrayList<>();

    public void submit(List<ParticipantBalance> newData) {
        this.data = newData != null ? newData : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_participant_balance, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ParticipantBalance item = data.get(position);

        holder.tvName.setText(item.displayName);
        holder.tvRight.setText(item.rightText);

        // 根据是否是付款人设置颜色
        if (item.isPayer) {
            // 付款人 - 黑色
            holder.tvRight.setTextColor(Color.parseColor("#000000"));
        } else {
            // 欠款人 - 红色
            holder.tvRight.setTextColor(Color.parseColor("#F44336")); // Material Red
        }

        // 加载头像
        if (item.photoUrl != null && !item.photoUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(item.photoUrl)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .circleCrop()
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName, tvRight;

        VH(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            tvRight = itemView.findViewById(R.id.tvRight);
        }
    }
}