package me.dreamvoid.mtweaks.xposed.system;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.utils.Utils;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;
import me.dreamvoid.mtweaks.xposed.core.Hooks;
import me.dreamvoid.mtweaks.xposed.core.Reflect;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Created by lilingfeng on 2017/8/15.
 * <p>
 * Adds entries to the overflow menu of the application info page.
 * <p>
 * Android 16+ replaced the Settings app info page with a Compose screen
 * (com.android.settings.spa), so the old Fragment menu hooks no longer apply. Both
 * implementations are hooked here: the legacy {@code onCreateOptionsMenu} path and the
 * Compose {@code MoreOptionsScope} path.
 */
public abstract class XposedAppInfo extends FeatureModule {

    private static final String LEGACY_APP_INFO = "com.android.settings.applications.InstalledAppDetails";
    private static final String DASHBOARD_APP_INFO = "com.android.settings.applications.appinfo.AppInfoDashboardFragment";
    private static final String SPA_MORE_OPTIONS = "com.android.settings.spa.app.appinfo.AppInfoSettingsMoreOptionsKt";
    private static final String SPA_MORE_OPTIONS_STATE = "com.android.settings.spa.app.appinfo.AppInfoSettingsMoreOptionsState";
    private static final String SPA_MORE_OPTIONS_SCOPE = "com.android.settingslib.spa.widget.scaffold.MoreOptionsScope";

    /**
     * The overflow entries are emitted by the menu content lambda. When the state getters
     * are queried from anywhere else (the "should the overflow icon be shown" check), they
     * report true so the icon is always visible once this module adds entries.
     */
    private static final ThreadLocal<Boolean> IN_MORE_OPTIONS_CONTENT = new ThreadLocal<>();

    @Override
    protected void onLoad() throws Throwable {
        final Pair<String, Integer>[] names = newMenuNames();
        if (names == null || names.length == 0) {
            return;
        }
        hookLegacyMenu(names);
        hookSpaMenu(names);
    }

    // ------------------------------------------------------------- legacy fragment

    private void hookLegacyMenu(final Pair<String, Integer>[] names) {
        for (String className : new String[]{LEGACY_APP_INFO, DASHBOARD_APP_INFO}) {
            final Class<?> cls;
            try {
                cls = findClass(className);
            } catch (Throwable ignored) {
                continue;
            }
            try {
                findAndHookMethod(cls, "onCreateOptionsMenu",
                        new Class<?>[]{Menu.class, MenuInflater.class}, chain -> {
                            Object result = chain.proceed();
                            Menu menu = (Menu) chain.getArg(0);
                            for (Pair<String, Integer> pair : names) {
                                menu.add(Menu.NONE, Menu.NONE, pair.second, pair.first);
                            }
                            return result;
                        });

                findAndHookMethod(cls, "onOptionsItemSelected",
                        new Class<?>[]{MenuItem.class}, chain -> {
                            MenuItem item = (MenuItem) chain.getArg(0);
                            if (!Utils.pairContains(names, item.getTitle(), true)) {
                                return chain.proceed();
                            }
                            ApplicationInfo info = legacyApplicationInfo(chain.getThisObject());
                            if (info != null) {
                                onMenuItemSelected(item.getTitle(), info);
                            }
                            return true;
                        });
                Logger.i("Hooked legacy app info menu of " + className);
            } catch (Throwable e) {
                Logger.w("Can't hook legacy app info menu of " + className + ": " + e);
            }
        }
    }

    private ApplicationInfo legacyApplicationInfo(Object fragment) {
        try {
            Object appEntry = Reflect.getObjectField(fragment, "mAppEntry");
            return (ApplicationInfo) Reflect.getObjectField(appEntry, "info");
        } catch (Throwable ignored) {
            return null;
        }
    }

    // -------------------------------------------------------------------- compose

