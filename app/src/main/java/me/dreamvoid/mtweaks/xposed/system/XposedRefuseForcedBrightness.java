package me.dreamvoid.mtweaks.xposed.system;

import android.util.SparseArray;
import io.github.libxposed.api.XposedInterface.HookHandle;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;
import me.dreamvoid.mtweaks.xposed.core.Reflect;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 拒绝应用通过窗口属性强制覆盖屏幕亮度，作用域为系统框架。
 */
@XposedLoad(packages = PackageNames.ANDROID, prefs = "key_display_refuse_forced_brightness")
public class XposedRefuseForcedBrightness extends FeatureModule {

    private static final String ROOT_WINDOW_CONTAINER = "com.android.server.wm.RootWindowContainer";

    private Field mScalarField;
    private Field mPerDisplayField;
    // 首次调用时字段的「无覆盖」值：Android 10 为 -1，Android 12+ 为 NaN，不同版本语义不同
    private Object mNoOverrideValue;
    private boolean mAccessFailureLogged;

    @Override
    protected void onLoad() {
        Class<?> rootWindowContainer;
        Field scalarField;
        Field perDisplayField;
        try {
            rootWindowContainer = findClass(ROOT_WINDOW_CONTAINER);
            scalarField = Reflect.findFieldIfExists(rootWindowContainer, "mScreenBrightnessOverride");
            if (scalarField == null) {
                scalarField = Reflect.findFieldIfExists(rootWindowContainer, "mScreenBrightness");
            }
            perDisplayField = Reflect.findFieldIfExists(rootWindowContainer, "mDisplayBrightnessOverrides");
        } catch (Throwable e) {
            Logger.w("RefuseForcedBrightness: can't inspect " + ROOT_WINDOW_CONTAINER + ": " + e);
            return;
        }
        if (scalarField == null && perDisplayField == null) {
            Logger.w("RefuseForcedBrightness: no brightness override field in " + ROOT_WINDOW_CONTAINER);
            return;
        }
        mScalarField = scalarField;
        mPerDisplayField = perDisplayField;

        try {
            List<HookHandle> handles = hookAllMethods(rootWindowContainer, "handleNotObscuredLocked", chain -> {
                Object container = chain.getThisObject();
                captureNoOverrideValue(container);
                try {
                    return chain.proceed();
                } finally {
                    refuseBrightnessOverride(container);
                }
            });
            if (handles.isEmpty()) {
                Logger.w("RefuseForcedBrightness: handleNotObscuredLocked not found in " + ROOT_WINDOW_CONTAINER);
            } else {
                Logger.i("RefuseForcedBrightness: hooked " + describeFields());
            }
        } catch (Throwable e) {
            Logger.w("RefuseForcedBrightness: can't hook handleNotObscuredLocked: " + e);
        }
    }

    private void captureNoOverrideValue(Object container) {
        if (mScalarField == null || mNoOverrideValue != null) {
            return;
        }
        try {
            mNoOverrideValue = mScalarField.get(container);
        } catch (IllegalAccessException e) {
            logAccessFailure();
        }
    }

    private void refuseBrightnessOverride(Object container) {
        if (mScalarField != null && mNoOverrideValue != null) {
            try {
                mScalarField.set(container, mNoOverrideValue);
            } catch (IllegalAccessException e) {
                logAccessFailure();
            }
        }
        if (mPerDisplayField != null) {
            try {
                if (mPerDisplayField.get(container) instanceof SparseArray<?> overrides) {
                    overrides.clear();
                }
            } catch (IllegalAccessException e) {
                logAccessFailure();
            }
        }
    }

    private void logAccessFailure() {
        if (!mAccessFailureLogged) {
            mAccessFailureLogged = true;
            Logger.w("RefuseForcedBrightness: can't access " + describeFields());
        }
    }

    private String describeFields() {
        return "scalar=" + (mScalarField == null ? "none" : mScalarField.getName()) + ", perDisplay=" + (mPerDisplayField == null ? "none" : mPerDisplayField.getName());
    }
}
