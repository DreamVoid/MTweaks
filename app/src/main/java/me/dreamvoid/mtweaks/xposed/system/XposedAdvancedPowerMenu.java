package me.dreamvoid.mtweaks.xposed.system;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Binder;
import android.os.PowerManager;
import android.os.Process;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.R;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;
import me.dreamvoid.mtweaks.xposed.core.Hooks;
import me.dreamvoid.mtweaks.xposed.core.Reflect;

import java.util.ArrayList;
import java.util.List;

/**
 * 将电源菜单的“重启”替换为 LineageOS 风格的高级电源菜单。
 */
@XposedLoad(packages = {PackageNames.ANDROID, PackageNames.SYSTEMUI}, prefs = "key_system_advanced_power_menu")
public class XposedAdvancedPowerMenu extends FeatureModule {

    private static final String RESTART_MESSAGE = "重启…";
    private static final int HALT_MODE_REBOOT = 1;
    private static final int PER_USER_RANGE = 100000;
    private static final String REASON_RECOVERY = "recovery";
    private static final String REASON_FASTBOOT = "fastboot";
    private static final String REASON_BOOTLOADER = "bootloader";

    private volatile int mSystemUiUid = -1;
    private volatile boolean mSystemUiUidResolved;
    private Resources mModuleResources;
    private volatile Dialog mHostDialog;

    @Override
    protected void onLoad() {
        if (context().systemServer()) {
            hookPowerManagerReboot();
        } else if (PackageNames.SYSTEMUI.equals(context().packageName())) {
            hookGlobalActions();
        }
    }

    private void hookGlobalActions() {
        Class<?> dialogClass;
        Class<?> restartActionClass;
        try {
            dialogClass = findClass("com.android.systemui.globalactions.GlobalActionsDialogLite");
            restartActionClass = findClass("com.android.systemui.globalactions.GlobalActionsDialogLite$RestartAction");
        } catch (Throwable e) {
            Logger.w("AdvancedPowerMenu: GlobalActionsDialogLite not found: " + e);
            return;
        }
        hookRestartAction(restartActionClass);
        hookAdapters(dialogClass, restartActionClass);
    }

    private void hookRestartAction(Class<?> restartActionClass) {
        int hooks = 0;
        for (var constructor : restartActionClass.getDeclaredConstructors()) {
            try {
                Hooks.hook(constructor, chain -> {
                    Object result = chain.proceed();
                    Object action = chain.getThisObject();
                    applyRestartAction(action, findContext(action));
                    return result;
                });
                hooks++;
            } catch (Throwable e) {
                Logger.w("AdvancedPowerMenu: can't hook RestartAction constructor: " + e);
            }
        }
        try {
            hooks += hookAllMethods(restartActionClass, "onPress", chain -> {
                if (showRestartMenu(chain.getThisObject())) {
                    return null;
                }
                Logger.d("AdvancedPowerMenu: falling back to stock restart");
                return chain.proceed();
            }).size();
        } catch (Throwable e) {
            Logger.w("AdvancedPowerMenu: can't hook RestartAction onPress: " + e);
        }
        Logger.i("AdvancedPowerMenu: hooked " + hooks + " restart action member(s)");
    }

    private void hookAdapters(Class<?> dialogClass, Class<?> restartActionClass) {
        Class<?> adapterClass;
        try {
            adapterClass = findClass(dialogClass.getName() + "$MyAdapter");
        } catch (Throwable e) {
            Logger.w("AdvancedPowerMenu: MyAdapter not found: " + e);
            return;
        }
        int hooks = 0;
        try {
            hooks += hookAllMethods(adapterClass, "getView", chain -> {
                Object result = chain.proceed();
                if (result instanceof View view) {
                    int position = (int) chain.getArg(0);
                    Object item = getAdapterItem(chain.getThisObject(), position);
                    if (restartActionClass.isInstance(item)) {
                        applyRestartView(view);
                        // 接管点击：原对话框保持显示，直接在其窗口内换成重启网格
                        view.setOnClickListener(v -> showRestartMenu(item));
                    }
                }
                return result;
            }).size();
        } catch (Throwable e) {
            Logger.w("AdvancedPowerMenu: can't hook MyAdapter getView: " + e);
        }
        if (hooks > 0) {
            Logger.i("AdvancedPowerMenu: hooked MyAdapter (" + hooks + ")");
        }
    }

    private void applyRestartAction(Object action, Context context) {
        if (action == null) {
            return;
        }
        try {
            Reflect.setObjectField(action, "mMessage", RESTART_MESSAGE);
        } catch (Throwable e) {
            Logger.w("AdvancedPowerMenu: can't set restart label: " + e);
        }
        if (context == null) {
            return;
        }
        Drawable icon = moduleDrawable(context, R.drawable.ic_restart_more);
        if (icon == null) {
            return;
        }
        try {
            Reflect.setObjectField(action, "mIcon", icon);
        } catch (Throwable e) {
            Logger.w("AdvancedPowerMenu: can't set restart icon: " + e);
        }
    }

