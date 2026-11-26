package me.dreamvoid.mtweaks.xposed.google;

import android.os.Build;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;
import me.dreamvoid.mtweaks.xposed.core.Reflect;

/**
 * Spoofs the device as a Pixel XL so Google Photos enables its Pixel exclusive features.
 */
@XposedLoad(packages = PackageNames.GOOGLE_PHOTOS, prefs = "key_google_photos_spoof_pixel_xl")
public class XposedGooglePhotosSpoofPixelXl extends FeatureModule {

    private static final String PACKAGE_MANAGER_CLASS = "android.app.ApplicationPackageManager";
    private static final String HAS_SYSTEM_FEATURE = "hasSystemFeature";

    private static final Map<String, String> BUILD_FIELDS = new LinkedHashMap<>();
    static {
        BUILD_FIELDS.put("BRAND", "google");
        BUILD_FIELDS.put("MANUFACTURER", "Google");
        BUILD_FIELDS.put("DEVICE", "marlin");
        BUILD_FIELDS.put("PRODUCT", "marlin");
        BUILD_FIELDS.put("MODEL", "Pixel XL");
        BUILD_FIELDS.put("FINGERPRINT",
                "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");
    }

    private static final Set<String> ENABLED_FEATURES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.photos.NEXUS_PRELOAD",
            "com.google.android.apps.photos.nexus_preload"
    ));

    private static final Set<String> DISABLED_FEATURES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.photos.PIXEL_2017_PRELOAD",
            "com.google.android.apps.photos.PIXEL_2018_PRELOAD",
            "com.google.android.apps.photos.PIXEL_2019_MIDYEAR_PRELOAD",
            "com.google.android.apps.photos.PIXEL_2019_PRELOAD",
            "com.google.android.feature.PIXEL_2020_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2020_EXPERIENCE",
            "com.google.android.feature.PIXEL_2021_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2021_EXPERIENCE",
            "com.google.android.feature.PIXEL_2022_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2022_EXPERIENCE",
            "com.google.android.feature.PIXEL_2023_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2023_EXPERIENCE",
            "com.google.android.feature.PIXEL_2024_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2024_EXPERIENCE",
            "com.google.android.feature.PIXEL_2025_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2025_EXPERIENCE",
            "com.google.android.feature.PIXEL_2026_EXPERIENCE"
    ));

    @Override
    protected void onLoad() {
        spoofBuildFields();
        hookHasSystemFeature(new Class<?>[]{ String.class });
        hookHasSystemFeature(new Class<?>[]{ String.class, int.class });
    }

    private void spoofBuildFields() {
        int spoofed = 0;
        for (Map.Entry<String, String> entry : BUILD_FIELDS.entrySet()) {
            try {
                Reflect.setStaticObjectField(Build.class, entry.getKey(), entry.getValue());
                spoofed++;
            } catch (Throwable e) {
                Logger.w("Can't spoof Build." + entry.getKey() + ": " + e);
            }
        }
        Logger.i("Spoofed Build (" + spoofed + "/" + BUILD_FIELDS.size()
                + "), MODEL is now " + Build.MODEL);
    }

    private void hookHasSystemFeature(Class<?>[] parameterTypes) {
        try {
            findAndHookMethod(PACKAGE_MANAGER_CLASS, HAS_SYSTEM_FEATURE, parameterTypes, chain -> {
                Object arg = chain.getArg(0);
                if (!(arg instanceof String feature)) {
                    return chain.proceed();
                }
                if (ENABLED_FEATURES.contains(feature)) {
                    return true;
                }
                if (DISABLED_FEATURES.contains(feature)) {
                    return false;
                }
                return chain.proceed();
            });
            Logger.i("Hooked " + PACKAGE_MANAGER_CLASS + "#" + HAS_SYSTEM_FEATURE
                    + Arrays.toString(parameterTypes));
        } catch (Throwable e) {
            Logger.w("Can't hook " + HAS_SYSTEM_FEATURE + Arrays.toString(parameterTypes) + ": " + e);
        }
    }
}
