package me.dreamvoid.mtweaks.xposed.core;

import java.util.List;

import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedInterface.Hooker;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.Prefs;
import me.dreamvoid.mtweaks.utils.Logger;

/**
 * Base class for every feature, replacing the legacy {@code XposedBase}.
 * <p>
 * Feature classes are annotated with {@link XposedLoad} and instantiated by
 * {@link MTweaksModule}. Hooks use the libxposed API 102 interceptor chain directly.
 */
public abstract class FeatureModule {

    private ModuleContext mContext;

    public final void load(ModuleContext context) {
        mContext = context;
        XposedLoad load = getClass().getAnnotation(XposedLoad.class);
        if (isEnabledByPrefs(load)) {
            runOnLoad();
        }
    }

    private void runOnLoad() {
        try {
            Logger.i("Load " + getClass().getSimpleName() + " for " + mContext.packageName());
            onLoad();
        } catch (Throwable e) {
            Logger.e("Failed to load " + getClass().getName(), e);
        }
    }

    private boolean isEnabledByPrefs(XposedLoad load) {
        if (load == null || load.prefs().length == 0) {
            return true;
        }
        for (String pref : load.prefs()) {
            if (!pref.isEmpty() && Prefs.getBoolean(pref, false)) {
                return true;
            }
        }
        return false;
    }

    protected abstract void onLoad() throws Throwable;

    protected final ModuleContext context() {
        return mContext;
    }

    protected final ClassLoader loader() {
        return mContext.classLoader();
    }

    protected Class<?> findClass(String name) {
        return Reflect.findClass(name, loader());
    }

    // ---------------------------------------------------------------- hooks

    protected HookHandle findAndHookMethod(Class<?> cls, String methodName,
                                           Class<?>[] parameterTypes, Hooker hooker) {
        return Hooks.findAndHookMethod(cls, methodName, parameterTypes, hooker);
    }

    protected HookHandle findAndHookMethod(String className, String methodName,
                                           Class<?>[] parameterTypes, Hooker hooker) {
        return Hooks.findAndHookMethod(findClass(className), methodName, parameterTypes, hooker);
    }

    protected HookHandle findAndHookConstructor(String className, Class<?>[] parameterTypes,
                                                Hooker hooker) {
        return Hooks.findAndHookConstructor(findClass(className), parameterTypes, hooker);
    }

    protected List<HookHandle> hookAllMethods(Class<?> cls, String methodName, Hooker hooker) {
        return Hooks.hookAllMethods(cls, methodName, hooker);
    }

    protected List<HookHandle> hookAllMethods(String className, String methodName, Hooker hooker) {
        return Hooks.hookAllMethods(findClass(className), methodName, hooker);
    }
}
