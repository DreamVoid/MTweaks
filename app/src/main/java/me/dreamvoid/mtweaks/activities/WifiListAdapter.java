package me.dreamvoid.mtweaks.activities;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import li.lingfeng.ltweaks.activities.ListCheckActivity;
import me.dreamvoid.mtweaks.R;

import java.util.List;

public class WifiListAdapter extends RecyclerView.Adapter<WifiListAdapter.ViewHolder> {

    // 复用你原本的 ListItem 类（如果它属于外部类，请正确引包）
    private final List<ListCheckActivity.DataProvider.ListItem> mItems;
    private final OnItemCheckChangeListener mListener;

    public interface OnItemCheckChangeListener {
        void onCheckedChanged(ListCheckActivity.DataProvider.ListItem item, boolean isChecked);
    }

    public WifiListAdapter(List<ListCheckActivity.DataProvider.ListItem> items, OnItemCheckChangeListener listener) {
        this.mItems = items;
        this.mListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 使用标准的带 CheckBox 的 List Item 布局，这里用系统的或者自定义的均可
        // 为了演示，这里假设你有一个自定义的 R.layout.item_wifi_check
        // 如果没有，可以用下面的代码动态绑定，或者参照系统 simple_list_item_multiple_choice
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wifi_checker, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ListCheckActivity.DataProvider.ListItem item = mItems.get(position);

        holder.tvTitle.setText(item.mTitle);
        holder.tvDescription.setText(item.mDescription);
        if (item.mIcon != null) {
            holder.ivIcon.setImageDrawable(item.mIcon);
        }

        // 先移除监听器，防止错乱，设置完状态后再挂载监听
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(item.mChecked);

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.mChecked = isChecked;
            if (mListener != null) {
                mListener.onCheckedChanged(item, isChecked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mItems != null ? mItems.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle;
        TextView tvDescription;
        CheckBox checkBox;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // 对应 item_wifi_check.xml 中的 id
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvDescription = itemView.findViewById(R.id.tv_description);
            checkBox = itemView.findViewById(R.id.checkbox);
        }
    }
}