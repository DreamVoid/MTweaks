package li.lingfeng.ltweaks.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.provider.Settings;
import android.util.Pair;
import androidx.core.app.ActivityCompat;
import li.lingfeng.ltweaks.prefs.Prefs;
import li.lingfeng.ltweaks.utils.Logger;
import li.lingfeng.ltweaks.utils.NetUtils;
import li.lingfeng.ltweaks.utils.Utils;
import me.dreamvoid.mtweaks.R;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by lilingfeng on 2017/7/6.
 */

public class TrustAgentWifiSettings extends ListCheckActivity {

    @Override
    protected Class<? extends ListCheckActivity.DataProvider> getDataProviderClass() {
        return DataProvider.class;
    }

    public static class DataProvider extends ListCheckActivity.DataProvider {

        private Set<String> mTrustedAps;
        private List<ListItem> mListItems;

        public DataProvider(ListCheckActivity activity) {
            super(activity);
            mTrustedAps = new HashSet<>(
                    Prefs.instance().getStringSet(R.string.key_trust_agent_wifi_aps, new HashSet<String>())
            );
            mListItems = new ArrayList<>(mTrustedAps.size() + 1);
            WifiInfo wifiInfo = NetUtils.getWifiInfo();
            boolean hasCurrent = false;
            for (String ap : mTrustedAps) {
                final String[] s = Utils.splitByLastChar(ap, ',');
                boolean isCurrent = (wifiInfo != null && StringUtils.strip(wifiInfo.getSSID(), "\"").equals(s[0]) && wifiInfo.getBSSID().equals(s[1]));
                hasCurrent |= isCurrent;
                ListItem item = createListItem(s[0], s[1], isCurrent, true);
                mListItems.add(item);
            }
            if (wifiInfo != null && !hasCurrent) {
                ListItem item = createListItem(StringUtils.strip(wifiInfo.getSSID(), "\""), wifiInfo.getBSSID(), true, false);
                mListItems.add(item);
            }
        }

        private ListItem createListItem(final String ssid, final String bssid, boolean isCurrent, boolean isChecked) {
            ListItem item = new ListItem();
            item.mData = new Pair<>(ssid, bssid);
            item.mIcon = mActivity.getResources().getDrawable(R.drawable.ic_wifi);
            item.mTitle = ssid + (isCurrent ? (" (" + mActivity.getString(R.string.current) + ")") : "");
            item.mDescription = bssid;
            item.mChecked = isChecked;
            return item;
        }

        @Override
        protected String getActivityTitle() {
            return mActivity.getString(R.string.pref_trust_agent_wifi);
        }

        @Override
        protected String[] getTabTitles() {
            return new String[] { mActivity.getString(R.string.list) };
        }

        @Override
        protected int getListItemCount(int tab) {
            return mListItems.size();
        }

        @Override
        protected ListItem getListItem(int tab, int position) {
            return mListItems.get(position);
        }

        @Override
        protected boolean reload() {
            return false;
        }

        @Override
        public void onCheckedChanged(ListItem item, Boolean isChecked) {
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
        }
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
            showCustomRationaleDialog(TrustAgentWifiSettings.this, new DialogInterface.OnClickListener() {
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

    private static void showCustomRationaleDialog(TrustAgentWifiSettings context, DialogInterface.OnClickListener onConfirmListener) {
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
