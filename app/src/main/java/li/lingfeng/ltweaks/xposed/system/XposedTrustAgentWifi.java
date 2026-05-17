package li.lingfeng.ltweaks.xposed.system;

import android.content.pm.PackageManager;
import android.os.Build;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import li.lingfeng.ltweaks.lib.XposedLoad;
import li.lingfeng.ltweaks.prefs.ClassNames;
import li.lingfeng.ltweaks.prefs.PackageNames;
import li.lingfeng.ltweaks.utils.Logger;
import li.lingfeng.ltweaks.xposed.XposedBase;

/**
 * Created by lilingfeng on 2017/7/5.
 * Updated for Android 14+ compatibility.
 */
@XposedLoad(packages = PackageNames.ANDROID, prefs = {})
public class XposedTrustAgentWifi extends XposedBase {

    @Override
    protected void handleLoadPackage() throws Throwable {
        Logger.i("XposedTrustAgentWifi loaded on SDK " + Build.VERSION.SDK_INT);
        ClassLoader sysLoader = Thread.currentThread().getContextClassLoader();

        // Hook PackageManagerService.checkPermission (may not catch all paths)
        try {
            hookAllMethods(ClassNames.PACKAGE_MANAGER_SERVICE, "checkPermission", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    for (int i = 0; i < param.args.length; i++) {
                        if (param.args[i] instanceof String
                                && ((String) param.args[i]).contains("PROVIDE_TRUST_AGENT")) {
                            Logger.i("Grant via PMS.checkPermission");
                            param.setResult(PackageManager.PERMISSION_GRANTED);
                            return;
                        }
                    }
                }
            });
        } catch (Throwable e) { Logger.i("PMS hook: " + e.getMessage()); }

        // ApplicationPackageManager is the concrete class for getPackageManager().
        // Hook its checkPermission(String, String) so TrustManagerService resolves our agent.
        try {
            Class<?> apmClass = XposedHelpers.findClass(
                    "android.app.ApplicationPackageManager", sysLoader);
            hookAllMethods(apmClass, "checkPermission", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length < 2) return;
                    for (int i = 0; i < param.args.length; i++) {
                        if (param.args[i] instanceof String
                                && ((String) param.args[i]).contains("PROVIDE_TRUST_AGENT")) {
                            Logger.i("Grant via APM.checkPermission");
                            param.setResult(PackageManager.PERMISSION_GRANTED);
                            return;
                        }
                    }
                }
            });
        } catch (Throwable e) { Logger.i("APM hook: " + e.getMessage()); }

        // Hook TrustManagerService.resolveAllowedTrustAgents as backup;
        // if APM hook works, this will never add our agent (already present).
        try {
            Class<?> tmsClass = XposedHelpers.findClass(
                    "com.android.server.trust.TrustManagerService", sysLoader);
            hookAllMethods(tmsClass, "resolveAllowedTrustAgents", new XC_MethodHook() {
                @Override
                @SuppressWarnings("unchecked")
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        java.util.List<Object> result = (java.util.List<Object>) param.getResult();
                        if (result == null) return;
                        boolean found = false;
                        for (Object info : result) {
                            Object si = XposedHelpers.getObjectField(info, "serviceInfo");
                            if (si != null && PackageNames.L_TWEAKS.equals(
                                    XposedHelpers.getObjectField(si, "packageName"))) {
                                found = true; break;
                            }
                        }
                        if (!found) {
                            Logger.i("Adding agent via afterHookedMethod (APM hook missed)");
                            PackageManager pm = (PackageManager) param.args[0];
                            android.content.Intent intent = new android.content.Intent(
                                    "android.service.trust.TrustAgentService");
                            java.util.List<Object> all = (java.util.List<Object>)
                                    XposedHelpers.callMethod(pm, "queryIntentServicesAsUser",
                                            intent,
                                            PackageManager.GET_META_DATA
                                                    | PackageManager.MATCH_DIRECT_BOOT_AWARE
                                                    | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                                            param.args[1]);
                            for (Object info : all) {
                                Object si = XposedHelpers.getObjectField(info, "serviceInfo");
                                if (si != null && PackageNames.L_TWEAKS.equals(
                                        XposedHelpers.getObjectField(si, "packageName"))) {
                                    result.add(info);
                                    Logger.i("Agent added via backup path");
                                    break;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable e) {
            Logger.i("TMS hook: " + e.getMessage());
        }
    }
}
