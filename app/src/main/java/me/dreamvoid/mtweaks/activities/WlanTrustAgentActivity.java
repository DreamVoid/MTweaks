package me.dreamvoid.mtweaks.activities;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDividerItemDecoration;
import me.dreamvoid.mtweaks.R;
import me.dreamvoid.mtweaks.databinding.ActivityWlanTrustAgentBinding;
import me.dreamvoid.mtweaks.databinding.ItemWifiCheckerBinding;
import me.dreamvoid.mtweaks.prefs.Prefs;
import me.dreamvoid.mtweaks.utils.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WlanTrustAgentActivity extends AppCompatActivity {

    private ActivityWlanTrustAgentBinding binding;

    private Set<String> mTrustedAps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UiUtils.enableEdgeToEdge(this, false);

        binding = ActivityWlanTrustAgentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // The app bar draws behind the status bar so both keep the same surface color.
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0);
            binding.recyclerView.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());

        MaterialToolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.collapsingToolbar.setTitle(getString(R.string.pref_trust_agent_wifi));

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.recyclerView.setLayoutManager(layoutManager);
        MaterialDividerItemDecoration dividerItemDecoration = new MaterialDividerItemDecoration(this, RecyclerView.VERTICAL);
        dividerItemDecoration.setLastItemDecorated(false);
        binding.recyclerView.addItemDecoration(dividerItemDecoration);

        mTrustedAps = new HashSet<>(Prefs.getStringSet(R.string.key_trust_agent_wifi_aps, Set.of()));
        List<CheckListItem> mListItems = new ArrayList<>(mTrustedAps.size() + 1);

        WifiConnection connection = NetUtils.getCurrentWifi(this);
        if (connection.valid()) {
            Logger.i("Current wifi: " + connection);
        } else {
            Logger.w("Current wifi unavailable: " + connection.reason());
        }
        boolean hasCurrent = false;

        for (String ap1 : mTrustedAps) {
            final String[] s = Utils.splitByLastChar(ap1, ',');
            if (s.length != 2) {
                continue;
            }
            boolean isCurrent = connection.valid() && s[0].equals(connection.ssid()) && s[1].equalsIgnoreCase(connection.bssid());
            hasCurrent |= isCurrent;

            CheckListItem item1 = createListItem(s[0], s[1], isCurrent, true);
            mListItems.add(item1);
        }

        if (connection.valid() && !hasCurrent) {
            CheckListItem item1 = createListItem(connection.ssid(), connection.bssid(), true, false);
            mListItems.add(item1);
        }

        WLANListAdapter mAdapter = new WLANListAdapter(mListItems, (item, isChecked) -> {
            Pair pair = (Pair) item.data;
            String ap = pair.first + "," + pair.second;
            if (isChecked) {
                Logger.i("Trust wifi " + ap);
                mTrustedAps.add(ap);
            } else {
                Logger.i("Revoke wifi " + ap);
                mTrustedAps.remove(ap);
            }
            if (Prefs.get() != null) {
                Prefs.get().edit().putStringSet(getString(R.string.key_trust_agent_wifi_aps), mTrustedAps).apply();
            }
        });

        binding.recyclerView.setAdapter(mAdapter);
    }

    private CheckListItem createListItem(final String ssid, final String bssid, boolean isCurrent, boolean isChecked) {
        CheckListItem item = new CheckListItem();
        item.data = new Pair<>(ssid, bssid);
        item.icon = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_wifi, null);
        item.title = ssid + (isCurrent ? (" (" + getString(R.string.current) + ")") : "");
        item.description = bssid;
        item.checked = isChecked;
        return item;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Android 8 开始需要位置信息权限
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,}, 0);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 开始需要附近设备权限
            if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean hasBackgroundPermission = checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;

            if (hasBackgroundPermission) {
                return;
            }
            showCustomRationaleDialog(WlanTrustAgentActivity.this, (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            });
        }
    }

    private static void showCustomRationaleDialog(WlanTrustAgentActivity context, DialogInterface.OnClickListener onConfirmListener) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("“始终允许”位置信息权限")
                .setMessage("需要“始终允许”的位置信息权限，才能在后台获取当前连接的 WLAN 信息，否则，可信代理将无法工作。\n\n在接下来的界面中选择：权限 -> 位置信息 -> 始终允许。")
                .setPositiveButton("去设置", onConfirmListener)
                .setNegativeButton("取消", (dialog, which) -> {
                    dialog.dismiss();
                    context.finish();
                }).setCancelable(false).show();
    }

    public static class WLANListAdapter extends RecyclerView.Adapter<WLANListAdapter.ViewHolder> {

        private final List<CheckListItem> mItems;
        private final OnItemCheckChangeListener mListener;

        public interface OnItemCheckChangeListener {
            void onCheckedChanged(CheckListItem item, boolean isChecked);
        }

        public WLANListAdapter(List<CheckListItem> items, OnItemCheckChangeListener listener) {
            this.mItems = items;
            this.mListener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemWifiCheckerBinding binding = ItemWifiCheckerBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
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

    /**
     * 复选列表条目。
     */
    public static final class CheckListItem {

        /**
         * 条目携带的原始数据。
         */
        public Object data;
        /**
         * 左侧图标。
         */
        public Drawable icon;
        /**
         * 标题。
         */
        public CharSequence title;
        /**
         * 副标题。
         */
        public CharSequence description;
        /**
         * 是否勾选。
         */
        public boolean checked;
    }
}