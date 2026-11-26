package me.dreamvoid.mtweaks.xposed.system;

import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.utils.Utils;
import me.dreamvoid.mtweaks.xposed.core.Hooks;
import me.dreamvoid.mtweaks.xposed.core.Reflect;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by lilingfeng on 2017/11/29.
 * <p>
 * Allows disabling any package from the application info page. The forced "disable" button
 * of a system app is painted red, so the risk is visible at a glance.
 */
@XposedLoad(packages = PackageNames.ANDROID_SETTINGS,
        prefs = "key_app_info_allow_disable")
public class XposedAppInfoAllowDisable extends XposedAppInfo {

    private static final String LEGACY_APP_INFO = "com.android.settings.applications.InstalledAppDetails";
    private static final String SPA_DISABLE_BUTTON = "com.android.settings.spa.app.appinfo.AppDisableButton";
    private static final String SPA_ACTION_BUTTONS = "com.android.settingslib.spa.widget.button.ActionButtonsKt";
    private static final String MATERIAL_BUTTON_KT = "androidx.compose.material3.ButtonKt";
    private static final String MATERIAL_ICON_BUTTON_KT = "androidx.compose.material3.IconButtonKt";
    private static final String MARK_RISKY = "mtweaks_risky_disable";

    /** Packages whose disable button had to be force enabled by the module. */
    private static final Set<String> FORCED_PACKAGES = ConcurrentHashMap.newKeySet();

    /** Set while the marked button is being rendered, so its colors can be replaced. */
    private static final ThreadLocal<Boolean> RENDERING_RISKY = new ThreadLocal<>();

    /**
     * Compose stores a color as {@code (argb << 32) | colorSpaceId}, sRGB being 0, so the
     * packed longs below are opaque red / white and their translucent variants.
     */
    private static final long COLOR_RED = 0xFFFF000000000000L;
    private static final long COLOR_ON_RED = 0xFFFFFFFF00000000L;
    private static final long COLOR_RED_DISABLED = 0x4DFF000000000000L;
    private static final long COLOR_ON_RED_DISABLED = 0x66FFFFFF00000000L;

    @Override
    protected void onLoad() {
        hookLegacyButton();
        hookSpaButton();
    }

