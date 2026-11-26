package me.dreamvoid.mtweaks.xposed.system;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageParser;
import li.lingfeng.ltweaks.lib.XposedLoad;
import li.lingfeng.ltweaks.prefs.ClassNames;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;

@XposedLoad(packages = PackageNames.ANDROID, prefs = "key_debug_debuggable")
public class XposedDebuggable extends FeatureModule {
    @Override
    protected void onLoad() {
        hookAllMethods(ClassNames.PACKAGE_PARSER, "parsePackage", chain -> {
            Object result = chain.proceed();
            PackageParser.Package pkg = (PackageParser.Package) result;
            if (pkg == null) {
                return result;
            }
            if (pkg.packageName.equals(PackageNames.ANDROID)) {
                Logger.i("Set debuggable for all apps.");
                return result;
            }

            ApplicationInfo appInfo = pkg.applicationInfo;
            appInfo.flags |= ApplicationInfo.FLAG_DEBUGGABLE;
            return result;
        });
    }
}
