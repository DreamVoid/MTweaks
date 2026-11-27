package me.dreamvoid.mtweaks.xposed.system;

import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Process;
import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.HookHandle;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;
import me.dreamvoid.mtweaks.xposed.core.Hooks;
import me.dreamvoid.mtweaks.xposed.core.Reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/**
 * 允许截图与录屏捕获设置了 FLAG_SECURE 的界面。
 */
@XposedLoad(packages = {PackageNames.ANDROID, PackageNames.SYSTEMUI}, prefs = "key_capture_force_screen_capture")
public class XposedForceScreenCapture extends FeatureModule {

    private static final String[] SURFACE_CREATION_METHODS = {
            "setInitialSurfaceControlProperties", "createSurfaceLocked"
    };
    private static final String[] CAPTURE_CLASSES = {
            "android.window.ScreenCaptureInternal",
            "android.window.ScreenCapture",
            "android.view.SurfaceControl"
    };
    private static final String[] SCREENSHOT_BUFFER_CLASSES = {
            "android.window.ScreenCaptureInternal$ScreenshotHardwareBuffer",
            "android.window.ScreenCapture$ScreenshotHardwareBuffer",
            "android.view.SurfaceControl$ScreenshotHardwareBuffer"
    };
    private static final String[] DISPLAY_CONTROL_METHODS = {"createVirtualDisplay", "createDisplay"};

    private final Map<Class<?>, Field> mSecureCaptureFields = new ConcurrentHashMap<>();
    private final Set<Class<?>> mNoSecureCaptureFields = ConcurrentHashMap.newKeySet();
    private volatile boolean mCaptureFieldFailureLogged;

    @Override
    protected void onLoad() {
        if (context().systemServer()) {
            loadSystemServer();
        } else if (PackageNames.SYSTEMUI.equals(context().packageName())) {
            loadSystemUi();
        }
    }

