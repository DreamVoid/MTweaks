package me.dreamvoid.mtweaks.xposed.system;

import android.content.ComponentName;
import android.os.SystemClock;
import android.service.chooser.ChooserTarget;
import io.github.libxposed.api.XposedInterface.Chain;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.prefs.ShareFilterStore;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;
import me.dreamvoid.mtweaks.xposed.core.Reflect;

import java.util.List;
import java.util.Set;

/**
 * 在系统分享面板进程内移除被隐藏的分享目标。
 */
@XposedLoad(packages = PackageNames.INTENT_RESOLVER, prefs = {}, uiKeys = "key_system_share_filter")
public class XposedShareFilterChooser extends FeatureModule {

    private static final long CACHE_TTL = 1000L;

    private volatile Set<String> mHidden = Set.of();
    private volatile long mHiddenAt = -CACHE_TTL;

    @Override
    protected void onLoad() {
        int hooks = 0;
        try {
            hooks += hookAllMethods("com.android.intentresolver.ResolverListAdapter",
                    "shouldAddResolveInfo", this::filterResolveInfo).size();
        } catch (Throwable e) {
            Logger.w("ShareFilter: ResolverListAdapter hook skipped: " + e);
        }
        try {
            hooks += hookAllMethods("com.android.intentresolver.ChooserListAdapter",
                    "addServiceResults", this::filterServiceResults).size();
        } catch (Throwable e) {
            Logger.w("ShareFilter: ChooserListAdapter hook skipped: " + e);
        }
        Logger.i("ShareFilter: " + hooks + " chooser hook(s) installed");
    }

    private Object filterResolveInfo(Chain chain) throws Throwable {
        Object result = chain.proceed();
        if (Boolean.TRUE.equals(result)) {
            try {
                Object info = chain.getArg(0);
                Object component = Reflect.callMethod(info, "getResolvedComponentName");
                if (component instanceof ComponentName resolvedComponent
                        && isHidden(resolvedComponent)) {
                    return false;
                }
            } catch (Throwable e) {
                Logger.w("ShareFilter: can't resolve chooser target: " + e);
            }
        }
        return result;
    }

    private Object filterServiceResults(Chain chain) throws Throwable {
        try {
            Object target = chain.getArg(0);
            Object targets = chain.getArg(1);
            if (target != null && targets instanceof List<?> targetList) {
                Object component = Reflect.callMethod(target, "getResolvedComponentName");
                if (component instanceof ComponentName resolvedComponent
                        && isHidden(resolvedComponent)) {
                    targetList.clear();
                    return chain.proceed();
                }
            }
            if (targets instanceof List<?> list) {
                for (int i = list.size() - 1; i >= 0; --i) {
                    if (list.get(i) instanceof ChooserTarget chooserTarget) {
                        ComponentName component = chooserTarget.getComponentName();
                        if (component != null && isHidden(component)) {
                            list.remove(i);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            Logger.w("ShareFilter: can't filter chooser targets: " + e);
        }
        return chain.proceed();
    }

    private boolean isHidden(ComponentName component) {
        return hidden().contains(component.getPackageName() + "/" + component.getClassName());
    }

    private Set<String> hidden() {
        long now = SystemClock.elapsedRealtime();
        if (now - mHiddenAt >= CACHE_TTL) {
            Set<String> hidden = ShareFilterStore.hidden();
            mHidden = hidden != null ? hidden : Set.of();
            mHiddenAt = now;
        }
        return mHidden;
    }
}
