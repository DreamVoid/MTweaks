package me.dreamvoid.mtweaks.xposed.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

/**
 * Reflection helpers replacing the removed {@code XposedHelpers}.
 * <p>
 * Method/constructor invocations go through {@link XposedInterface#getInvoker}, which bypasses
 * access checks. Field reads use plain reflection; static final writes fall back to
 * {@code sun.misc.Unsafe} because Android 16/17 rejects them through reflection.
 */
public final class Reflect {

    private static volatile XposedInterface sFramework;
    private static final Map<Object, Map<String, Object>> sAdditionalFields =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile Object sUnsafe;
    private static volatile long sFieldOffsetOffset = -1;
    private static volatile Method sUnsafeGetInt;
    private static volatile Method sUnsafePutObject;
    private static volatile Method sUnsafeObjectFieldOffset;

    private Reflect() {
    }

    public static void attachFramework(XposedInterface framework) {
        sFramework = framework;
    }

    public static XposedInterface framework() {
        XposedInterface framework = sFramework;
        if (framework == null) {
            throw new IllegalStateException("Xposed framework is not attached");
        }
        return framework;
    }

    public static class ClassNotFoundError extends NoClassDefFoundError {
        public ClassNotFoundError(String className) {
            super(className);
        }
    }

    public static class InvocationTargetError extends Error {
        public InvocationTargetError(Throwable cause) {
            super(cause);
        }
    }

