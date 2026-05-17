package me.dreamvoid.mtweaks.activities;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import me.dreamvoid.mtweaks.databinding.ItemWifiCheckerBinding;

import java.util.List;

public class WifiListAdapter extends RecyclerView.Adapter<WifiListAdapter.ViewHolder> {

    private final List<CheckListItem> mItems;
    private final OnItemCheckChangeListener mListener;

    public interface OnItemCheckChangeListener {
        void onCheckedChanged(CheckListItem item, boolean isChecked);
    }

    public WifiListAdapter(List<CheckListItem> items, OnItemCheckChangeListener listener) {
        this.mItems = items;
        this.mListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWifiCheckerBinding binding = ItemWifiCheckerBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CheckListItem item = mItems.get(position);

        holder.binding.tvTitle.setText(item.title);
        holder.binding.tvDescription.setText(item.description);
        if (item.icon != null) {
            holder.binding.ivIcon.setImageDrawable(item.icon);
        }

        // 先移除监听器，防止错乱，设置完状态后再挂载监听
        holder.binding.checkbox.setOnCheckedChangeListener(null);
        holder.binding.checkbox.setChecked(item.checked);

        holder.itemView.setOnClickListener(v -> holder.binding.checkbox.performClick());

        holder.binding.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.checked = isChecked;
            if (mListener != null) {
                mListener.onCheckedChanged(item, isChecked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemWifiCheckerBinding binding;

        ViewHolder(ItemWifiCheckerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}