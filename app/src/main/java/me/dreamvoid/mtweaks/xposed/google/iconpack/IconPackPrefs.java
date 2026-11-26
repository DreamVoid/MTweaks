package me.dreamvoid.mtweaks.xposed.google.iconpack;

import android.content.Context;

/**
 * 图标包选择在启动器进程中的持久化与缓存。
 */
public final class IconPackPrefs {

    private static final String PREFS_NAME = "com.android.launcher3.prefs";
    private static final String KEY = "mtweaks_icon_pack";

    private static volatile String sValue;
    private static volatile boolean sLoaded;

    private IconPackPrefs() {
    }

    public static String get(Context context) {
        if (sLoaded) {
            return sValue;
        }
        synchronized (IconPackPrefs.class) {
            if (!sLoaded) {
                String value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, "");
                sValue = value == null ? "" : value;
                sLoaded = true;
            }
            return sValue;
        }
    }

    public static void set(Context context, String value) {
        String normalized = value == null ? "" : value;
        synchronized (IconPackPrefs.class) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY, normalized).apply();
            sValue = normalized;
            sLoaded = true;
        }
    }
}
