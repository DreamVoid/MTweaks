package me.dreamvoid.mtweaks.xposed.core;

import android.os.Process;

/**
 * Created by lilingfeng on 2017/6/30.
 */

public abstract class XposedCommon extends FeatureModule {
    protected boolean isUserInstalledApp() {
        int uid = Process.myUid();
        return uid >= Process.FIRST_APPLICATION_UID && uid <= Process.LAST_APPLICATION_UID;
    }
}