    public static Class<?> findClass(String name, ClassLoader classLoader) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundError(name);
        }
    }

    public static Method findMethodExact(Class<?> cls, String name, Class<?>... parameterTypes) {
        for (Class<?> current = cls; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Class<?> current = cls; current != null; current = current.getSuperclass()) {
            for (Class<?> itf : current.getInterfaces()) {
                try {
                    Method method = itf.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        throw new NoSuchMethodError(cls.getName() + "#" + name + Arrays.toString(parameterTypes));
    }

    public static Constructor<?> findConstructorExact(Class<?> cls, Class<?>... parameterTypes) {
        try {
            Constructor<?> constructor = cls.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(cls.getName() + "(" + Arrays.toString(parameterTypes) + ")");
        }
    }

    public static Field findField(Class<?> cls, String name) {
        for (Class<?> current = cls; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldError(cls.getName() + "#" + name);
    }

    public static Object callMethod(Object obj, String name, Object... args) {
        if (obj == null) {
            throw new NullPointerException("obj == null for " + name);
        }
        Method method = findMethodBestMatch(obj.getClass(), name, args);
        try {
            return framework().getInvoker(method).invoke(obj, args);
        } catch (InvocationTargetException e) {
            throw new InvocationTargetError(e.getCause());
        } catch (Throwable e) {
            throw new InvocationTargetError(e);
        }
    }

    public static Object callStaticMethod(Class<?> cls, String name, Object... args) {
        Method method = findMethodBestMatch(cls, name, args);
        try {
            return framework().getInvoker(method).invoke(null, args);
        } catch (InvocationTargetException e) {
            throw new InvocationTargetError(e.getCause());
        } catch (Throwable e) {
            throw new InvocationTargetError(e);
        }
    }

    public static Object newInstance(Class<?> cls, Object... args) {
        Constructor<?> constructor = findConstructorBestMatch(cls, args);
        try {
            return framework().getInvoker(constructor).newInstance(args);
        } catch (InvocationTargetException e) {
            throw new InvocationTargetError(e.getCause());
        } catch (Throwable e) {
            throw new InvocationTargetError(e);
        }
    }

    public static Object getObjectField(Object obj, String name) {
        try {
            return findField(obj.getClass(), name).get(obj);
        } catch (IllegalAccessException e) {
            throw new InvocationTargetError(e);
        }
    }

    public static boolean getBooleanField(Object obj, String name) {
        try {
            return findField(obj.getClass(), name).getBoolean(obj);
        } catch (IllegalAccessException e) {
            throw new InvocationTargetError(e);
        }
    }

    public static void setStaticObjectField(Class<?> clazz, String name, Object value) {
        setStaticObjectField(findField(clazz, name), value);
    }

    public static void setStaticObjectField(Field field, Object value) {
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new IllegalArgumentException(field + " is not static");
        }
        field.setAccessible(true);
        try {
            field.set(null, value);
            return;
        } catch (IllegalAccessException | IllegalArgumentException ignored) {
            // Android 16+/17 rejects writes to static final fields, fall through to Unsafe.
        }
        try {
            Object unsafe = unsafe();
            field.get(null); // Resolve the field so that ART fills in Field.offset.
            int offset = (Integer) sUnsafeGetInt.invoke(unsafe, field,
                    fieldOffsetOffset(unsafe));
            if (offset <= 0) {
                throw new IllegalStateException("No resolved offset for " + field);
            }
            sUnsafePutObject.invoke(unsafe, field.getDeclaringClass(), (long) offset, value);
        } catch (ReflectiveOperationException e) {
            throw new InvocationTargetError(e);
        }
    }

    private static Object unsafe() throws ReflectiveOperationException {
        synchronized (Reflect.class) {
            if (sUnsafe != null) {
                return sUnsafe;
            }
            Class<?> cls = Class.forName("sun.misc.Unsafe");
            Field theUnsafe;
            try {
                theUnsafe = cls.getDeclaredField("THE_ONE");
            } catch (NoSuchFieldException e) {
                theUnsafe = cls.getDeclaredField("theUnsafe");
            }
            theUnsafe.setAccessible(true);
            sUnsafeGetInt = cls.getMethod("getInt", Object.class, long.class);
            sUnsafePutObject = cls.getMethod("putObject", Object.class, long.class, Object.class);
            sUnsafeObjectFieldOffset = cls.getMethod("objectFieldOffset", Field.class);
            sUnsafe = theUnsafe.get(null);
            return sUnsafe;
        }
    }

    private static long fieldOffsetOffset(Object unsafe) throws ReflectiveOperationException {
        long offset = sFieldOffsetOffset;
        if (offset >= 0) {
            return offset;
        }
        offset = (Long) sUnsafeObjectFieldOffset.invoke(unsafe, findField(Field.class, "offset"));
        sFieldOffsetOffset = offset;
        return offset;
    }

    public static void setAdditionalInstanceField(Object obj, String key, Object value) {
        if (obj == null) {
            throw new NullPointerException("object must not be null");
        }
        synchronized (sAdditionalFields) {
            Map<String, Object> fields = sAdditionalFields.get(obj);
            if (fields == null) {
                fields = new WeakHashMap<>();
                sAdditionalFields.put(obj, fields);
            }
            fields.put(key, value);
        }
    }

    public static Object getAdditionalInstanceField(Object obj, String key) {
        if (obj == null) {
            throw new NullPointerException("object must not be null");
        }
        synchronized (sAdditionalFields) {
            Map<String, Object> fields = sAdditionalFields.get(obj);
            return fields == null ? null : fields.get(key);
        }
    }

    private static Method findMethodBestMatch(Class<?> cls, String name, Object[] args) {
        Method best = null;
        int bestScore = -1;
        for (Class<?> current = cls; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                int score = score(method.getParameterTypes(), args);
                if (score > bestScore) {
                    bestScore = score;
                    best = method;
                }
            }
        }
        if (best == null) {
            throw new NoSuchMethodError(cls.getName() + "#" + name + Arrays.toString(args));
        }
        best.setAccessible(true);
        return best;
    }

    private static Constructor<?> findConstructorBestMatch(Class<?> cls, Object[] args) {
        Constructor<?> best = null;
        int bestScore = -1;
        for (Constructor<?> constructor : cls.getDeclaredConstructors()) {
            int score = score(constructor.getParameterTypes(), args);
            if (score > bestScore) {
                bestScore = score;
                best = constructor;
            }
        }
        if (best == null) {
            throw new NoSuchMethodError(cls.getName() + "(" + Arrays.toString(args) + ")");
        }
        best.setAccessible(true);
        return best;
    }

    private static int score(Class<?>[] types, Object[] args) {
        if (types.length != args.length) {
            return -1;
        }
        int score = 0;
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            Object arg = args[i];
            if (arg == null) {
                if (type.isPrimitive()) {
                    return -1;
                }
                score += 1;
                continue;
            }
            Class<?> argType = arg.getClass();
            if (type == argType) {
                score += 100;
            } else if (type.isAssignableFrom(argType)) {
                score += 10;
            } else if (type.isPrimitive() && box(type) == argType) {
                score += 8;
            } else {
                return -1;
            }
        }
        return score;
    }

    private static Class<?> box(Class<?> primitive) {
        return switch (primitive.getName()) {
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "boolean" -> Boolean.class;
            case "byte" -> Byte.class;
            case "char" -> Character.class;
            case "short" -> Short.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            default -> primitive;
        };
    }
}
