package me.dreamvoid.mtweaks.xposed.system;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.util.Pair;

import li.lingfeng.ltweaks.lib.XposedLoad;
import li.lingfeng.ltweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Utils;

/**
 * Created by lilingfeng on 2017/8/15.
 */
@XposedLoad(packages = PackageNames.ANDROID_SETTINGS,
            prefs = "key_app_info_go_market")
public class XposedAppInfoGoMarket extends XposedAppInfo {

    /** Matches the wording used by Settings for opening an app page in a store. */
    private static final String MENU_VIEW_IN_MARKET = "在应用市场中查看";

    @Override
    protected Pair<String, Integer>[] newMenuNames() {
        return new Pair[] {
                Pair.create(MENU_VIEW_IN_MARKET, 1010)
        };
    }

    @Override
    protected void onMenuItemSelected(CharSequence menuName, ApplicationInfo info) {
        Activity activity = activity();
        if (activity == null || !MENU_VIEW_IN_MARKET.equals(menuName)) {
            return;
        }
        Utils.openAppInMarket(activity, info.packageName);
    }
}
