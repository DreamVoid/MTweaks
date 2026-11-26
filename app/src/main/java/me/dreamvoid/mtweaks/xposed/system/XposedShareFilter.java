package me.dreamvoid.mtweaks.xposed.system;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import io.github.libxposed.api.XposedInterface.Chain;
import li.lingfeng.ltweaks.lib.XposedLoad;
import li.lingfeng.ltweaks.prefs.IntentActions;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.prefs.ShareFilterStore;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;
import me.dreamvoid.mtweaks.xposed.core.Hooks;
import me.dreamvoid.mtweaks.xposed.core.Reflect;
import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 从分享意图查询结果中移除被隐藏的分享入口。
 */
@XposedLoad(packages = PackageNames.ANDROID, prefs = {})
public class XposedShareFilter extends FeatureModule {

    private static final String CLASS_PARCELED_LIST_SLICE = "android.content.pm.ParceledListSlice";
    private static final String METHOD_INTERNAL = "queryIntentActivitiesInternal";
    private static final String[] INTERNAL_CLASSES = {
            "com.android.server.pm.ComputerEngine",
            "com.android.server.pm.ComputerLocked",
            "com.android.server.pm.PackageManagerService"
    };
    private static final String[] BINDER_CLASSES = {
            "com.android.server.pm.IPackageManagerBase",
            "com.android.server.pm.PackageManagerService$IPackageManagerImpl"
    };
    private static final String[] BINDER_METHODS = {
            "queryIntentActivities",
            "queryIntentActivitiesAsUser"
    };

    private volatile Context mContext;
    private volatile int mSelfUid = -1;

    @Override
    protected void onLoad() {
        int hooks = 0;
        for (String className : INTERNAL_CLASSES) {
            hooks += hookQueryMethods(className, METHOD_INTERNAL);
        }
        for (String className : BINDER_CLASSES) {
            for (String methodName : BINDER_METHODS) {
                hooks += hookQueryMethods(className, methodName);
            }
        }
        if (hooks == 0) {
            Logger.w("ShareFilter: no intent query hook installed");
        } else {
            Logger.i("ShareFilter: " + hooks + " intent query hook(s) installed");
        }
        hookSystemContext();
    }

    private int hookQueryMethods(String className, String methodName) {
        Class<?> cls;
        try {
            cls = findClass(className);
        } catch (Throwable ignored) {
            return 0;
        }
        int count = 0;
        for (Method method : cls.getDeclaredMethods()) {
            if (!method.getName().equals(methodName)
                    || Modifier.isAbstract(method.getModifiers())
                    || method.isBridge() || method.isSynthetic()) {
                continue;
            }
            try {
                Hooks.hook(method, this::handleQuery);
                ++count;
            } catch (Throwable e) {
                Logger.w("ShareFilter: can't hook " + className + "#" + methodName + ": " + e);
            }
        }
        return count;
    }

    private Object handleQuery(Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            Intent intent = null;
            List<Object> args = chain.getArgs();
            if (!args.isEmpty() && args.get(0) instanceof Intent argIntent) {
                intent = argIntent;
            }
            if (intent == null || !ArrayUtils.contains(IntentActions.sSendActions, intent.getAction())
                    || isSelfCaller()) {
                return result;
            }
            Set<String> hidden = ShareFilterStore.hidden();
            if (hidden == null || hidden.isEmpty()) {
                return result;
            }
            return filterResult(result, hidden);
        } catch (Throwable e) {
            Logger.w("ShareFilter: filter failed: " + e);
            return result;
        }
    }

    private Object filterResult(Object result, Set<String> hidden) {
        if (result instanceof List<?> list) {
            List<ResolveInfo> filtered = filterList(list, hidden);
            return filtered != null ? filtered : result;
        }
        if (result != null && result.getClass().getName().equals(CLASS_PARCELED_LIST_SLICE)) {
            try {
                Object rawList = Reflect.callMethod(result, "getList");
                if (rawList instanceof List<?> innerList) {
                    List<ResolveInfo> filtered = filterList(innerList, hidden);
                    if (filtered != null) {
                        return Reflect.newInstance(findClass(CLASS_PARCELED_LIST_SLICE), filtered);
                    }
                }
            } catch (Throwable e) {
                Logger.w("ShareFilter: can't filter parceled list: " + e);
            }
        }
        return result;
    }

    private List<ResolveInfo> filterList(List<?> list, Set<String> hidden) {
        List<ResolveInfo> filtered = null;
        for (int i = 0; i < list.size(); ++i) {
            Object raw = list.get(i);
            if (!(raw instanceof ResolveInfo info)) {
                return null;
            }
            ActivityInfo activityInfo = info.activityInfo;
            boolean hide = activityInfo != null
                    && hidden.contains(activityInfo.packageName + "/" + activityInfo.name);
            if (hide) {
                if (filtered == null) {
                    filtered = new ArrayList<>(list.size());
                    for (int j = 0; j < i; ++j) {
                        filtered.add((ResolveInfo) list.get(j));
                    }
                }
            } else if (filtered != null) {
                filtered.add(info);
            }
        }
        if (filtered != null) {
            Logger.d("ShareFilter: removed " + (list.size() - filtered.size()) + " share target(s)");
        }
        return filtered;
    }

    private boolean isSelfCaller() {
        int selfUid = mSelfUid;
        if (selfUid == -1) {
            selfUid = resolveSelfUid();
            mSelfUid = selfUid;
        }
        return selfUid > 0 && Binder.getCallingUid() == selfUid;
    }

    private int resolveSelfUid() {
        Context context = getSystemContext();
        if (context == null) {
            return -1;
        }
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(PackageNames.M_TWEAKS, 0);
            return info.uid;
        } catch (Throwable e) {
            return -1;
        }
    }

    private Context getSystemContext() {
        Context context = mContext;
        if (context != null) {
            return context;
        }
        context = readSystemContext();
        if (context != null) {
            mContext = context;
        }
        return context;
    }

    private Context readSystemContext() {
        try {
            Class<?> activityThread = findClass("android.app.ActivityThread");
            Object thread = Reflect.callStaticMethod(activityThread, "currentActivityThread");
            Object context = Reflect.callMethod(thread, "getSystemContext");
            if (context instanceof Context systemContext) {
                return systemContext;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> globals = findClass("android.app.AppGlobals");
            Object app = Reflect.callStaticMethod(globals, "getInitialApplication");
            if (app instanceof Context initialApplication) {
                return initialApplication;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void hookSystemContext() {
        try {
            findAndHookMethod("com.android.server.am.ActivityManagerService", "finishBooting",
                    new Class<?>[0], chain -> {
                        Object result = chain.proceed();
                        try {
                            Object context = Reflect.getObjectField(chain.getThisObject(), "mContext");
                            if (context instanceof Context systemContext) {
                                mContext = systemContext;
                            }
                        } catch (Throwable ignored) {
                        }
                        return result;
                    });
        } catch (Throwable e) {
            Logger.w("ShareFilter: system context hook skipped: " + e);
        }
    }
}