    private void loadSystemServer() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Logger.w("ForceScreenCapture: Android 12+ required");
            return;
        }
        deoptimizeSystemServer();
        hookWindowState();
        hookCaptureArgs();
        hookScreenshotHardwareBuffer();
        hookDisplayCreation();
        hookVirtualDisplayAdapter();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            hookActivityManagerService();
        }
    }

    private void loadSystemUi() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return;
        }
        try {
            int hooks = hookCaptureArgs(findClass("android.view.SurfaceControl"));
            if (hooks == 0) {
                Logger.w("ForceScreenCapture: no SystemUI capture methods found");
            } else {
                Logger.i("ForceScreenCapture: hooked " + hooks + " SystemUI capture method(s)");
            }
        } catch (Throwable e) {
            Logger.w("ForceScreenCapture: can't hook SystemUI capture args: " + e);
        }
    }

    private void deoptimizeSystemServer() {
        deoptimizeMethods("com.android.server.wm.WindowStateAnimator", "createSurfaceLocked");
        deoptimizeMethods("com.android.server.wm.WindowManagerService", "relayoutWindow");
        for (int i = 0; i < 20; i++) {
            try {
                Class<?> cls = findClass("com.android.server.wm.RootWindowContainer$$ExternalSyntheticLambda" + i);
                if (BiConsumer.class.isAssignableFrom(cls)) {
                    deoptimizeMethods(cls, "accept");
                }
            } catch (Throwable ignored) {
            }
            try {
                Class<?> cls = findClass("com.android.server.wm.DisplayContent$" + i);
                if (BiPredicate.class.isAssignableFrom(cls)) {
                    deoptimizeMethods(cls, "test");
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void deoptimizeMethods(String className, String... methodNames) {
        try {
            deoptimizeMethods(findClass(className), methodNames);
        } catch (Throwable ignored) {
        }
    }

    private void deoptimizeMethods(Class<?> cls, String... methodNames) {
        for (Method method : cls.getDeclaredMethods()) {
            if (Modifier.isAbstract(method.getModifiers()) || Modifier.isNative(method.getModifiers())) {
                continue;
            }
            for (String methodName : methodNames) {
                if (!methodName.equals(method.getName())) {
                    continue;
                }
                try {
                    if (!Reflect.framework().deoptimize(method)) {
                        Logger.d("ForceScreenCapture: deoptimize skipped " + cls.getName() + "#" + methodName);
                    }
                } catch (Throwable e) {
                    Logger.w("ForceScreenCapture: deoptimize failed " + cls.getName() + "#" + methodName + ": " + e);
                }
            }
        }
    }

    private void hookWindowState() {
        try {
            Class<?> windowState = findClass("com.android.server.wm.WindowState");
            Method method = Reflect.findMethodExact(windowState, "isSecureLocked");
            Hooks.hook(method, chain -> {
                if (isSurfaceCreationCall()) {
                    return chain.proceed();
                }
                return false;
            });
            Logger.i("ForceScreenCapture: hooked WindowState#isSecureLocked");
        } catch (Throwable e) {
            Logger.w("ForceScreenCapture: can't hook WindowState#isSecureLocked: " + e);
        }
    }

    private boolean isSurfaceCreationCall() {
        ClassLoader systemServer = loader();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                        .walk(frames -> frames.anyMatch(frame -> {
                            Class<?> cls = frame.getDeclaringClass();
                            return cls != null
                                    && cls.getClassLoader() == systemServer
                                    && isSurfaceCreationMethod(frame.getMethodName());
                        }));
            } catch (Throwable ignored) {
                // Fall back to the Throwable stack below.
            }
        }
        for (StackTraceElement frame : new Throwable().getStackTrace()) {
            if (!isSurfaceCreationMethod(frame.getMethodName())) {
                continue;
            }
            try {
                Class<?> cls = Class.forName(frame.getClassName(), false, systemServer);
                if (cls.getClassLoader() == systemServer) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static boolean isSurfaceCreationMethod(String name) {
        for (String method : SURFACE_CREATION_METHODS) {
            if (method.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void hookCaptureArgs() {
        int hooks = 0;
        for (String className : CAPTURE_CLASSES) {
            try {
                hooks += hookCaptureArgs(findClass(className));
            } catch (Throwable ignored) {
            }
        }
        if (hooks == 0) {
            Logger.w("ForceScreenCapture: no screen capture methods found");
        } else {
            Logger.i("ForceScreenCapture: hooked " + hooks + " screen capture method(s)");
        }
    }

    private int hookCaptureArgs(Class<?> cls) {
        int hooks = 0;
        hooks += Hooks.hookAllMethodsIncludingNative(cls, "nativeCaptureDisplay", this::forceSecureCaptureArgs).size();
        hooks += Hooks.hookAllMethodsIncludingNative(cls, "nativeCaptureLayers", this::forceSecureCaptureArgs).size();
        hooks += Hooks.hookAllMethods(cls, "captureDisplay", this::forceSecureCaptureArgs).size();
        hooks += Hooks.hookAllMethods(cls, "captureLayers", this::forceSecureCaptureArgs).size();
        return hooks;
    }

    private Object forceSecureCaptureArgs(Chain chain) throws Throwable {
        List<Object> args = chain.getArgs();
        if (!args.isEmpty()) {
            applySecureCaptureField(args.get(0));
        }
        return chain.proceed();
    }

    private void applySecureCaptureField(Object captureArgs) {
        if (captureArgs == null) {
            return;
        }
        Class<?> cls = captureArgs.getClass();
        Field field = mSecureCaptureFields.get(cls);
        if (field == null) {
            if (mNoSecureCaptureFields.contains(cls)) {
                return;
            }
            field = findFieldInHierarchy(cls, "mSecureContentPolicy");
            if (field == null) {
                field = findFieldInHierarchy(cls, "mCaptureSecureLayers");
            }
            if (field == null) {
                mNoSecureCaptureFields.add(cls);
                if (cls.getName().contains("CaptureArgs")) {
                    Logger.w("ForceScreenCapture: no secure field in " + cls.getName());
                }
                return;
            }
            mSecureCaptureFields.put(cls, field);
        }
        try {
            if (field.getType() == int.class) {
                field.setInt(captureArgs, 1);
            } else if (field.getType() == boolean.class) {
                field.setBoolean(captureArgs, true);
            }
        } catch (Throwable e) {
            if (!mCaptureFieldFailureLogged) {
                mCaptureFieldFailureLogged = true;
                Logger.w("ForceScreenCapture: can't set " + field.getName() + ": " + e);
            }
        }
    }

    private static Field findFieldInHierarchy(Class<?> cls, String name) {
        for (Class<?> current = cls; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private void hookScreenshotHardwareBuffer() {
        int hooks = 0;
        for (String className : SCREENSHOT_BUFFER_CLASSES) {
            try {
                hooks += Hooks.hookAllMethods(findClass(className), "containsSecureLayers", chain -> false).size();
            } catch (Throwable ignored) {
            }
        }
        if (hooks == 0) {
            Logger.w("ForceScreenCapture: containsSecureLayers not found");
        } else {
            Logger.i("ForceScreenCapture: hooked " + hooks + " containsSecureLayers method(s)");
        }
    }

    private void hookDisplayCreation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            int hooks = 0;
            for (String methodName : DISPLAY_CONTROL_METHODS) {
                hooks += hookDisplayControl(methodName);
            }
            if (hooks == 0) {
                Logger.w("ForceScreenCapture: DisplayControl create method not found");
            } else {
                Logger.i("ForceScreenCapture: hooked " + hooks + " DisplayControl create method(s)");
            }
        } else {
            hookSurfaceControlCreateDisplay();
        }
    }

    private int hookDisplayControl(String methodName) {
        try {
            Class<?> cls = findClass("com.android.server.display.DisplayControl");
            Method method = Reflect.findMethodExact(cls, methodName, String.class, boolean.class);
            Hooks.hook(method, chain -> {
                Object[] args = chain.getArgs().toArray();
                args[1] = true;
                return chain.proceed(args);
            });
            return 1;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void hookSurfaceControlCreateDisplay() {
        try {
            Class<?> cls = findClass("android.view.SurfaceControl");
            Method method = Reflect.findMethodExact(cls, "createDisplay", String.class, boolean.class);
            Hooks.hook(method, chain -> {
                if (isCalledFromVirtualDisplayAdapter()) {
                    return chain.proceed();
                }
                Object[] args = chain.getArgs().toArray();
                args[1] = true;
                return chain.proceed(args);
            });
            Logger.i("ForceScreenCapture: hooked SurfaceControl#createDisplay");
        } catch (Throwable e) {
            Logger.w("ForceScreenCapture: can't hook SurfaceControl#createDisplay: " + e);
        }
    }

    private boolean isCalledFromVirtualDisplayAdapter() {
        ClassLoader systemServer = loader();
        for (StackTraceElement frame : new Throwable().getStackTrace()) {
            if (!"createVirtualDisplayLocked".equals(frame.getMethodName())) {
                continue;
            }
            try {
                Class<?> cls = Class.forName(frame.getClassName(), false, systemServer);
                if (cls.getClassLoader() == systemServer) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private void hookVirtualDisplayAdapter() {
        try {
            Class<?> cls = findClass("com.android.server.display.VirtualDisplayAdapter");
            List<HookHandle> handles = Hooks.hookAllMethods(cls, "createVirtualDisplayLocked", chain -> {
                List<Object> args = chain.getArgs();
                if (args.size() < 3 || !(args.get(2) instanceof Integer ownerUid)) {
                    return chain.proceed();
                }
                Object projection = args.get(1);
                if (ownerUid >= Process.FIRST_APPLICATION_UID && projection == null) {
                    return chain.proceed();
                }
                for (int i = 3; i < args.size(); i++) {
                    if (args.get(i) instanceof Integer flags) {
                        Object[] newArgs = args.toArray();
                        newArgs[i] = flags | DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE;
                        return chain.proceed(newArgs);
                    }
                }
                Logger.w("ForceScreenCapture: flags not found in VirtualDisplayAdapter#createVirtualDisplayLocked");
                return chain.proceed();
            });
            if (handles.isEmpty()) {
                Logger.w("ForceScreenCapture: createVirtualDisplayLocked not found");
            } else {
                Logger.i("ForceScreenCapture: hooked " + handles.size() + " createVirtualDisplayLocked method(s)");
            }
        } catch (Throwable e) {
            Logger.w("ForceScreenCapture: can't hook VirtualDisplayAdapter: " + e);
        }
    }

    private void hookActivityManagerService() {
        try {
            Class<?> cls = findClass("com.android.server.am.ActivityManagerService");
            Hooks.findAndHookMethod(cls, "checkPermission",
                    new Class<?>[]{String.class, int.class, int.class}, chain -> {
                        if ("android.permission.CAPTURE_BLACKOUT_CONTENT".equals(chain.getArg(0))) {
                            Object[] args = chain.getArgs().toArray();
                            args[0] = "android.permission.READ_FRAME_BUFFER";
                            return chain.proceed(args);
                        }
                        return chain.proceed();
                    });
            Logger.i("ForceScreenCapture: hooked ActivityManagerService#checkPermission");
        } catch (Throwable e) {
            Logger.w("ForceScreenCapture: can't hook ActivityManagerService#checkPermission: " + e);
        }
    }
}
