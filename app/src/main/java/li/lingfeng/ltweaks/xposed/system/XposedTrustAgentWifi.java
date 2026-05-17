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

        // Hook PackageManagerService.checkPermission / checkUidPermission
        hookAllMethods(ClassNames.PACKAGE_MANAGER_SERVICE, "checkPermission", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                for (int i = 0; i < param.args.length; i++) {
                    if (param.args[i] instanceof String
                            && ((String) param.args[i]).contains("PROVIDE_TRUST_AGENT")) {
                        Logger.i("Grant PROVIDE_TRUST_AGENT via checkPermission");
                        param.setResult(PackageManager.PERMISSION_GRANTED);
                        return;
                    }
                }
            }
        });
        hookAllMethods(ClassNames.PACKAGE_MANAGER_SERVICE, "checkUidPermission", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                for (int i = 0; i < param.args.length; i++) {
                    if (param.args[i] instanceof String
                            && ((String) param.args[i]).contains("PROVIDE_TRUST_AGENT")) {
                        Logger.i("Grant PROVIDE_TRUST_AGENT via checkUidPermission");
                        param.setResult(PackageManager.PERMISSION_GRANTED);
                        return;
                    }
                }
            }
        });

        // Hook TrustManagerService.resolveAllowedTrustAgents(PackageManager, int)
        // Re-add our agent if it was filtered out due to missing permission.
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

                        // Check if our trust agent is already in the allowed list
                        boolean found = false;
                        for (Object info : result) {
                            Object serviceInfo = XposedHelpers.getObjectField(info, "serviceInfo");
                            if (serviceInfo != null) {
                                String pkgName = (String) XposedHelpers.getObjectField(
                                        serviceInfo, "packageName");
                                if (PackageNames.L_TWEAKS.equals(pkgName)) {
                                    found = true;
                                    break;
                                }
                            }
                        }
                        if (!found) {
                            Logger.i("Re-adding our trust agent to allowed list");
                            // Re-query all trust agent services and add ours
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
                                Object serviceInfo = XposedHelpers.getObjectField(
                                        info, "serviceInfo");
                                if (serviceInfo != null) {
                                    String pkgName = (String) XposedHelpers.getObjectField(
                                            serviceInfo, "packageName");
                                    if (PackageNames.L_TWEAKS.equals(pkgName)) {
                                        result.add(info);
                                        Logger.i("Successfully added our trust agent");
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                        // Don't let hook failures propagate
                    }
                }
            });
        } catch (Throwable e) {
            Logger.i("TrustManagerService hook skipped: " + e.getMessage());
        }
    }
}
