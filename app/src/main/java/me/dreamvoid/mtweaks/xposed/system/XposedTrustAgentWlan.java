package me.dreamvoid.mtweaks.xposed.system;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import io.github.libxposed.api.XposedInterface.HookHandle;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;
import me.dreamvoid.mtweaks.xposed.core.Reflect;

import java.util.List;

/**
 * Grants {@code PROVIDE_TRUST_AGENT} to this module inside system_server and makes sure the trust
 * agent service survives {@code TrustManagerService#resolveAllowedTrustAgents}.
 */
@XposedLoad(packages = PackageNames.ANDROID, prefs = {})
public class XposedTrustAgentWlan extends FeatureModule {

    private static final String PERMISSION = "android.permission.PROVIDE_TRUST_AGENT";
    private static final String SERVICE_ACTION = "android.service.trust.TrustAgentService";

    // PackageManager.MATCH_DIRECT_BOOT_AWARE / MATCH_DIRECT_BOOT_UNAWARE are hidden constants.
    private static final long MATCH_FLAGS =
            PackageManager.GET_META_DATA | 0x00000040L | 0x00000080L;

    @Override
    protected void onLoad() {
        hookCheckPermission("com.android.server.pm.PackageManagerService");
        hookCheckPermission("android.app.ApplicationPackageManager");
        hookCheckPermission("com.android.server.pm.permission.PermissionManagerService");
        hookCheckPermission("com.android.server.pm.permission.PermissionManagerServiceImpl");
        hookResolveAllowedTrustAgents();
    }

    private void hookCheckPermission(String className) {
        try {
            Class<?> cls = findClass(className);
            List<HookHandle> handles = hookAllMethods(cls, "checkPermission", chain -> {
                for (Object arg : chain.getArgs()) {
                    if (PERMISSION.equals(arg)) {
                        return PackageManager.PERMISSION_GRANTED;
                    }
                }
                return chain.proceed();
            });
            if (!handles.isEmpty()) {
                Logger.i(className + "#checkPermission hooked (" + handles.size() + ")");
            }
        } catch (Throwable e) {
            Logger.d("checkPermission hook skipped for " + className + ": " + e);
        }
    }

    @SuppressWarnings("unchecked")
    private void hookResolveAllowedTrustAgents() {
        try {
            Class<?> cls = findClass("com.android.server.trust.TrustManagerService");
            List<HookHandle> handles = hookAllMethods(cls, "resolveAllowedTrustAgents", chain -> {
                Object result = chain.proceed();
                if (!(result instanceof List<?> rawAgents)) {
                    return result;
                }
                List<ResolveInfo> agents = (List<ResolveInfo>) rawAgents;
                if (containsOurAgent(agents)) {
                    return result;
                }
                PackageManager packageManager = null;
                int userId = 0;
                for (Object arg : chain.getArgs()) {
                    if (packageManager == null && arg instanceof PackageManager pmArg) {
                        packageManager = pmArg;
                    }
                    if (arg instanceof Integer intArg) {
                        userId = intArg;
                    }
                }
                if (packageManager == null) {
                    Logger.w("resolveAllowedTrustAgents: package manager argument not found");
                    return result;
                }
                ResolveInfo ours = findOurAgent(packageManager, userId);
                if (ours != null) {
                    agents.add(ours);
                    Logger.i("Injected trust agent into resolveAllowedTrustAgents, userId=" + userId);
                }
                return result;
            });
            if (!handles.isEmpty()) {
                Logger.i("TrustManagerService#resolveAllowedTrustAgents hooked (" + handles.size() + ")");
            }
        } catch (Throwable e) {
            Logger.w("resolveAllowedTrustAgents hook failed: " + e);
        }
    }

    private static boolean containsOurAgent(List<ResolveInfo> agents) {
        return agents != null && agents.stream().anyMatch(XposedTrustAgentWlan::isOurAgent);
    }

    private static boolean isOurAgent(ResolveInfo info) {
        return info != null && info.serviceInfo != null
                && PackageNames.M_TWEAKS.equals(info.serviceInfo.packageName);
    }

    private ResolveInfo findOurAgent(PackageManager packageManager, int userId) {
        try {
            Intent intent = new Intent(SERVICE_ACTION);
            List<ResolveInfo> candidates;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    candidates = (List<ResolveInfo>) Reflect.callMethod(packageManager,
                            "queryIntentServicesAsUser", intent,
                            PackageManager.ResolveInfoFlags.of(MATCH_FLAGS), userId);
                } else {
                    candidates = (List<ResolveInfo>) Reflect.callMethod(packageManager,
                            "queryIntentServicesAsUser", intent, (int) MATCH_FLAGS, userId);
                }
            } catch (Throwable e) {
                // queryIntentServicesAsUser is a hidden API; fall back to the public variant.
                candidates = packageManager.queryIntentServices(intent, (int) MATCH_FLAGS);
            }
            if (candidates == null) {
                return null;
            }
            return candidates.stream()
                    .filter(XposedTrustAgentWlan::isOurAgent)
                    .findFirst()
                    .orElse(null);
        } catch (Throwable e) {
            Logger.w("queryIntentServicesAsUser failed: " + e);
        }
        return null;
    }
}
