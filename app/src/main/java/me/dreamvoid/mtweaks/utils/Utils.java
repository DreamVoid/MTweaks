package me.dreamvoid.mtweaks.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Pair;
import android.widget.Toast;
import me.dreamvoid.mtweaks.MyApplication;
import me.dreamvoid.mtweaks.xposed.core.Reflect;

import java.util.Arrays;
import java.util.Map;

/**
 * Small helpers shared by the module app and the Xposed hooks.
 */
public final class Utils {

    private Utils() {
    }

    /**
     * Splits a value at the last occurrence of a separator; returns the value itself when the
     * separator is missing or at an edge.
     */
    public static String[] splitByLastChar(String value, char separator) {
        if (value == null) {
            return new String[0];
        }
        int index = value.lastIndexOf(separator);
        if (index <= 0 || index >= value.length() - 1) {
            return new String[]{value};
        }
        return new String[]{value.substring(0, index), value.substring(index + 1)};
    }

    public static boolean pairContains(Pair<?, ?>[] pairs, Object o, boolean isFirst) {
        return Arrays.stream(pairs)
                .anyMatch(pair -> isFirst ? pair.first.equals(o) : pair.second.equals(o));
    }

    /**
     * Returns the currently resumed activity of the hooked process, if any.
     */
    public static Activity getTopActivity() {
        try {
            Class<?> activityThread = Reflect.findClass("android.app.ActivityThread", null);
            Object thread = Reflect.callStaticMethod(activityThread, "currentActivityThread");
            if (thread == null) {
                return null;
            }
            Object activities = Reflect.getObjectField(thread, "mActivities");
            if (!(activities instanceof Map<?, ?> activityMap)) {
                return null;
            }
            Activity fallback = null;
            for (Object record : activityMap.values()) {
                if (record == null) {
                    continue;
                }
                Object activity = Reflect.getObjectField(record, "activity");
                if (!(activity instanceof Activity candidate)) {
                    continue;
                }
                if (candidate.isFinishing() || candidate.isDestroyed()) {
                    continue;
                }
                if (fallback == null) {
                    fallback = candidate;
                }
                try {
                    if (!Reflect.getBooleanField(record, "paused")) {
                        return candidate;
                    }
                } catch (Throwable ignored) {
                }
            }
            return fallback;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String getString(String name) {
        Context context = MyApplication.instance();
        return context.getString(context.getResources()
                .getIdentifier(name, "string", context.getPackageName()));
    }

    public static void openFolder(Context context, String path) {
        Logger.v("openFolder " + path);
        Uri uri = Uri.parse("file://" + path);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "resource/folder");
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        } else {
            Toast.makeText(context, "Error.", Toast.LENGTH_SHORT).show();
        }
    }

    public static void openAppInMarket(Activity activity, String app) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("market://details?id=" + app));
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            // No app store installed; ignore silently.
        }
    }
}
