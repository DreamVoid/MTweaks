package me.dreamvoid.mtweaks.xposed.system;

import android.os.Build;
import io.github.libxposed.api.XposedInterface.HookHandle;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;

import java.util.List;

/**
 * 阻止应用注册录屏监听。
 */
@XposedLoad(packages = PackageNames.ANDROID, prefs = "key_capture_disable_screen_recording_detection")
public class XposedDisableScreenRecordingDetection extends FeatureModule {

    @Override
    protected void onLoad() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            Logger.w("DisableScreenRecordingDetection: Android 15+ required");
            return;
        }
        try {
            Class<?> cls = findClass("com.android.server.wm.WindowManagerService");
            List<HookHandle> handles = hookAllMethods(cls, "registerScreenRecordingCallback", chain -> false);
            if (handles.isEmpty()) {
                Logger.w("DisableScreenRecordingDetection: registerScreenRecordingCallback not found");
            } else {
                Logger.i("DisableScreenRecordingDetection: hooked " + handles.size() + " method(s)");
            }
        } catch (Throwable e) {
            Logger.w("DisableScreenRecordingDetection: can't hook registerScreenRecordingCallback: " + e);
        }
    }
}
