package me.dreamvoid.mtweaks.xposed.google;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageItemInfo;
import android.content.res.Configuration;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.R;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;
import me.dreamvoid.mtweaks.xposed.core.Reflect;
import me.dreamvoid.mtweaks.xposed.google.iconpack.*;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

/**
 * Replaces Pixel Launcher app icons with icons from the selected installed icon pack.
 */
@XposedLoad(packages = {PackageNames.PIXEL_LAUNCHER}, prefs = "key_pixel_launcher_themed_icon_pack")
public class XposedPixelLauncherThemedIconPack extends FeatureModule {

    private static final String ICON_PROVIDER = "com.android.launcher3.icons.IconProvider";
    private static final String SETTINGS_FRAGMENT = "com.google.android.apps.nexuslauncher.settings.MySettingsFragment";
    private static final String PREF_KEY = "mtweaks_icon_pack_pref";

    private static volatile String sRenderedTheme;
    private static volatile long sLastThemeRefresh;

    @Override
    protected void onLoad() {
        hookIconProvider();
        hookThemeChange();
        hookSettingsFragment();
    }

    private void hookIconProvider() {
        try {
            Class<?> provider = findClass(ICON_PROVIDER);
            Class<?> packageItemInfo = findClass("android.content.pm.PackageItemInfo");
            findAndHookMethod(provider, "getIcon", new Class<?>[]{packageItemInfo, ApplicationInfo.class, int.class}, chain -> {
                Drawable replacement = null;
                try {
                    replacement = replaceIcon(chain.getThisObject(), (PackageItemInfo) chain.getArg(0), (Integer) chain.getArg(2));
                } catch (Throwable e) {
                    Logger.w("Can't load icon from icon pack: " + e);
                }
                return replacement != null ? replacement : chain.proceed();
            });
            findAndHookMethod(provider, "getStateForApp", new Class<?>[]{ApplicationInfo.class}, chain -> {
                Object result = chain.proceed();
                try {
                    if (result != null) {
                        Context context = (Context) Reflect.getObjectField(chain.getThisObject(), "mContext");
                        String pack = IconPackPrefs.get(context);
                        String state = "mtweaks_icon_pack=" + pack;
                        if (!pack.isEmpty()) {
                            String signature = ThemeSignature.of(context);
                            if (sRenderedTheme == null) {
                                sRenderedTheme = signature;
                            }
                            state += ";theme=" + signature;
                        }
                        return Reflect.callMethod(result, "withAdditionalValues", (Object) new String[]{state});
                    }
                } catch (Throwable e) {
                    Logger.w("Can't update icon freshness: " + e);
                }
                return result;
            });
            Logger.i("Hooked Pixel Launcher icon pack");
        } catch (Throwable e) {
            Logger.w("Can't hook Pixel Launcher icon provider: " + e);
        }
    }

