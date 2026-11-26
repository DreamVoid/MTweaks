package me.dreamvoid.mtweaks.xposed.google.iconpack;

import android.content.Context;
import android.content.res.Resources;
import me.dreamvoid.mtweaks.prefs.PackageNames;

/**
 * 从模块资源读取界面文本，失败时使用中文回退值。
 */
public final class ModuleStrings {

    private ModuleStrings() {
    }

    public static String get(Context context, int resourceId, String fallback) {
        try {
            Context module = context.createPackageContext(PackageNames.M_TWEAKS, Context.CONTEXT_IGNORE_SECURITY);
            Resources resources = module.getResources();
            return resources.getString(resourceId);
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
