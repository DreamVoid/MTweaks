package me.dreamvoid.mtweaks.utils;

import android.util.Log;
import io.github.libxposed.api.XposedInterface;

/**
 * Logger for the modern (libxposed API 102) part of the module.
 * Falls back to android.util.Log when no framework is attached (module app process).
 */
public final class Logger {

    private static final String TAG = "MTweaks";
    private static volatile XposedInterface sXposed;

    private Logger() {
    }

    public static void attach(XposedInterface xposed) {
        sXposed = xposed;
    }

    public static void v(String msg) {
        log(Log.VERBOSE, msg, null);
    }

    public static void d(String msg) {
        log(Log.DEBUG, msg, null);
    }

    public static void i(String msg) {
        log(Log.INFO, msg, null);
    }

    public static void w(String msg) {
        log(Log.WARN, msg, null);
    }

    public static void e(String msg) {
        log(Log.ERROR, msg, null);
    }

    public static void e(String msg, Throwable tr) {
        log(Log.ERROR, msg, tr);
    }

    public static void stackTrace(Throwable tr) {
        log(Log.ERROR, "stackTrace", tr);
    }

    private static void log(int priority, String msg, Throwable tr) {
        XposedInterface xposed = sXposed;
        if (xposed != null) {
            try {
                if (tr == null) {
                    xposed.log(priority, TAG, msg);
                } else {
                    xposed.log(priority, TAG, msg, tr);
                }
                return;
            } catch (Throwable ignored) {
                // Fall through to android.util.Log.
            }
        }
        if (tr == null) {
            Log.println(priority, TAG, msg);
        } else {
            Log.println(priority, TAG, msg + '\n' + Log.getStackTraceString(tr));
        }
    }
}
