package me.dreamvoid.mtweaks.xposed.google;

import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;
import me.dreamvoid.mtweaks.xposed.core.Reflect;

import java.lang.reflect.Field;

/**
 * Shows legacy app icons in their original shape instead of wrapping them into the adaptive icon shape.
 */
@XposedLoad(packages = {PackageNames.PIXEL_LAUNCHER}, prefs = "key_pixel_launcher_no_adaptive_icon")
public class XposedPixelLauncherNoAdaptiveIcon extends FeatureModule {

    private static final String BASE_ICON_FACTORY = "com.android.launcher3.icons.BaseIconFactory";
    private static final String ICON_OPTIONS = "com.android.launcher3.icons.BaseIconFactory$IconOptions";
    private static final String ICON_PROVIDER = "com.android.launcher3.icons.IconProvider";
    private static final String STATE_VALUE = "mtweaks_no_adaptive_icon=1";

    private Class<?> mFactoryClass;
    private Class<?> mOptionsClass;
    private Field mWrapNonAdaptiveIcon;
    private Field mDrawFullBleed;
    private Field mIconScale;
    private float mForegroundScale = 0.92f;

    @Override
    protected void onLoad() {
        resolveClasses();
        hookIconOptions();
        hookAnimationWrapper();
        hookIconFreshness();
    }

    private void resolveClasses() {
        try {
            mFactoryClass = findClass(BASE_ICON_FACTORY);
            mOptionsClass = findClass(ICON_OPTIONS);
            mWrapNonAdaptiveIcon = Reflect.findField(mOptionsClass, "wrapNonAdaptiveIcon");
            mDrawFullBleed = Reflect.findField(mOptionsClass, "drawFullBleed");
            mIconScale = Reflect.findField(mOptionsClass, "iconScale");
            try {
                mForegroundScale = mIconScale.getFloat(Reflect.newInstance(mOptionsClass));
            } catch (Throwable e) {
                Logger.w("Can't read default icon scale: " + e);
            }
        } catch (Throwable e) {
            Logger.w("Can't resolve Pixel Launcher icon classes: " + e);
        }
    }

    private void hookIconOptions() {
        if (mFactoryClass == null || mOptionsClass == null || mWrapNonAdaptiveIcon == null || mDrawFullBleed == null) {
            return;
        }
        try {
            findAndHookMethod(mFactoryClass, "createBadgedIconBitmap", new Class<?>[]{Drawable.class, mOptionsClass}, chain -> {
                Object icon = chain.getArg(0);
                Object options = chain.getArg(1);
                if (icon instanceof Drawable drawable && options != null && !(drawable instanceof AdaptiveIconDrawable)) {
                    try {
                        if (mWrapNonAdaptiveIcon.getBoolean(options)) {
                            mWrapNonAdaptiveIcon.setBoolean(options, false);
                            // Full bleed draws a white background and clips the icon to the icon shape.
                            mDrawFullBleed.set(options, Boolean.FALSE);
                        }
                    } catch (Throwable e) {
                        Logger.w("Can't disable adaptive icon options: " + e);
                    }
                }
                return chain.proceed();
            });
            Logger.i("Hooked Pixel Launcher adaptive icon options");
        } catch (Throwable e) {
            Logger.w("Can't hook Pixel Launcher icon options: " + e);
        }
    }

    private void hookAnimationWrapper() {
        if (mFactoryClass == null || mOptionsClass == null) {
            return;
        }
        try {
            findAndHookMethod(mFactoryClass, "wrapToAdaptiveIcon", new Class<?>[]{Drawable.class, mOptionsClass}, chain -> {
                Object icon = chain.getArg(0);
                if (chain.getArg(1) == null && icon instanceof Drawable drawable && !(drawable instanceof AdaptiveIconDrawable)) {
                    try {
                        return wrapTransparent(drawable);
                    } catch (Throwable e) {
                        Logger.w("Can't wrap transparent adaptive icon: " + e);
                    }
                }
                return chain.proceed();
            });
            Logger.i("Hooked Pixel Launcher adaptive icon wrapper");
        } catch (Throwable e) {
            Logger.w("Can't hook Pixel Launcher icon wrapper: " + e);
        }
    }

    private void hookIconFreshness() {
        try {
            Class<?> provider = findClass(ICON_PROVIDER);
            findAndHookMethod(provider, "getStateForApp", new Class<?>[]{ApplicationInfo.class}, chain -> {
                Object result = chain.proceed();
                if (chain.getArg(0) == null) {
                    return result;
                }
                try {
                    return Reflect.callMethod(result, "withAdditionalValues", (Object) new String[]{STATE_VALUE});
                } catch (Throwable e) {
                    Logger.w("Can't update icon freshness: " + e);
                    return result;
                }
            });
            Logger.i("Hooked Pixel Launcher icon freshness");
        } catch (Throwable e) {
            Logger.w("Can't hook Pixel Launcher icon state: " + e);
        }
    }

    private AdaptiveIconDrawable wrapTransparent(Drawable icon) {
        float extra = AdaptiveIconDrawable.getExtraInsetFraction();
        float scale = mForegroundScale / ((extra * 2.0f) + 1.0f);
        Drawable foreground = (Drawable) Reflect.callStaticMethod(mFactoryClass, "wrapIntoSquareDrawable", icon, scale);
        AdaptiveIconDrawable adaptive = new AdaptiveIconDrawable(new ColorDrawable(Color.TRANSPARENT), foreground);
        adaptive.setBounds(0, 0, 1, 1);
        return adaptive;
    }
}
