package me.dreamvoid.mtweaks.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Pair;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import li.lingfeng.ltweaks.activities.ListCheckActivity;
import li.lingfeng.ltweaks.prefs.Prefs;
import li.lingfeng.ltweaks.utils.Logger;
import li.lingfeng.ltweaks.utils.NetUtils;
import li.lingfeng.ltweaks.utils.Utils;
import me.dreamvoid.mtweaks.R;
import me.dreamvoid.mtweaks.databinding.ActivityWlanTrustAgentBinding;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WLANTrustAgentActivity extends AppCompatActivity {

    private ActivityWlanTrustAgentBinding binding;

    private Set<String> mTrustedAps;
    private List<ListCheckActivity.DataProvider.ListItem> mListItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityWlanTrustAgentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 1. 初始化新式现代化的 Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        CollapsingToolbarLayout collapsingToolbar = findViewById(R.id.collapsingToolbar);
        collapsingToolbar.setTitle(getString(R.string.pref_trust_agent_wifi));

        // 2. 初始化列表
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 3. 迁移并加载数据
        loadData();

        // 4. 设置适配器与桥接原本的 onCheckedChanged 逻辑
        WifiListAdapter mAdapter = new WifiListAdapter(mListItems, (item, isChecked) -> {
            // 完全保留你原本的业务逻辑
            Pair pair = (Pair) item.mData;
            String ap = pair.first + "," + pair.second;
            if (isChecked) {
                Logger.i("Trust wifi " + ap);
                mTrustedAps.add(ap);
            } else {
                Logger.i("Revoke wifi " + ap);
                mTrustedAps.remove(ap);
            }
            Prefs.instance().edit()
                    .putStringSet(R.string.key_trust_agent_wifi_aps, mTrustedAps)
                    .commit();
        });

        recyclerView.setAdapter(mAdapter);
    }
    /**
     * 核心数据业务逻辑：完全从原本的 DataProvider 构造函数中平移过来
     */
    private void loadData() {
        mTrustedAps = new HashSet<>(
                Prefs.instance().getStringSet(R.string.key_trust_agent_wifi_aps, new HashSet<>())
        );
        mListItems = new ArrayList<>(mTrustedAps.size() + 1);

        WifiInfo wifiInfo = NetUtils.getWifiInfo();
        boolean hasCurrent = false;

        for (String ap : mTrustedAps) {
            final String[] s = Utils.splitByLastChar(ap, ',');
            boolean isCurrent = (wifiInfo != null
                    && StringUtils.strip(wifiInfo.getSSID(), "\"").equals(s[0])
                    && wifiInfo.getBSSID().equals(s[1]));
            hasCurrent |= isCurrent;

            ListCheckActivity.DataProvider.ListItem item = createListItem(s[0], s[1], isCurrent, true);
            mListItems.add(item);
        }

        if (wifiInfo != null && !hasCurrent) {
            ListCheckActivity.DataProvider.ListItem item = createListItem(StringUtils.strip(wifiInfo.getSSID(), "\""), wifiInfo.getBSSID(), true, false);
            mListItems.add(item);
        }
    }

    private ListCheckActivity.DataProvider.ListItem createListItem(final String ssid, final String bssid, boolean isCurrent, boolean isChecked) {
        ListCheckActivity.DataProvider.ListItem item = new ListCheckActivity.DataProvider.ListItem();
        item.mData = new Pair<>(ssid, bssid);
        // 注意：将原本的 mActivity 替换为 this
        item.mIcon = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_wifi, null);
        item.mTitle = ssid + (isCurrent ? (" (" + getString(R.string.current) + ")") : "");
        item.mDescription = bssid;
        item.mChecked = isChecked;
        return item;
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android 8 开始需要位置信息权限
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,}, 0);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean hasBackgroundPermission = checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;

            if (hasBackgroundPermission) {
                return;
            }
            // 1. 弹出自定义对话框，向用户解释为什么需要“始终允许”
            showCustomRationaleDialog(WLANTrustAgentActivity.this, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // 2. 用户点击确定后，跳转到系统设置页
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            });
        }
    }

    private static void showCustomRationaleDialog(WLANTrustAgentActivity context, DialogInterface.OnClickListener onConfirmListener) {
        new AlertDialog.Builder(context)
                .setTitle("“始终允许”位置信息权限")
                .setMessage("需要“始终允许”的位置信息权限，才能在后台获取当前连接的 WLAN 信息，否则，可信代理将无法工作。\n\n在接下来的界面中选择：权限 -> 位置信息 -> 始终允许。")
                .setPositiveButton("去设置", onConfirmListener)
                .setNegativeButton("取消", (dialog, which) -> {
                    dialog.dismiss();
                    context.finish();
                    // 这里可以处理用户拒绝后的逻辑（如提示功能无法使用）
                })
                .setCancelable(false) // 强制用户做出选择
                .show();
    }
}