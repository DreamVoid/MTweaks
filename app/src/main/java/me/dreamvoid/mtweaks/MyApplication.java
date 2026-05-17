package me.dreamvoid.mtweaks;

import android.app.Application;

import me.dreamvoid.mtweaks.prefs.Prefs;
import me.dreamvoid.mtweaks.prefs.PrefsSync;

public class MyApplication extends Application {

    private static Application instance;

    public static Application instance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Prefs.attachModuleProcess(this);
        PrefsSync.start(this);
    }
}