    /**
     * Android 13 and older: the uninstall button is disabled for system apps.
     */
    private void hookLegacyButton() {
        try {
            Class<?> cls = findClass(LEGACY_APP_INFO);
            findAndHookMethod(cls, "initUninstallButtons", new Class<?>[0], chain -> {
                Object result = chain.proceed();
                Button button = (Button) Reflect.getObjectField(chain.getThisObject(), "mUninstallButton");
                if (button.isEnabled()) {
                    return result;
                }
                ApplicationInfo info = legacyInfo(chain.getThisObject());
                if (info == null) {
                    return result;
                }
                Logger.i("Enable disable button for " + info.packageName);
                button.getBackground().setColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY);
                button.setEnabled(true);
                button.setOnClickListener((View.OnClickListener) chain.getThisObject());
                if (!info.enabled) {
                    button.setText(Utils.getString("enable_text"));
                }
                return result;
            });
            Logger.i("Hooked legacy uninstall buttons.");
        } catch (Throwable ignored) {
            // Not present on newer Settings.
        }
    }

    /**
     * Android 16+: the app info page is Compose based. The button is gated by
     * {@code AppDisableButton.canBeDisabled(ApplicationInfo)} and rendered as a
     * {@code FilledTonalButton} whose colors are replaced with red for system apps.
     */
    private void hookSpaButton() {
        final Class<?> composerClass;
        final Class<?> actionButtonClass;
        final Class<?> disableButtonClass;
        try {
            disableButtonClass = findClass(SPA_DISABLE_BUTTON);
            actionButtonClass = findClass("com.android.settingslib.spa.widget.button.ActionButton");
            composerClass = findClass("androidx.compose.runtime.Composer");
        } catch (Throwable e) {
            return;
        }

        try {
            findAndHookMethod(disableButtonClass, "canBeDisabled",
                    new Class<?>[]{ApplicationInfo.class}, chain -> {
                        boolean originallyAllowed = Boolean.TRUE.equals(chain.proceed());
                        if (originallyAllowed) {
                            return true;
                        }
                        // Only system apps that Android refuses to disable are forced on, and
                        // these are exactly the ones painted red.
                        ApplicationInfo info = (ApplicationInfo) chain.getArg(0);
                        if (isSystemApp(info)) {
                            FORCED_PACKAGES.add(info.packageName);
                            Logger.i("Force enabling disable button for " + info.packageName);
                            return true;
                        }
                        return false;
                    });
            Logger.i("Force enable disable button for locked system apps (SPA).");
        } catch (Throwable e) {
            Logger.w("Can't hook SPA canBeDisabled: " + e);
            return;
        }

        try {
            findAndHookMethod(disableButtonClass, "getActionButton",
                    new Class<?>[]{ApplicationInfo.class, composerClass, int.class}, chain -> {
                        Object result = chain.proceed();
                        ApplicationInfo info = (ApplicationInfo) chain.getArg(0);
                        return markRiskyButton(result, info);
                    });
        } catch (Throwable e) {
            Logger.w("Can't hook SPA getActionButton: " + e);
        }

        hookSpaButtonColors(actionButtonClass);
    }

    /**
     * Marks the button instance itself; the renderer receives the very same object.
     * Only packages that had to be force enabled are marked, so the buttons of system apps
     * that are already disposable keep their normal look.
     */
    private Object markRiskyButton(Object actionButton, ApplicationInfo info) {
        if (actionButton == null || info == null || !FORCED_PACKAGES.contains(info.packageName)) {
            return actionButton;
        }
        try {
            if (!Boolean.TRUE.equals(Reflect.callMethod(actionButton, "getEnabled"))) {
                return actionButton;
            }
            Reflect.setAdditionalInstanceField(actionButton, MARK_RISKY, Boolean.TRUE);
        } catch (Throwable e) {
            Logger.w("Can't mark risky disable button: " + e);
        }
        return actionButton;
    }

    private boolean isSystemApp(ApplicationInfo info) {
        return info != null && info.enabled
                && (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    /**
     * Replaces the {@code ButtonColors} of {@code FilledTonalButton} calls that belong to the
     * forced disable button with a red scheme.
     */
    private void hookSpaButtonColors(Class<?> actionButtonClass) {
        hookActionButtonRenderer(actionButtonClass);
        hookColoredComposable(MATERIAL_ICON_BUTTON_KT, "FilledIconButton",
                "androidx.compose.material3.IconButtonColors");
        hookColoredComposable(MATERIAL_BUTTON_KT, "FilledTonalButton",
                "androidx.compose.material3.ButtonColors");
    }

    /**
     * Replaces the color object of a Material 3 button composable with a red scheme while the
     * marked button is being rendered. The original colors come from the wallpaper based
     * monet palette, so they have to be overridden explicitly.
     */
    private void hookColoredComposable(String ktClassName, String methodPrefix, String colorsClassName) {
        final Class<?> colorsClass;
        final Class<?> ktClass;
        final Object redColors;
        try {
            colorsClass = findClass(colorsClassName);
            ktClass = findClass(ktClassName);
            redColors = Reflect.newInstance(colorsClass,
                    COLOR_RED, COLOR_ON_RED, COLOR_RED_DISABLED, COLOR_ON_RED_DISABLED);
        } catch (Throwable e) {
            Logger.w("Can't prepare red colors for " + methodPrefix + ": " + e);
            return;
        }

        int hooked = 0;
        for (Method method : ktClass.getDeclaredMethods()) {
            if (!method.getName().startsWith(methodPrefix)) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            final int colorsIndex = indexOf(types, colorsClass);
            if (colorsIndex < 0) {
                continue;
            }
            try {
                Hooks.hook(method, chain -> {
                    if (!Boolean.TRUE.equals(RENDERING_RISKY.get())) {
                        return chain.proceed();
                    }
                    Object[] args = chain.getArgs().toArray();
                    args[colorsIndex] = redColors;
                    return chain.proceed(args);
                });
                ++hooked;
            } catch (Throwable e) {
                Logger.w("Can't hook " + method.getName() + ": " + e);
            }
        }
        if (hooked > 0) {
            Logger.i("Hooked " + hooked + " " + methodPrefix + " for red warning.");
        }
    }

    /**
     * Flags the composition of the marked button so the color replacement above knows which
     * {@code FilledTonalButton} belongs to it.
     */
    private void hookActionButtonRenderer(Class<?> actionButtonClass) {
        try {
            Class<?> buttonsKt = findClass(SPA_ACTION_BUTTONS);
            for (Method method : buttonsKt.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                final int buttonIndex = indexOf(types, actionButtonClass);
                if (buttonIndex < 0) {
                    continue;
                }
                try {
                    Hooks.hook(method, chain -> {
                        Object button = chain.getArg(buttonIndex);
                        boolean risky = button != null
                                && Reflect.getAdditionalInstanceField(button, MARK_RISKY) != null;
                        if (!risky) {
                            return chain.proceed();
                        }
                        Boolean previous = RENDERING_RISKY.get();
                        RENDERING_RISKY.set(Boolean.TRUE);
                        try {
                            return chain.proceed();
                        } finally {
                            RENDERING_RISKY.set(previous);
                        }
                    });
                } catch (Throwable e) {
                    Logger.w("Can't hook " + method.getName() + ": " + e);
                }
            }
        } catch (Throwable e) {
            Logger.w("Can't hook action button renderer: " + e);
        }
    }

    private static int indexOf(Class<?>[] types, Class<?> target) {
        for (int i = 0; i < types.length; ++i) {
            if (types[i] == target) {
                return i;
            }
        }
        return -1;
    }

    private ApplicationInfo legacyInfo(Object fragment) {
        try {
            Object appEntry = Reflect.getObjectField(fragment, "mAppEntry");
            return (ApplicationInfo) Reflect.getObjectField(appEntry, "info");
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    protected Pair<String, Integer>[] newMenuNames() {
        return null;
    }

    @Override
    protected void onMenuItemSelected(CharSequence menuName, ApplicationInfo info) {
    }
}
