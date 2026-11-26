package me.dreamvoid.mtweaks.xposed.google.iconpack;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

/**
 * 生成与夜间模式、系统主题色相关的签名，用于判断图标缓存是否需要刷新。
 */
public final class ThemeSignature {

    private static final String[] COLOR_NAMES = {
            "system_accent1_100",
            "system_accent1_700",
            "system_accent2_800",
            "system_accent1_200"
    };

    private static volatile int[] sColorIds;

    private ThemeSignature() {
    }

    public static String of(Context context) {
        Resources resources = context.getResources();
        int uiMode = resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        StringBuilder builder = new StringBuilder("ui=").append(uiMode);
        for (int id : getColorIds(resources)) {
            builder.append(';');
            builder.append(id == 0 ? 0 : Integer.toHexString(context.getColor(id)));
        }
        return builder.toString();
    }

    private static int[] getColorIds(Resources resources) {
        int[] ids = sColorIds;
        if (ids == null) {
            ids = new int[COLOR_NAMES.length];
            for (int i = 0; i < COLOR_NAMES.length; i++) {
                ids[i] = resources.getIdentifier(COLOR_NAMES[i], "color", "android");
            }
            sColorIds = ids;
        }
        return ids;
    }
}
