package li.lingfeng.ltweaks.xposed.entertainment;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;
import android.widget.Toast;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import me.dreamvoid.mtweaks.R;
import li.lingfeng.ltweaks.lib.XposedLoad;
import li.lingfeng.ltweaks.prefs.PackageNames;
import li.lingfeng.ltweaks.utils.ContextUtils;
import li.lingfeng.ltweaks.utils.Logger;
import li.lingfeng.ltweaks.utils.ShareUtils;
import li.lingfeng.ltweaks.xposed.XposedBase;

/**
 * Created by smallville on 2017/6/24.
 */
@XposedLoad(packages = PackageNames.STEAM, prefs = "key_steam_share_url")
public class XposedSteamShare extends XposedSteam {

    @Override
    protected String newMenuName() {
        return "Share";
    }

    @Override
    protected int newMenuPriority() {
        return 3;
    }

    @Override
    protected int newMenuShowAsAction() {
        return MenuItem.SHOW_AS_ACTION_NEVER;
    }

    @Override
    protected void menuItemSelected() throws Throwable {
        String url = getUrl();
        if (url != null) {
            ShareUtils.shareText(mActivity, url);
        }
    }
}
