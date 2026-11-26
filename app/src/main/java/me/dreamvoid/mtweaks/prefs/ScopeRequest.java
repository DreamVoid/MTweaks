package me.dreamvoid.mtweaks.prefs;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import io.github.libxposed.service.XposedService;
import me.dreamvoid.mtweaks.MTweaks;
import me.dreamvoid.mtweaks.R;
import me.dreamvoid.mtweaks.utils.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 在用户开启功能时向 Xposed 框架建议启用对应的作用域。
 */
public final class ScopeRequest {

    // SharedPreferences 只持有弱引用，监听器必须静态保存。
    private static final SharedPreferences.OnSharedPreferenceChangeListener LISTENER = (prefs, key) -> onPreferenceChanged(key);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private ScopeRequest() {
    }

    /**
     * 注册偏好变更监听，重复调用无副作用。
     */
    public static void start() {
        SharedPreferences prefs = Prefs.get();
        if (prefs == null) {
            return;
        }
        try {
            prefs.registerOnSharedPreferenceChangeListener(LISTENER);
        } catch (Throwable e) {
            Logger.w("Can't listen for scope changes: " + e);
        }
    }

    /**
     * 开关类功能被打开时请求作用域。
     *
     * @param key 偏好键
     */
    public static void onPreferenceChanged(String key) {
        if (key == null || !Prefs.getBoolean(key, false)) {
            return;
        }
        request(FeatureScopes.packagesFor(key));
    }

    /**
     * 点击型功能入口被打开时请求作用域。
     *
     * @param key 偏好键
     */
    public static void onEntryOpened(String key) {
        request(FeatureScopes.packagesFor(key));
    }

    /**
     * 请求框架把缺失的包加入模块作用域；已在作用域内的包会被跳过。
     *
     * @param packages 目标包名
     */
    public static void request(String... packages) {
        if (packages == null || packages.length == 0) {
            return;
        }
        XposedService service = PrefsSync.getService();
        if (service == null) {
            // 不排队补发，框架未就绪时直接放弃。
            Logger.w("Scope request skipped, Xposed service is not bound: " + String.join(", ", packages));
            return;
        }
        try {
            Set<String> granted = new HashSet<>(service.getScope());
            List<String> missing = new ArrayList<>();
            for (String packageName : packages) {
                if (packageName != null && !packageName.isEmpty() && !granted.contains(packageName) && !missing.contains(packageName)) {
                    missing.add(packageName);
                }
            }
            if (missing.isEmpty()) {
                return;
            }
            Logger.i("Requesting scope: " + missing);
            service.requestScope(missing, new XposedService.OnScopeEventListener() {
                @Override
                public void onScopeRequestApproved(List<String> approved) {
                    Logger.i("Scope approved: " + approved);
                }

                @Override
                public void onScopeRequestFailed(String message) {
                    Logger.w("Scope request failed: " + message);
                    showToast(message);
                }
            });
        } catch (Throwable e) {
            Logger.w("Scope request failed: " + e);
        }
    }

    private static void showToast(String message) {
        android.app.Application application = MTweaks.instance();
        if (application == null) {
            return;
        }
        MAIN_HANDLER.post(() -> {
            try {
                String text = application.getString(R.string.scope_request_failed, message);
                Toast.makeText(application, text, Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {
            }
        });
    }
}
