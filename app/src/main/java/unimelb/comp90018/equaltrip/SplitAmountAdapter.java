package unimelb.comp90018.equaltrip;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Locale;

public class SplitAmountAdapter extends RecyclerView.Adapter<SplitAmountAdapter.ViewHolder> {

    private ArrayList<ParticipantSplit> participantSplits;
    private boolean isEditableMode = false;
    private OnAmountChangeListener listener;

    public interface OnAmountChangeListener {
        void onAmountChanged(String participant, double newAmount);
    }

    public SplitAmountAdapter(ArrayList<ParticipantSplit> participantSplits, OnAmountChangeListener listener) {
        this.participantSplits = participantSplits;
        this.listener = listener;
    }

    public void setEditableMode(boolean editable) {
        this.isEditableMode = editable;
        notifyDataSetChanged();
    }

    public void updateData(ArrayList<ParticipantSplit> newData) {
        this.participantSplits = newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_split_amount, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ParticipantSplit split = participantSplits.get(position);

        // Set participant icon based on name (A, B, C)
        holder.tvParticipantName.setText(split.getName());
        setParticipantIcon(holder.ivParticipantIcon, split.getName());

        // Set amount
        String amountText = String.format(Locale.getDefault(), "%.2f", split.getAmount());

        if (isEditableMode) {
            // Show editable field
            holder.tvAmount.setVisibility(View.GONE);
            holder.etAmount.setVisibility(View.VISIBLE);
            holder.etAmount.setText(amountText);

            // Remove previous listener to avoid duplicates
            if (holder.etAmount.getTag() != null) {
                holder.etAmount.removeTextChangedListener((TextWatcher) holder.etAmount.getTag());
            }

            // Add text watcher for amount changes
            TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (!s.toString().isEmpty()) {
                        try {
                            double newAmount = Double.parseDouble(s.toString());
                            if (listener != null) {
                                listener.onAmountChanged(split.getName(), newAmount);
                            }
                        } catch (NumberFormatException e) {
                            // Invalid input, ignore
                        }
                    } else {
                        if (listener != null) {
                            listener.onAmountChanged(split.getName(), 0.0);
                        }
                    }
                }
            };

            holder.etAmount.addTextChangedListener(watcher);
            holder.etAmount.setTag(watcher);

        } else {
            // Show non-editable text
            holder.tvAmount.setVisibility(View.VISIBLE);
            holder.etAmount.setVisibility(View.GONE);
            holder.tvAmount.setText(amountText);
        }
    }

    private void setParticipantIcon(ImageView imageView, String name) {
        // Set different background colors for different participants
        switch (name) {
            case "A":
                imageView.setBackgroundResource(R.drawable.circle_participant_a);
                break;
            case "B":
                imageView.setBackgroundResource(R.drawable.circle_participant_b);
                break;
            case "C":
                imageView.setBackgroundResource(R.drawable.circle_participant_c);
                break;
            default:
                imageView.setBackgroundResource(R.drawable.circle_participant_default);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return participantSplits.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivParticipantIcon;
        TextView tvParticipantName;
        TextView tvAmount;
        EditText etAmount;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivParticipantIcon = itemView.findViewById(R.id.iv_participant_icon);
            tvParticipantName = itemView.findViewById(R.id.tv_participant_name);
            tvAmount = itemView.findViewById(R.id.tv_amount);
            etAmount = itemView.findViewById(R.id.et_amount);
        }
    }
}
