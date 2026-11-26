package me.dreamvoid.mtweaks.xposed.google;

import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;
import me.dreamvoid.mtweaks.xposed.core.Hooks;

/**
 * Forces the body temperature (BTS) feature of the Pixel Thermometer app to be available.
 * <p>
 * The old PixelThermometerBTEnabler hooked the (now removed) obfuscated class {@code blw}.
 * Newer versions moved the availability check into a feature helper whose conditions are:
 * <ul>
 *     <li>{@code ro.build.id} must not contain {@code UD1A};</li>
 *     <li>{@code DeviceConfig} flag {@code Thermometer__bts_enabled} must be true;</li>
 *     <li>the {@code BTSActivity} component must exist;</li>
 *     <li>the {@code is_bts_activated} preference must be true, otherwise the SIM country has to
 *     be in the enabled country list.</li>
 * </ul>
 * Instead of touching the obfuscated class names, this module hooks the stable framework APIs
 * used by that check. When the preference read is forced, the app itself enables the
 * {@code BTSActivity} component and returns true. The preference is never written, so turning
 * the switch off restores the hidden state after the app is restarted.
 */
@XposedLoad(packages = { PackageNames.PIXEL_HEALTH },
        prefs = "key_pixel_thermometer_force_bts")
public class XposedPixelThermometerBts extends FeatureModule {

    private static final String DEVICE_CONFIG_CLASS = "android.provider.DeviceConfig";
    private static final String SHARED_PREFERENCES_IMPL_CLASS = "android.app.SharedPreferencesImpl";
    private static final String SYSTEM_PROPERTIES_CLASS = "android.os.SystemProperties";

    private static final String DEVICE_CONFIG_NAMESPACE = "device_personalization_services";
    private static final String BTS_ENABLED_FLAG = "Thermometer__bts_enabled";
    private static final String BTS_ACTIVATED_PREF = "is_bts_activated";

    private static final String BUILD_ID_PROPERTY = "ro.build.id";
    private static final String BLOCKED_BUILD_ID_MARKER = "UD1A";
    private static final String REPLACEMENT_BUILD_ID_MARKER = "UU1A";

    @Override
    protected void onLoad() {
        hookBtsFlag();
        hookBtsActivatedPreference();
        hookBuildId();
    }

    /**
     * The app caches {@code DeviceConfig.getBoolean("device_personalization_services",
     * "Thermometer__bts_enabled", true)} when the feature helper is created.
     */
    private void hookBtsFlag() {
        try {
            Class<?> deviceConfig = findClass(DEVICE_CONFIG_CLASS);
            Hooks.findAndHookMethod(deviceConfig, "getBoolean",
                    new Class<?>[]{ String.class, String.class, boolean.class }, chain -> {
                        String namespace = (String) chain.getArg(0);
                        String flag = (String) chain.getArg(1);
                        if (DEVICE_CONFIG_NAMESPACE.equals(namespace)
                                && BTS_ENABLED_FLAG.equals(flag)) {
                            return true;
                        }
                        return chain.proceed();
                    });
            Logger.i("Force enabled DeviceConfig flag " + BTS_ENABLED_FLAG);
        } catch (Throwable e) {
            Logger.w("Can't hook DeviceConfig#getBoolean: " + e);
        }
    }

    /**
     * This is the decisive check: when the preference reads as true the app enables its
     * {@code BTSActivity} component and reports the feature as available.
     */
    private void hookBtsActivatedPreference() {
        try {
            Class<?> preferences = findClass(SHARED_PREFERENCES_IMPL_CLASS);
            Hooks.findAndHookMethod(preferences, "getBoolean",
                    new Class<?>[]{ String.class, boolean.class }, chain -> {
                        if (BTS_ACTIVATED_PREF.equals(chain.getArg(0))) {
                            return true;
                        }
                        return chain.proceed();
                    });
            Logger.i("Force enabled preference " + BTS_ACTIVATED_PREF);
        } catch (Throwable e) {
            Logger.w("Can't hook SharedPreferencesImpl#getBoolean: " + e);
        }
    }

    /**
     * Some builds disable the feature by embedding {@code UD1A} in {@code ro.build.id}.
     */
    private void hookBuildId() {
        try {
            Class<?> systemProperties = findClass(SYSTEM_PROPERTIES_CLASS);
            Hooks.findAndHookMethod(systemProperties, "get",
                    new Class<?>[]{ String.class, String.class }, chain -> {
                        Object result = chain.proceed();
                        if (result instanceof String value
                                && BUILD_ID_PROPERTY.equals(chain.getArg(0))
                                && value.contains(BLOCKED_BUILD_ID_MARKER)) {
                            return value.replace(BLOCKED_BUILD_ID_MARKER,
                                    REPLACEMENT_BUILD_ID_MARKER);
                        }
                        return result;
                    });
            Logger.i("Neutralized UD1A build id gate");
        } catch (Throwable e) {
            Logger.w("Can't hook SystemProperties#get: " + e);
        }
    }
}