    private void hookThemeChange() {
        try {
            Class<?> launcher = findClass("com.android.launcher3.uioverrides.QuickstepLauncher");
            findAndHookMethod(launcher, "onConfigurationChanged", new Class<?>[]{Configuration.class}, chain -> {
                Object result = chain.proceed();
                if (chain.getThisObject() instanceof Context context) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> refreshIfThemeChanged(context, false), 500);
                }
                return result;
            });
            findAndHookMethod(launcher, "onResume", new Class<?>[0], chain -> {
                Object result = chain.proceed();
                if (chain.getThisObject() instanceof Context context) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> refreshIfThemeChanged(context, true), 500);
                }
                return result;
            });
        } catch (Throwable e) {
            Logger.w("Can't hook launcher configuration change: " + e);
        }
        try {
            Class<?> themeManager = findClass("com.android.launcher3.graphics.ThemeManager");
            findAndHookMethod(themeManager, "verifyIconState", new Class<?>[0], chain -> {
                Object result = chain.proceed();
                try {
                    if (Reflect.getObjectField(chain.getThisObject(), "context") instanceof Context context) {
                        refreshIfThemeChanged(context, false);
                    }
                } catch (Throwable e) {
                    Logger.w("Can't read theme manager context: " + e);
                }
                return result;
            });
        } catch (Throwable e) {
            Logger.w("Can't hook theme manager: " + e);
        }
    }

    private static void refreshIfThemeChanged(Context context, boolean force) {
        try {
            if (IconPackPrefs.get(context).isEmpty()) {
                sRenderedTheme = ThemeSignature.of(context);
                return;
            }
            String signature = ThemeSignature.of(context);
            String rendered = sRenderedTheme;
            if (rendered == null) {
                sRenderedTheme = signature;
                return;
            }
            if (rendered.equals(signature)) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            if (!force && now - sLastThemeRefresh < 1000) {
                return;
            }
            sLastThemeRefresh = now;
            Logger.i("Theme colors changed, refreshing icons");
            LauncherAccess.refresh(context);
            if (force) {
                sRenderedTheme = signature;
            }
        } catch (Throwable e) {
            Logger.w("Can't refresh icons after theme change: " + e);
        }
    }

    private void hookSettingsFragment() {
        try {
            Class<?> fragment = findClass(SETTINGS_FRAGMENT);
            findAndHookMethod(fragment, "onCreatePreferences", new Class<?>[]{Bundle.class, String.class}, chain -> {
                Object result = chain.proceed();
                try {
                    if (chain.getArg(1) == null) {
                        injectSettings(chain.getThisObject());
                    }
                } catch (Throwable e) {
                    Logger.w("Can't inject icon pack setting: " + e);
                }
                return result;
            });
        } catch (Throwable e) {
            Logger.w("Can't hook Pixel Launcher settings: " + e);
        }
    }

    private Drawable replaceIcon(Object provider, PackageItemInfo info, int density) {
        if (!(info instanceof ComponentInfo) || info.name == null) {
            return null;
        }
        Context context = (Context) Reflect.getObjectField(provider, "mContext");
        String pack = IconPackPrefs.get(context);
        if (pack.isEmpty()) {
            return null;
        }
        String className = info.name;
        if (className.startsWith(".")) {
            className = info.packageName + className;
        }
        Drawable icon = IconPackManager.getIcon(context, pack, new ComponentName(info.packageName, className), density);
        if (icon == null) {
            return null;
        }
        Drawable monochrome = IconPackManager.getMonochromeIcon(context, pack, info.packageName, density);
        return addMonochromeLayer(icon, monochrome);
    }

    private static Drawable addMonochromeLayer(Drawable icon, Drawable monochrome) {
        if (monochrome == null || !(icon instanceof AdaptiveIconDrawable adaptive)) {
            return icon;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (adaptive.getMonochrome() == null) {
                Drawable background = adaptive.getBackground();
                Drawable foreground = adaptive.getForeground();
                if (background != null && foreground != null) {
                    float extra = AdaptiveIconDrawable.getExtraInsetFraction();
                    float inset = extra / ((extra * 2.0f) + 1.0f);
                    return new AdaptiveIconDrawable(background, foreground, new InsetDrawable(new InsetDrawable(monochrome.mutate(), 0.2f), inset));
                }
            }
        }
        return icon;
    }

    private void injectSettings(Object fragment) throws Throwable {
        Object screen = Reflect.callMethod(fragment, "getPreferenceScreen");
        if (screen == null || Reflect.callMethod(screen, "findPreference", PREF_KEY) != null) {
            return;
        }
        Context context = (Context) Reflect.callMethod(fragment, "getContext");
        if (context == null) {
            return;
        }

        List<IconPackInfo> packs;
        try {
            packs = IconPackScanner.scan(context);
        } catch (Throwable e) {
            packs = Collections.emptyList();
        }

        CharSequence[] entries = new CharSequence[packs.size() + 1];
        CharSequence[] values = new CharSequence[packs.size() + 1];
        entries[0] = ModuleStrings.get(context, R.string.pref_pixel_launcher_themed_icon_pack_system, "系统图标");
        values[0] = "";
        for (int i = 0; i < packs.size(); i++) {
            entries[i + 1] = packs.get(i).label();
            values[i + 1] = packs.get(i).packageName();
        }

        String current = IconPackPrefs.get(context);
        if (!contains(values, current)) {
            current = "";
            IconPackPrefs.set(context, "");
        }

        ClassLoader loader = fragment.getClass().getClassLoader();
        if (loader == null) {
            loader = context.getClassLoader();
        }
        Class<?> preferenceClass = Class.forName("androidx.preference.Preference", false, loader);
        Object preference = Reflect.newInstance(preferenceClass, context, null);
        Reflect.callMethod(preference, "setKey", PREF_KEY);
        Reflect.callMethod(preference, "setTitle", ModuleStrings.get(context, R.string.pref_pixel_launcher_themed_icon_pack, "带主题的图标包"));
        Reflect.callMethod(preference, "setOrder", 0);
        Reflect.callMethod(preference, "setSummary", summaryFor(values, entries, current));
        Reflect.findField(preferenceClass, "mPersistent").setBoolean(preference, false);

        Field clickField = Reflect.findField(preferenceClass, "mOnClickListener");
        Object clickListener = Proxy.newProxyInstance(loader, new Class<?>[]{clickField.getType()}, (proxy, method, args) -> {
            String name = method.getName();
            switch (name) {
                case "onPreferenceClick" -> {
                    showPackDialog(context, preference, entries, values);
                    return true;
                }
                case "toString" -> {
                    return "MTweaksIconPackClickListener";
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                default -> {
                    return null;
                }
            }
        });
        clickField.set(preference, clickListener);
        Reflect.callMethod(screen, "addPreference", preference);
        Logger.i("Added icon pack setting");
    }

    private void showPackDialog(Context context, Object preference, CharSequence[] entries, CharSequence[] values) {
        String current = IconPackPrefs.get(context);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].toString().equals(current)) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(context)
                .setTitle(ModuleStrings.get(context, R.string.pref_pixel_launcher_themed_icon_pack, "带主题的图标包"))
                .setSingleChoiceItems(entries, checked, (dialog, which) -> {
                    String value = values[which].toString();
                    IconPackPrefs.set(context, value);
                    sRenderedTheme = ThemeSignature.of(context);
                    try {
                        Reflect.callMethod(preference, "setSummary", entries[which]);
                    } catch (Throwable e) {
                        Logger.w("Can't update icon pack summary: " + e);
                    }
                    LauncherAccess.refresh(context);
                    dialog.dismiss();
                }).show();
    }

    private static boolean contains(CharSequence[] values, String value) {
        for (CharSequence item : values) {
            if (item.toString().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static CharSequence summaryFor(CharSequence[] values, CharSequence[] entries, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].toString().equals(value)) {
                return entries[i];
            }
        }
        return entries[0];
    }
}
