package me.dreamvoid.mtweaks.xposed.system;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.StringBuilderPrinter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import li.lingfeng.ltweaks.lib.XposedLoad;
import li.lingfeng.ltweaks.prefs.ClassNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.utils.ViewUtils;
import me.dreamvoid.mtweaks.xposed.core.Reflect;
import me.dreamvoid.mtweaks.xposed.core.XposedCommon;

@XposedLoad(packages = {}, prefs = "key_debug_app_crash_info")
public class XposedAppCrashInfo extends XposedCommon {

    private static final String APP_ERRORS = "com.android.server.am.AppErrors";
    private static final String APP_ERROR_DIALOG = "com.android.server.am.AppErrorDialog";
    private static final String APP_ERROR_DIALOG_DATA = APP_ERROR_DIALOG + "$Data";

    private Object mCrashInfo;

    @Override
    protected void onLoad() {
        if (context().systemServer()) {
            hookAndroid();
        } else if (isUserInstalledApp()) {
            hookUserInstalledApp();
        }
    }

    private void hookAndroid() {
        hookAllMethods(APP_ERRORS, "crashApplicationInner", chain -> {
            mCrashInfo = chain.getArg(1);
            try {
                return chain.proceed();
            } finally {
                mCrashInfo = null;
            }
        });

        findAndHookConstructor(APP_ERROR_DIALOG_DATA, new Class<?>[0], chain -> {
            if (mCrashInfo == null) {
                Logger.w("No crash info when constructing error dialog data.");
                return chain.proceed();
            }
            Logger.d("Attach crash info to error dialog data.");
            Reflect.setAdditionalInstanceField(chain.getThisObject(), "crashInfo", mCrashInfo);
            mCrashInfo = null;
            return chain.proceed();
        });

        findAndHookConstructor(APP_ERROR_DIALOG,
                new Class<?>[]{Context.class, findClass(ClassNames.ACTIVITY_MANAGER_SERVICE), findClass(APP_ERROR_DIALOG_DATA)},
                chain -> {
                    mCrashInfo = Reflect.getAdditionalInstanceField(chain.getArg(2), "crashInfo");
                    if (mCrashInfo == null) {
                        Logger.w("No crash info when constructing error dialog.");
                    }
                    return chain.proceed();
                });

        findAndHookMethod(APP_ERROR_DIALOG, "onCreate", new Class<?>[]{Bundle.class}, chain -> {
            Object result = chain.proceed();
            Logger.d("Append crash info button.");
            final Dialog dialog = (Dialog) chain.getThisObject();
            FrameLayout frame = dialog.findViewById(android.R.id.custom);
            final LinearLayout linearLayout = ViewUtils.findViewByType(frame, LinearLayout.class);
            final Button button = new Button(dialog.getContext());
            button.setText("Show crash info");
            button.setOnClickListener(v -> {
                showCrashInfo(dialog, linearLayout);
                linearLayout.removeView(button);
            });
            linearLayout.addView(button);
            return result;
        });

        findAndHookMethod(APP_ERROR_DIALOG, "onStop", new Class<?>[0], chain -> {
            mCrashInfo = null;
            return chain.proceed();
        });
    }

    private void showCrashInfo(Dialog dialog, LinearLayout linearLayout) {
        if (mCrashInfo == null) {
            Toast.makeText(dialog.getContext(), "No crash info.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder stringBuilder = new StringBuilder();
        StringBuilderPrinter printer = new StringBuilderPrinter(stringBuilder);
        Reflect.callMethod(mCrashInfo, "dump", printer, "");

        TextView textView = new TextView(dialog.getContext());
        textView.setText(stringBuilder.toString());
        ScrollView scrollView = new ScrollView(dialog.getContext());
        scrollView.addView(textView);
        linearLayout.addView(scrollView);
        mCrashInfo = null;
    }

    private void hookUserInstalledApp() {
        findAndHookMethod(Thread.class, "setDefaultUncaughtExceptionHandler",
                new Class<?>[]{Thread.UncaughtExceptionHandler.class}, chain -> {
                    Logger.d("Ignore default exception handler " + chain.getArg(0).getClass().getName());
                    return null;
                });

        findAndHookMethod(Thread.class, "setUncaughtExceptionHandler",
                new Class<?>[]{Thread.UncaughtExceptionHandler.class}, chain -> {
                    Logger.d("Ignore exception handler " + chain.getArg(0).getClass().getName());
                    return null;
                });
    }
}
