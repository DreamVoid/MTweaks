package me.dreamvoid.mtweaks.xposed.system;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

import io.github.libxposed.api.XposedInterface.HookHandle;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;

/**
 * Makes the Settings app show this module in "Trust agents" and accept it.
 */
@XposedLoad(packages = PackageNames.ANDROID_SETTINGS, prefs = {})
public class XposedTrustAgentWifiSettings extends FeatureModule {

    private static final String PERMISSION = "android.permission.PROVIDE_TRUST_AGENT";

    private static final String[] TRUST_AGENT_MANAGER_CLASSES = {
            "com.android.settings.security.trustagent.TrustAgentManager",
            "com.android.settings.trustagent.TrustAgentManager",
            "com.android.settings.trust.TrustAgentManager"
    };

    private static final String[] TRUST_AGENT_METHODS = {
            "shouldProvideTrust",
            "checkTrustAgent",
            "isTrustAgent",
            "checkAgentPermission"
    };

    @Override
    protected void onLoad() {
        if (!hookTrustAgentManager()) {
            Logger.w("TrustAgentManager not found, relying on checkPermission fallback");
        }
        hookApplicationPackageManager();
    }

    private boolean hookTrustAgentManager() {
        for (String className : TRUST_AGENT_MANAGER_CLASSES) {
            Class<?> cls;
            try {
                cls = findClass(className);
            } catch (Throwable e) {
                continue;
            }
            for (String methodName : TRUST_AGENT_METHODS) {
                try {
                    List<HookHandle> handles = hookAllMethods(cls, methodName, chain -> {
                        if (isOurAgent(chain.getArg(0))) {
                            return true;
                        }
                        return chain.proceed();
                    });
                    if (!handles.isEmpty()) {
                        Logger.i(className + "#" + methodName + " hooked (" + handles.size() + ")");
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private void hookApplicationPackageManager() {
        try {
            // Hidden framework class, resolved by name to keep the module compiling against the SDK.
            Class<?> cls = findClass("android.app.ApplicationPackageManager");
            List<HookHandle> handles = hookAllMethods(cls, "checkPermission", chain -> {
                for (Object arg : chain.getArgs()) {
                    if (PERMISSION.equals(arg)) {
                        return PackageManager.PERMISSION_GRANTED;
                    }
                }
                return chain.proceed();
            });
            if (!handles.isEmpty()) {
                Logger.i("ApplicationPackageManager#checkPermission hooked in Settings ("
                        + handles.size() + ")");
            }
        } catch (Throwable e) {
            Logger.w("Settings checkPermission hook failed: " + e);
        }
    }

    private static boolean isOurAgent(Object arg) {
        if (!(arg instanceof ResolveInfo info)) {
            return false;
        }
        return info.serviceInfo != null
                && PackageNames.M_TWEAKS.equals(info.serviceInfo.packageName);
    }
}