    private void applyRestartView(View view) {
        if (view == null) {
            return;
        }
        Context context = view.getContext();
        View iconView = view.findViewById(android.R.id.icon);
        if (iconView instanceof ImageView icon) {
            Drawable drawable = moduleDrawable(context, R.drawable.ic_restart_more);
            if (drawable != null) {
                icon.setImageDrawable(drawable);
                icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
        }
        View messageView = view.findViewById(android.R.id.message);
        if (messageView instanceof TextView message) {
            message.setSelected(true);
            message.setText(RESTART_MESSAGE);
        }
    }

    private boolean showRestartMenu(Object source) {
        Object outer = findOuter(source);
        if (outer == null) {
            return false;
        }
        Context foundContext = findContext(outer);
        if (foundContext == null) {
            foundContext = findContext(source);
        }
        if (foundContext == null) {
            Logger.w("AdvancedPowerMenu: no context for restart menu");
            return false;
        }
        final Context context = foundContext;

        if (mHostDialog != null && mHostDialog.isShowing()) {
            return true;
        }
        List<MenuItem> items = buildMenuItems(context, outer);
        return showRestartMenuInPlace(outer, items);
    }

    private List<MenuItem> buildMenuItems(final Context context, Object outer) {
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem("重启系统", frameworkDrawable(context, "ic_restart"), () -> rebootSystem(outer, context)));
        items.add(new MenuItem("恢复模式", moduleIconOrDefault(context, R.drawable.ic_lock_restart_recovery), () -> rebootWithReason(context, REASON_RECOVERY)));
        items.add(new MenuItem("Fastbootd", moduleIconOrDefault(context, R.drawable.ic_lock_restart_fastboot), () -> rebootWithReason(context, REASON_FASTBOOT)));
        items.add(new MenuItem("Bootloader", moduleIconOrDefault(context, R.drawable.ic_lock_restart_bootloader), () -> rebootWithReason(context, REASON_BOOTLOADER)));
        items.add(new MenuItem("系统界面", moduleIconOrDefault(context, R.drawable.ic_android_head), this::restartSystemUi));
        return items;
    }

    /**
     * 在原版电源菜单的 GlobalActionsLayoutLite 内换成重启网格，
     * 让原生的 alpha / 位移 / 模糊 / 调暗动画直接作用到我们的内容上。
     */
    private boolean showRestartMenuInPlace(Object outer, List<MenuItem> items) {
        try {
            Object delegate = Reflect.getObjectField(outer, "mDelegate");
            if (delegate == null) {
                return false;
            }
            Object dialogObject = Reflect.getObjectField(delegate, "mCurrentDialog");
            if (!(dialogObject instanceof Dialog dialog) || !dialog.isShowing()) {
                return false;
            }
            Object layoutObject = Reflect.getObjectField(delegate, "mGlobalActionsLayout");
            if (!(layoutObject instanceof ViewGroup)) {
                return false;
            }
            View listObject = dialog.findViewById(android.R.id.list);
            if (!(listObject instanceof ViewGroup listView)) {
                return false;
            }

            // 用对话框自己的主题上下文，否则取不到 Monet 动态色
            Context themedContext = dialog.getContext();
            View panel = buildRestartPanel(themedContext, items);
            listView.removeAllViews();
            listView.addView(panel, createCenteredLayoutParams(listView));

            View overflow = dialog.findViewById(themedContext.getResources().getIdentifier("global_actions_overflow_button", "id", themedContext.getPackageName()));
            if (overflow != null) {
                overflow.setVisibility(View.GONE);
            }

            mHostDialog = dialog;
            playNativeAnimation(delegate, dialog, true);
            Logger.i("AdvancedPowerMenu: restart menu shown in place");
            return true;
        } catch (Throwable e) {
            Logger.w("AdvancedPowerMenu: can't replace global actions content: " + e);
            return false;
        }
    }

    /**
     * 复用 SystemUI 自己的 startAnimation，产生与原版一致的淡入/位移效果。
     */
    private void playNativeAnimation(Object delegate, Dialog dialog, boolean showing) {
        try {
            Runnable noop = () -> {
            };
            // 回调参数实际是 Float，不能声明成 Consumer<Integer>
            java.util.function.Consumer<Object> state = value -> {
            };
            Reflect.callMethod(delegate, "startAnimation", dialog, showing, noop, noop, state);
        } catch (Throwable e) {
            Logger.d("AdvancedPowerMenu: can't play native animation: " + e);
        }
    }

