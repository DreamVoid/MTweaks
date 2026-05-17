package li.lingfeng.ltweaks.xposed.system;

import android.content.pm.PackageManager;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import li.lingfeng.ltweaks.lib.XposedLoad;
import li.lingfeng.ltweaks.prefs.PackageNames;
import li.lingfeng.ltweaks.utils.Logger;
import li.lingfeng.ltweaks.xposed.XposedBase;

/**
 * ADDED FOR NEW ANDROID: Hooks into Settings app (com.android.settings)
 * to bypass the TrustAgentManager permission check for our app.
 *
 * On Android 15+, TrustAgentManager in the Settings process checks
 * PROVIDE_TRUST_AGENT permission through a path that doesn't go through
 * PackageManagerService.checkPermission() in system_server.
 * This module catches the check directly in the Settings process.
 */
@XposedLoad(packages = PackageNames.ANDROID_SETTINGS, prefs = {})
public class XposedTrustAgentWifiSettings extends XposedBase {

    @Override
    protected void handleLoadPackage() throws Throwable {
        Logger.i("XposedTrustAgentWifiSettings loaded in Settings");

        // Hook the Settings' TrustAgentManager class to skip permission checks for our app.
        // Try common class names for different Android versions.
        String[] possibleClasses = {
            "com.android.settings.TrustAgentManager",
            "com.android.settings.trustagent.TrustAgentManager",
            "com.android.settings.trust.TrustAgentManager",
            "com.android.settings.security.TrustAgentManager"
        };

        for (String className : possibleClasses) {
            try {
                Class<?> trustAgentMgr = XposedHelpers.findClass(className, null);
                // Hook all methods that might check trust agent permission
                String[] methods = {"checkTrustAgent", "isTrustAgent", "checkAgentPermission",
                        "getTrustAgent", "loadTrustAgents", "addTrustAgent"};
                for (String method : methods) {
                    try {
                        hookAllMethods(trustAgentMgr, method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Logger.i("TrustAgentManager." + method + "() called in Settings");
                                // Check if any arg is our package name - if so, grant permission
                                for (int i = 0; i < param.args.length; i++) {
                                    if (param.args[i] instanceof String
                                            && ((String) param.args[i]).contains(PackageNames.L_TWEAKS)) {
                                        Logger.i("TrustAgentManager found our package, granting access");
                                        return; // Allow the original method to proceed normally
                                    }
                                }
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                // If the method returned false (denied) and it's about our app,
                                // override to true (granted)
                                if (param.getResult() instanceof Boolean && !((Boolean) param.getResult())) {
                                    // Check if any arg is our package
                                    for (int i = 0; i < param.args.length; i++) {
                                        if (param.args[i] instanceof String
                                                && ((String) param.args[i]).contains(PackageNames.L_TWEAKS)) {
                                            Logger.i("TrustAgentManager denied our app, overriding to granted");
                                            param.setResult(true);
                                            return;
                                        }
                                    }
                                }
                            }
                        });
                    } catch (Throwable ignored) {
                        // Method not found on this version, try next
                    }
                }
                Logger.i("TrustAgentManager hooked in Settings: " + className);
                break; // Found and hooked, no need to try other class names
            } catch (Throwable ignored) {
                // Class not found on this version, try next class name
            }
        }

        // ADDED FOR NEW ANDROID: Hook ApplicationPackageManager.checkPermission
        // (concrete implementation of PackageManager) in the Settings process.
        try {
            Class<?> appPkgMgr = XposedHelpers.findClass(
                    "android.app.ApplicationPackageManager", null);
            hookAllMethods(appPkgMgr, "checkPermission", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    for (int i = 0; i < param.args.length; i++) {
                        if (param.args[i] instanceof String
                                && ((String) param.args[i]).contains("PROVIDE_TRUST_AGENT")) {
                            Logger.i("Grant PROVIDE_TRUST_AGENT in Settings process");
                            param.setResult(PackageManager.PERMISSION_GRANTED);
                            return;
                        }
                    }
                }
            });
            Logger.i("ApplicationPackageManager.checkPermission hooked in Settings");
        } catch (Throwable e) {
            Logger.i("Could not hook ApplicationPackageManager: " + e.getMessage());
        }
    }
}
