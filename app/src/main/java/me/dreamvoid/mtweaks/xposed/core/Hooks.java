package me.dreamvoid.mtweaks.xposed.core;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedInterface.Hooker;
import me.dreamvoid.mtweaks.utils.Logger;

/**
 * Thin helpers around the libxposed API 102 hook API.
 */
public final class Hooks {

    private Hooks() {
    }

    public static HookHandle hook(Executable executable, Hooker hooker) {
        return Reflect.framework()
                .hook(executable)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(hooker);
    }

    public static List<HookHandle> hookAllMethods(Class<?> cls, String methodName, Hooker hooker) {
        List<HookHandle> handles = new ArrayList<>();
        for (Method method : cls.getDeclaredMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            if (Modifier.isNative(method.getModifiers())) {
                continue;
            }
            try {
                handles.add(hook(method, hooker));
            } catch (Throwable e) {
                Logger.w("Failed to hook " + cls.getName() + "#" + methodName + ": " + e);
            }
        }
        return handles;
    }

    public static HookHandle findAndHookMethod(Class<?> cls, String methodName,
                                               Class<?>[] parameterTypes, Hooker hooker) {
        return hook(Reflect.findMethodExact(cls, methodName, parameterTypes), hooker);
    }

    public static HookHandle findAndHookConstructor(Class<?> cls, Class<?>[] parameterTypes,
                                                    Hooker hooker) {
        return hook(Reflect.findConstructorExact(cls, parameterTypes), hooker);
    }
}
