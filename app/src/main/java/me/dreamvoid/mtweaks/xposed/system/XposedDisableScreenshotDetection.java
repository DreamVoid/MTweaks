package me.dreamvoid.mtweaks.xposed.system;

import android.os.Build;
import io.github.libxposed.api.XposedInterface.HookHandle;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;

import java.util.List;

/**
 * 阻止应用注册截图监听。
 */
@XposedLoad(packages = PackageNames.ANDROID, prefs = "key_capture_disable_screenshot_detection")
public class XposedDisableScreenshotDetection extends FeatureModule {

    @Override
    protected void onLoad() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Logger.w("DisableScreenshotDetection: Android 14+ required");
            return;
        }
        try {
            Class<?> cls = findClass("com.android.server.wm.ActivityTaskManagerService");
            List<HookHandle> handles = hookAllMethods(cls, "registerScreenCaptureObserver", chain -> null);
            if (handles.isEmpty()) {
                Logger.w("DisableScreenshotDetection: registerScreenCaptureObserver not found");
            } else {
                Logger.i("DisableScreenshotDetection: hooked " + handles.size() + " method(s)");
            }
        } catch (Throwable e) {
            Logger.w("DisableScreenshotDetection: can't hook registerScreenCaptureObserver: " + e);
        }
    }
}
