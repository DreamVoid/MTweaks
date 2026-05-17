package me.dreamvoid.mtweaks.xposed.core;

import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.Modifier;
import java.util.List;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.Prefs;
import me.dreamvoid.mtweaks.utils.Logger;

/**
 * libxposed API 102 entry point, declared in {@code META-INF/xposed/java_init.list}.
 */
public class MTweaksModule extends XposedModule {

    private String mProcessName = "";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        mProcessName = param.getProcessName();
        Reflect.attachFramework(this);
        Logger.attach(this);
        try {
            Logger.i("MTweaks loaded in " + mProcessName + " (api " + getApiVersion() + ", "
                    + getFrameworkName() + " " + getFrameworkVersion()
                    + ", systemServer=" + param.isSystemServer() + ")");
        } catch (Throwable ignored) {
        }
        try {
            Prefs.attachHookedProcess(this);
        } catch (Throwable e) {
            Logger.w("Remote preferences unavailable: " + e);
        }
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        dispatch("system", param.getClassLoader(), true);
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        ClassLoader classLoader;
        try {
            classLoader = param.getDefaultClassLoader();
        } catch (Throwable e) {
            classLoader = MTweaksModule.class.getClassLoader();
        }
        dispatch(param.getPackageName(), classLoader, false);
    }

    private void dispatch(String packageName, ClassLoader classLoader, boolean systemServer) {
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
                module.load(context);
            } catch (Throwable e) {
                Logger.e("Can't load " + raw.getName() + " for " + packageName, e);
            }
        }
    }
}