    private void hookSpaMenu(final Pair<String, Integer>[] names) {
        final Class<?> scopeClass;
        final Class<?> composerClass;
        final Class<?> function0Class;
        final Class<?> ktClass;
        try {
            ktClass = findClass(SPA_MORE_OPTIONS);
            scopeClass = findClass(SPA_MORE_OPTIONS_SCOPE);
            composerClass = findClass("androidx.compose.runtime.Composer");
            function0Class = findClass("kotlin.jvm.functions.Function0");
        } catch (Throwable e) {
            return;
        }

        hookMoreOptionsStateVisibility();

        int hooked = 0;
        for (Method method : ktClass.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            final int scopeIndex = indexOf(types, scopeClass);
            final int composerIndex = indexOf(types, composerClass);
            final int infoIndex = indexOf(types, ApplicationInfo.class);
            if (scopeIndex < 0 || composerIndex < 0 || infoIndex < 0) {
                continue;
            }
            try {
                Hooks.hook(method, chain -> {
                    Object scope = chain.getArg(scopeIndex);
                    Object composer = chain.getArg(composerIndex);
                    ApplicationInfo info = (ApplicationInfo) chain.getArg(infoIndex);
                    addComposeMenuItems(scope, composer, info, names, function0Class);
                    Boolean previous = IN_MORE_OPTIONS_CONTENT.get();
                    IN_MORE_OPTIONS_CONTENT.set(Boolean.TRUE);
                    try {
                        return chain.proceed();
                    } finally {
                        IN_MORE_OPTIONS_CONTENT.set(previous);
                    }
                });
                ++hooked;
            } catch (Throwable e) {
                Logger.w("Can't hook SPA more options " + method.getName() + ": " + e);
            }
        }
        if (hooked > 0) {
            Logger.i("Hooked " + hooked + " SPA app info menu(s).");
        }
    }

    /**
     * Android only shows the overflow icon when the native menu is non-empty. Report the
     * state as non-empty outside of the menu content so the icon is shown for our entries
     * as well, while the native entries themselves still use the real values.
     */
    private void hookMoreOptionsStateVisibility() {
        try {
            Class<?> stateClass = findClass(SPA_MORE_OPTIONS_STATE);
            int count = 0;
            for (String methodName : new String[]{
                    "getShownUninstallUpdates",
                    "getShownUninstallForAllUsers",
                    "getShouldShowAccessRestrictedSettings"}) {
                try {
                    findAndHookMethod(stateClass, methodName, new Class<?>[0], chain -> {
                        if (!Boolean.TRUE.equals(IN_MORE_OPTIONS_CONTENT.get())) {
                            return true;
                        }
                        return chain.proceed();
                    });
                    ++count;
                } catch (Throwable ignored) {
                }
            }
            if (count > 0) {
                Logger.i("Forced app info overflow menu to be visible.");
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Emits the entries inside the menu content lambda.
     * <p>
     * Compose invokes this lambda in "gap" mode when the parent is being skipped, and no
     * node may be emitted there, so emission is skipped in that case. The entries are also
     * wrapped into a replaceable group so the group table stays consistent between
     * compositions and recompositions (otherwise closing the menu crashes the app).
     */
    private void addComposeMenuItems(Object scope, Object composer, ApplicationInfo info,
                                     Pair<String, Integer>[] names, Class<?> function0Class) {
        if (scope == null || composer == null || info == null || info.packageName == null) {
            return;
        }
        if (isComposerSkipping(composer)) {
            return;
        }

        boolean groupStarted = false;
        try {
            groupStarted = startReplaceableGroup(composer);
            for (Pair<String, Integer> pair : names) {
                final CharSequence title = pair.first;
                final Object onClick = Proxy.newProxyInstance(function0Class.getClassLoader(),
                        new Class<?>[]{function0Class}, (proxy, method, args) -> {
                            return switch (method.getName()) {
                                case "invoke" -> {
                                    try {
                                        onMenuItemSelected(title, info);
                                    } catch (Throwable e) {
                                        Logger.e("Menu " + title + " failed", e);
                                    }
                                    yield null;
                                }
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                case "toString" -> "MTweaksMenuAction";
                                default -> null;
                            };
                        });
                try {
                    Reflect.callMethod(scope, "MenuItem", title, true, onClick, composer, 0, 0);
                } catch (Throwable e) {
                    Logger.w("Can't add SPA menu item " + title + ": " + e);
                }
            }
        } finally {
            if (groupStarted) {
                try {
                    Reflect.callMethod(composer, "endReplaceableGroup");
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private boolean isComposerSkipping(Object composer) {
        try {
            return Boolean.TRUE.equals(Reflect.callMethod(composer, "getSkipping"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean startReplaceableGroup(Object composer) {
        int key = 0x4d54574b; // "MTWK"
        try {
            Reflect.callMethod(composer, "startReplaceableGroup", key);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Reflect.callMethod(composer, "startReplaceGroup", key);
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static int indexOf(Class<?>[] types, Class<?> target) {
        for (int i = 0; i < types.length; ++i) {
            if (types[i] == target) {
                return i;
            }
        }
        return -1;
    }

    // --------------------------------------------------------------------- actions

    protected final Activity activity() {
        return Utils.getTopActivity();
    }

    protected abstract Pair<String, Integer>[] newMenuNames();

    protected abstract void onMenuItemSelected(CharSequence menuName, ApplicationInfo info) throws Throwable;
}
