package me.dreamvoid.mtweaks.prefs;

import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import me.dreamvoid.mtweaks.utils.Logger;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors the module app's preferences into the Xposed framework so that hooked processes can read
 * them through {@code XposedInterface.getRemotePreferences()}.
 */
public final class PrefsSync {

    private static final Object LOCK = new Object();
    private static final SharedPreferences.OnSharedPreferenceChangeListener LISTENER = (sharedPreferences, key) -> push(key, sharedPreferences);
    private static XposedService sService;
    private static boolean sStarted;

    private PrefsSync() {
    }

    public static void start() {
        synchronized (LOCK) {
            if (sStarted) {
                return;
            }
            sStarted = true;
        }
        try {
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override
                public void onServiceBind(XposedService service) {
                    synchronized (LOCK) {
                        sService = service;
                    }
                    pushAll();
                }

                @Override
                public void onServiceDied(XposedService service) {
                    synchronized (LOCK) {
                        if (sService == service) {
                            sService = null;
                        }
                    }
                }
            });
        } catch (Throwable e) {
            Logger.w("Xposed service is unavailable: " + e);
        }

        SharedPreferences local = Prefs.get();
        if (local != null) {
            local.registerOnSharedPreferenceChangeListener(LISTENER);
        }
    }

    /**
     * 当前已绑定的 Xposed 服务，未绑定时返回 {@code null}。
     */
    public static XposedService getService() {
        synchronized (LOCK) {
            return sService;
        }
    }

    private static void pushAll() {
        SharedPreferences local = Prefs.get();
        if (local == null) {
            return;
        }
        try {
            for (Map.Entry<String, ?> entry : local.getAll().entrySet()) {
                push(entry.getKey(), entry.getValue());
            }
        } catch (Throwable e) {
            Logger.w("pushAll failed: " + e);
        }
        try {
            ShareFilterStore.persistFromPrefs();
        } catch (Throwable e) {
            Logger.w("pushAll files failed: " + e);
        }
    }

    /**
     * 写入模块共享数据目录中的文件，供 hook 进程按需读取；失败返回 {@code false}。
     */
    public static boolean writeFile(String name, String content) {
        XposedService service;
        synchronized (LOCK) {
            service = sService;
        }
        if (service == null || name == null || content == null) {
            return false;
        }
        try {
            // 先删除以免旧内容残留
            service.deleteRemoteFile(name);
        } catch (Throwable ignored) {
        }
        try (ParcelFileDescriptor fd = service.openRemoteFile(name); FileOutputStream out = new FileOutputStream(fd.getFileDescriptor())) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (Throwable e) {
            Logger.w("Failed to write remote file " + name + ": " + e);
            return false;
        }
    }

    private static void push(String key, SharedPreferences local) {
        if (key == null || local == null) {
            return;
        }
        try {
            push(key, local.getAll().get(key));
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void push(String key, Object value) {
        XposedService service;
        synchronized (LOCK) {
            service = sService;
        }
        if (service == null || key == null) {
            return;
        }
        try {
            SharedPreferences remote = service.getRemotePreferences(Prefs.REMOTE_GROUP);
            SharedPreferences.Editor editor = remote.edit();
            if (value == null) {
                editor.remove(key);
            } else if (value instanceof Boolean boolValue) {
                editor.putBoolean(key, boolValue);
            } else if (value instanceof String stringValue) {
                editor.putString(key, stringValue);
            } else if (value instanceof Set<?> setValue) {
                editor.putStringSet(key, (Set<String>) setValue);
            } else if (value instanceof Integer intValue) {
                editor.putInt(key, intValue);
            } else if (value instanceof Long longValue) {
                editor.putLong(key, longValue);
            } else if (value instanceof Float floatValue) {
                editor.putFloat(key, floatValue);
            }
            editor.apply();
        } catch (Throwable e) {
            Logger.w("Failed to mirror preference " + key + ": " + e);
        }
    }
}
