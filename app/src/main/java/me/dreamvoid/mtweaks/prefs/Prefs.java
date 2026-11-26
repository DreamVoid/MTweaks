package me.dreamvoid.mtweaks.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import io.github.libxposed.api.XposedInterface;
import me.dreamvoid.mtweaks.utils.Logger;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Preference access for both the module app process and hooked processes.
 * <p>
 * In the module process preferences live in device-protected private storage; in hooked processes
 * they are read through the Xposed framework's remote preferences (read-only).
 */
public final class Prefs {

    public static final String REMOTE_GROUP = "mtweaks";

    private static volatile SharedPreferences sPrefs;
    private static volatile Context sAppContext;
    private static volatile XposedInterface sXposed;
    private static volatile boolean sRemote;
    private static volatile boolean sWarned;

    private Prefs() {
    }

    public static void attachHookedProcess(XposedInterface xposed) {
        sXposed = xposed;
        sPrefs = xposed.getRemotePreferences(REMOTE_GROUP);
        sAppContext = null;
        sRemote = true;
        sWarned = false;
    }

    public static void attachModuleProcess(Context context) {
        Context storage = context.createDeviceProtectedStorageContext();
        sPrefs = storage.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        sAppContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        sXposed = null;
        sRemote = false;
        sWarned = false;
    }

    public static SharedPreferences get() {
        return sPrefs;
    }

    public static boolean getBoolean(String key, boolean defValue) {
        SharedPreferences prefs = sPrefs;
        if (prefs == null || key == null) {
            warnMissing();
            return defValue;
        }
        try {
            return prefs.getBoolean(key, defValue);
        } catch (Throwable e) {
            Logger.w("getBoolean(" + key + ") failed: " + e);
            return defValue;
        }
    }

    public static boolean getBoolean(int resId, boolean defValue) {
        return getBoolean(resolveKey(resId), defValue);
    }

    public static Set<String> getStringSet(int resId, Set<String> defValues) {
        return getStringSet(resolveKey(resId), defValues);
    }

    public static Set<String> getStringSet(String key, Set<String> defValues) {
        SharedPreferences prefs = sPrefs;
        if (prefs == null || key == null) {
            warnMissing();
            return defValues;
        }
        try {
            Set<String> value = prefs.getStringSet(key, defValues);
            return value == null ? defValues : new HashSet<>(value);
        } catch (Throwable e) {
            Logger.w("getStringSet(" + key + ") failed: " + e);
            return defValues;
        }
    }

    /**
     * 读取模块共享数据目录下的文本文件，每次调用都从框架重新读取，失败返回 {@code null}。
     */
    public static String readRemoteFile(String name) {
        XposedInterface xposed = sXposed;
        if (!sRemote || xposed == null || name == null) {
            return null;
        }
        try (ParcelFileDescriptor fd = xposed.openRemoteFile(name);
             FileInputStream in = new FileInputStream(fd.getFileDescriptor())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return out.toString(StandardCharsets.UTF_8);
            } else {
                //noinspection StringOperationCanBeSimplified
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Throwable e) {
            Logger.w("readRemoteFile(" + name + ") failed: " + e);
            return null;
        }
    }

    private static String resolveKey(int resId) {
        Context context = sAppContext;
        if (context != null) {
            try {
                return context.getString(resId);
            } catch (Throwable ignored) {
            }
        }
        return PrefKeys.getById(resId);
    }

    private static void warnMissing() {
        if (!sWarned) {
            sWarned = true;
            Logger.w("Preferences are not attached yet");
        }
    }
}
