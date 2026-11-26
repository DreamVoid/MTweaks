package me.dreamvoid.mtweaks.xposed.core;

import android.os.Build;
import android.os.Bundle;
import io.github.libxposed.api.XposedModule;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.Prefs;
import me.dreamvoid.mtweaks.utils.Logger;
import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * libxposed API 102 entry point, declared in {@code META-INF/xposed/java_init.list}.
 */
public class MTweaksModule extends XposedModule {

    private static final String STATE_PACKAGES = "me.dreamvoid.mtweaks.hot_reload.packages";

    private final Set<String> mLoadedPackages = new LinkedHashSet<>();
    private final List<FeatureModule> mModules = new ArrayList<>();
    private String mProcessName = "";
    private boolean mSystemServer;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        attach(param.getProcessName(), param.isSystemServer());
        try {
            Logger.i("MTweaks loaded in " + mProcessName + " (api " + getApiVersion() + ", "
                    + getFrameworkName() + " " + getFrameworkVersion()
                    + ", systemServer=" + mSystemServer + ")");
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        try {
            Bundle state = new Bundle();
            state.putStringArray(STATE_PACKAGES, mLoadedPackages.toArray(new String[0]));
            param.setSavedInstanceState(state);
        } catch (Throwable e) {
            Logger.w("Can't save hot reload state: " + e);
        }
        // 模块没有自有线程，丢弃延迟任务后旧代码即可安全退出。
        for (FeatureModule module : mModules) {
            module.unload();
        }
        Logger.i("Hot reloading " + mProcessName);
        return true;
    }

    @Override
    public void onHotReloaded(HotReloadedParam param) {
        // 旧 hook 的声明类能拿到该进程的目标类加载器，必须在卸载前取。
        ClassLoader classLoader = findClassLoader(param.getOldHookHandles());
        attach(param.getProcessName(), param.isSystemServer());
        super.onHotReloaded(param);
        List<String> packages = savedPackages(param);
        if (packages.isEmpty()) {
            packages = Collections.singletonList(mSystemServer ? "system" : mProcessName);
        }
        Logger.i("Hot reloaded " + mProcessName + ", reloading " + packages);
        for (String packageName : packages) {
            dispatch(packageName, classLoader, "system".equals(packageName));
        }
    }

    private void attach(String processName, boolean systemServer) {
        mProcessName = processName;
        mSystemServer = systemServer;
        Reflect.attachFramework(this);
        Logger.attach(this);
        try {
            Prefs.attachHookedProcess(this);
        } catch (Throwable e) {
            Logger.w("Remote preferences unavailable: " + e);
        }
    }

    private static ClassLoader findClassLoader(List<HookHandle> oldHooks) {
        for (HookHandle handle : oldHooks) {
            try {
                ClassLoader classLoader = handle.getExecutable().getDeclaringClass().getClassLoader();
                if (classLoader != null) {
                    return classLoader;
                }
            } catch (Throwable ignored) {
            }
        }
        return MTweaksModule.class.getClassLoader();
    }

    private static List<String> savedPackages(HotReloadedParam param) {
        Object saved = param.getSavedInstanceState();
        if (saved instanceof Bundle bundle) {
            String[] packages = bundle.getStringArray(STATE_PACKAGES);
            if (packages != null && packages.length > 0) {
                return new ArrayList<>(Arrays.asList(packages));
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        dispatch("system", param.getClassLoader(), true);
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        ClassLoader classLoader;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            classLoader = param.getDefaultClassLoader();
        } else {
            classLoader = MTweaksModule.class.getClassLoader();
        }
        dispatch(param.getPackageName(), classLoader, false);
    }

    private void dispatch(String packageName, ClassLoader classLoader, boolean systemServer) {
        mLoadedPackages.add(packageName);
        ModuleContext context = new ModuleContext(packageName, classLoader, systemServer);
        List<Class<?>> classes;
        try {
            classes = new ModuleRegistry().getModulesFor(packageName);
        } catch (Throwable e) {
            Logger.e("Can't build module registry for " + packageName, e);
            return;
        }
        for (Class<?> raw : classes) {
            if (!FeatureModule.class.isAssignableFrom(raw)) {
                // Legacy (de.robv) hook class that has not been migrated yet.
                continue;
            }
            if (Modifier.isAbstract(raw.getModifiers())) {
                continue;
            }
            XposedLoad load = raw.getAnnotation(XposedLoad.class);
            if (load == null) {
                continue;
            }
            if (ArrayUtils.contains(load.excludedPackages(), packageName)) {
                Logger.d("Skip " + raw.getSimpleName() + " on excluded " + packageName);
                continue;
            }
            try {
                FeatureModule module = (FeatureModule) raw.getDeclaredConstructor().newInstance();
                mModules.add(module);
                module.load(context);
            } catch (Throwable e) {
                Logger.e("Can't load " + raw.getName() + " for " + packageName, e);
            }
        }
    }
}