    private View buildRestartPanel(Context context, List<MenuItem> items) {
        Resources resources = context.getResources();
        String packageName = context.getPackageName();
        int itemLayout = resources.getIdentifier("global_actions_grid_item_lite", "layout", packageName);
        LayoutInflater inflater = LayoutInflater.from(context);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setClickable(true);

        int cellMinWidth = dp(context, 96);
        int cellGap = dp(context, 24);
        for (int start = 0; start < items.size(); start += 2) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = start; i < start + 2 && i < items.size(); i++) {
                View cell = createRestartCell(context, inflater, itemLayout, items.get(i), row);
                cell.setMinimumWidth(cellMinWidth);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > start) {
                    params.setMarginStart(cellGap);
                }
                row.addView(cell, params);
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.gravity = Gravity.CENTER_HORIZONTAL;
            if (start + 2 < items.size()) {
                rowParams.bottomMargin = cellGap;
            }
            panel.addView(row, rowParams);
        }
        return panel;
    }

    /**
     * 在 ConstraintLayout 里居中放置面板，用反射构造，避免引入 androidx 依赖。
     */
    private static ViewGroup.LayoutParams createCenteredLayoutParams(ViewGroup parent) {
        try {
            Class<?> paramsClass = Class.forName("androidx.constraintlayout.widget.ConstraintLayout$LayoutParams", false, parent.getClass().getClassLoader());
            Object params = paramsClass.getConstructor(int.class, int.class).newInstance(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            for (String field : new String[]{"topToTop", "bottomToBottom", "leftToLeft", "rightToRight", "startToStart", "endToEnd"}) {
                try {
                    paramsClass.getField(field).setInt(params, -1);
                } catch (Throwable ignored) {
                }
            }
            return (ViewGroup.LayoutParams) params;
        } catch (Throwable e) {
            return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void dismissRestartMenu() {
        Dialog dialog = mHostDialog;
        mHostDialog = null;
        if (dialog != null && dialog.isShowing()) {
            try {
                // 走 SystemUIDialog 的原生 dismiss，它自身会做淡出/模糊/调暗动画
                dialog.dismiss();
            } catch (Throwable ignored) {
            }
        }
    }

    private View createRestartCell(Context context, LayoutInflater inflater, int layoutId, MenuItem item, ViewGroup parent) {
        View view = layoutId == 0 ? null : inflater.inflate(layoutId, parent, false);
        if (view == null) {
            LinearLayout fallback = new LinearLayout(context);
            fallback.setOrientation(LinearLayout.VERTICAL);
            fallback.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(context);
            icon.setId(android.R.id.icon);
            fallback.addView(icon, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
            TextView text = new TextView(context);
            text.setId(android.R.id.message);
            text.setGravity(Gravity.CENTER);
            fallback.addView(text);
            view = fallback;
        }
        View iconView = view.findViewById(android.R.id.icon);
        if (iconView instanceof ImageView icon) {
            if (item.icon != null) {
                icon.setVisibility(View.VISIBLE);
                icon.setImageDrawable(item.icon);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            } else {
                icon.setVisibility(View.GONE);
            }
        }
        View messageView = view.findViewById(android.R.id.message);
        if (messageView instanceof TextView message) {
            message.setSelected(true);
            message.setText(item.label);
        }
        view.setOnClickListener(v -> {
            dismissRestartMenu();
            item.action.run();
        });
        return view;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void rebootSystem(Object outer, Context context) {
        try {
            Object manager = Reflect.getObjectField(outer, "mWindowManagerFuncs");
            try {
                Reflect.callMethod(manager, "reboot", false);
            } catch (NoSuchMethodError e) {
                Reflect.callMethod(manager, "reboot", false, null);
            }
            return;
        } catch (Throwable e) {
            Logger.w("AdvancedPowerMenu: global actions reboot failed: " + e);
        }
        rebootWithReason(context, null);
    }

    private void rebootWithReason(Context context, String reason) {
        PowerManager powerManager;
        try {
            powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        } catch (Throwable e) {
            powerManager = null;
        }
        if (powerManager == null) {
            return;
        }
        PowerManager finalPowerManager = powerManager;
        new Thread(() -> {
            try {
                finalPowerManager.reboot(reason);
            } catch (Throwable e) {
                Logger.w("AdvancedPowerMenu: reboot(" + reason + ") failed: " + e);
            }
        }, "MTweaks-reboot").start();
    }

    private void restartSystemUi() {
        Logger.i("AdvancedPowerMenu: restarting SystemUI");
        Process.killProcess(Process.myPid());
    }

    private Drawable moduleIconOrDefault(Context context, int resId) {
        Drawable drawable = moduleDrawable(context, resId);
        return drawable != null ? drawable : frameworkDrawable(context, "ic_restart");
    }

    private Drawable frameworkDrawable(Context context, String name) {
        try {
            Resources resources = context.getResources();
            int id = resources.getIdentifier(name, "drawable", "android");
            if (id != 0) {
                return context.getDrawable(id);
            }
            resources = Resources.getSystem();
            id = resources.getIdentifier(name, "drawable", "android");
            return id == 0 ? null : resources.getDrawable(id, null);
        } catch (Throwable e) {
            Logger.d("AdvancedPowerMenu: framework drawable " + name + " unavailable: " + e);
            return null;
        }
    }

    private Drawable moduleDrawable(Context context, int resId) {
        try {
            Resources resources = moduleResources(context);
            return resources == null ? null : resources.getDrawable(resId, null);
        } catch (Throwable e) {
            Logger.w("AdvancedPowerMenu: module drawable " + resId + " unavailable: " + e);
            return null;
        }
    }

    private synchronized Resources moduleResources(Context context) throws Exception {
        Resources resources = mModuleResources;
        if (resources != null) {
            return resources;
        }
        ApplicationInfo info = Reflect.framework().getModuleApplicationInfo();
        resources = context.getPackageManager().getResourcesForApplication(info);
        mModuleResources = resources;
        return resources;
    }

    private Context findContext(Object object) {
        if (object == null) {
            return null;
        }
        try {
            Object value = Reflect.getObjectField(object, "mContext");
            if (value instanceof Context context) {
                return context;
            }
        } catch (Throwable ignored) {
        }
        Object outer = findOuter(object);
        if (outer != null) {
            try {
                Object value = Reflect.getObjectField(outer, "mContext");
                if (value instanceof Context context) {
                    return context;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Object application = Reflect.callStaticMethod(findClass("android.app.ActivityThread"), "currentApplication");
            if (application instanceof Context context) {
                return context;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object findOuter(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return Reflect.getObjectField(object, "this$0");
        } catch (Throwable e) {
            return null;
        }
    }

    private static Object getAdapterItem(Object adapter, int position) {
        try {
            return Reflect.callMethod(adapter, "getItem", position);
        } catch (Throwable e) {
            return null;
        }
    }

    private static boolean isAdvancedReason(String reason) {
        return reason == null || REASON_RECOVERY.equals(reason) || REASON_FASTBOOT.equals(reason) || REASON_BOOTLOADER.equals(reason);
    }

    private void hookPowerManagerReboot() {
        try {
            Class<?> binderService = findClass("com.android.server.power.PowerManagerService$BinderService");
            Hooks.findAndHookMethod(binderService, "reboot", new Class<?>[]{boolean.class, String.class, boolean.class}, chain -> {
                String reason = (String) chain.getArg(1);
                if (!isAdvancedReason(reason)) {
                    return chain.proceed();
                }
                Object service = null;
                try {
                    service = Reflect.getObjectField(chain.getThisObject(), "this$0");
                } catch (Throwable ignored) {
                }
                if (service == null || !isSystemUiCaller(service)) {
                    return chain.proceed();
                }
                boolean confirm = (boolean) chain.getArg(0);
                boolean wait = (boolean) chain.getArg(2);
                long identity = Binder.clearCallingIdentity();
                try {
                    Reflect.callMethod(service, "shutdownOrRebootInternal", HALT_MODE_REBOOT, confirm, reason, wait);
                } catch (Throwable e) {
                    Logger.w("AdvancedPowerMenu: shutdownOrRebootInternal failed: " + e);
                } finally {
                    Binder.restoreCallingIdentity(identity);
                }
                return null;
            });
            Logger.i("AdvancedPowerMenu: hooked PowerManagerService reboot");
        } catch (Throwable e) {
            Logger.w("AdvancedPowerMenu: can't hook PowerManagerService reboot: " + e);
        }
    }

    private boolean isSystemUiCaller(Object powerManagerService) {
        int uid = mSystemUiUid;
        if (!mSystemUiUidResolved) {
            synchronized (this) {
                if (!mSystemUiUidResolved) {
                    uid = resolveSystemUiUid(powerManagerService);
                    mSystemUiUid = uid;
                    mSystemUiUidResolved = true;
                } else {
                    uid = mSystemUiUid;
                }
            }
        }
        return uid >= 0 && Binder.getCallingUid() % PER_USER_RANGE == uid % PER_USER_RANGE;
    }

    private int resolveSystemUiUid(Object powerManagerService) {
        try {
            Context context = (Context) Reflect.getObjectField(powerManagerService, "mContext");
            return context.getPackageManager().getPackageUid(PackageNames.SYSTEMUI, 0);
        } catch (Throwable e) {
            Logger.w("AdvancedPowerMenu: can't resolve SystemUI uid: " + e);
            return -1;
        }
    }

    private static final class MenuItem {
        final CharSequence label;
        final Drawable icon;
        final Runnable action;

        MenuItem(CharSequence label, Drawable icon, Runnable action) {
            this.label = label;
            this.icon = icon;
            this.action = action;
        }
    }
}
