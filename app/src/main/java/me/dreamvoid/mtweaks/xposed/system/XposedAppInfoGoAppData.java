package me.dreamvoid.mtweaks.xposed.system;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Environment;
import android.util.Pair;
import android.widget.Toast;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Utils;

import java.io.File;

/**
 * Created by lilingfeng on 2017/8/15.
 */
@XposedLoad(packages = PackageNames.ANDROID_SETTINGS,
        prefs = "key_app_info_open_app_data_folder")
public class XposedAppInfoGoAppData extends XposedAppInfo {

    private static final String MENU_APP_DATA_FOLDER = "打开应用数据目录";
    private static final String MENU_APP_EXTERNAL_DATA_FOLDER = "打开应用外部存储目录";
    private static final String MENU_APP_DEVICE_ENCRYPTED_STORAGE = "打开设备加密存储目录";
    private static final String MENU_APP_APK_FOLDER = "打开 APK 所在目录";

    @Override
    protected void onLoad() throws Throwable {
        super.onLoad();
        findAndHookMethod(Uri.class, "checkFileUriExposed", new Class<?>[]{String.class}, chain -> null);
    }

    @Override
    protected Pair<String, Integer>[] newMenuNames() {
        return new Pair[]{
                Pair.create(MENU_APP_DATA_FOLDER, 1000),
                Pair.create(MENU_APP_EXTERNAL_DATA_FOLDER, 1001),
                Pair.create(MENU_APP_DEVICE_ENCRYPTED_STORAGE, 1002),
                Pair.create(MENU_APP_APK_FOLDER, 1003)
        };
    }

    @Override
    protected void onMenuItemSelected(CharSequence menuName, ApplicationInfo info) throws Throwable {
        Activity activity = activity();
        if (activity == null) {
            return;
        }
        String packageName = info.packageName;
        if (MENU_APP_DATA_FOLDER.contentEquals(menuName)) {
            Utils.openFolder(activity, info.dataDir);
        } else if (MENU_APP_EXTERNAL_DATA_FOLDER.contentEquals(menuName)) {
            String dir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/" + packageName;
            if (new File(dir).exists()) {
                Utils.openFolder(activity, dir);
            } else {
                Toast.makeText(activity, "目录不存在", Toast.LENGTH_SHORT).show();
            }
        } else if (MENU_APP_DEVICE_ENCRYPTED_STORAGE.contentEquals(menuName)) {
            Utils.openFolder(activity, "/data/user_de/0/" + packageName);
        } else if (MENU_APP_APK_FOLDER.contentEquals(menuName)) {
            Utils.openFolder(activity, new File(info.sourceDir).getParent());
        } else {
            throw new Exception("Unknown menu " + menuName);
        }
    }
}
