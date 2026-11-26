package me.dreamvoid.mtweaks.xposed.google.iconpack;

import android.content.Context;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.Reflect;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 访问 Pixel 启动器内部对象并触发图标重载。
 */
public final class LauncherAccess {

    private LauncherAccess() {
    }

    public static void refresh(Context context) {
        try {
            Object appState = getAppState(context);
            Object iconCache = Reflect.getObjectField(appState, "iconCache");
            synchronized (iconCache) {
                Object cache = Reflect.getObjectField(iconCache, "cache");
                if (cache instanceof Map<?, ?> map) {
                    map.clear();
                }
            }
            Object model = Reflect.getObjectField(appState, "model");
            Reflect.callMethod(model, "reloadIfActive", "mtweaks_icon_pack");
        } catch (Throwable e) {
            Logger.w("Can't refresh launcher icons: " + e);
        }
    }

    private static Object getAppState(Context context) {
        Class<?> appStateClass = Reflect.findClass("com.android.launcher3.LauncherAppState", context.getClassLoader());
        try {
            Field instance = Reflect.findField(appStateClass, "INSTANCE");
            Object singleton = instance.get(null);
            return Reflect.callMethod(singleton, "get", context);
        } catch (Throwable e) {
            return Reflect.callStaticMethod(appStateClass, "getInstance", context);
        }
    }
}
