package me.dreamvoid.mtweaks.xposed.google.iconpack;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import me.dreamvoid.mtweaks.R;
import me.dreamvoid.mtweaks.prefs.PackageNames;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 查找已安装且可解析的图标包。
 */
public final class IconPackScanner {

    private static final String[] KNOWN_PACKAGES = {PackageNames.LAWNICONS, PackageNames.LAWNICONS_PLAY, PackageNames.LAWNICONS_NIGHTLY};

    private IconPackScanner() {
    }

    public static List<IconPackInfo> scan(Context context) {
        PackageManager pm = context.getPackageManager();
        LinkedHashSet<String> packages = new LinkedHashSet<>();
        for (String known : KNOWN_PACKAGES) {
            if (isInstalled(pm, known)) {
                packages.add(known);
            }
        }
        for (Intent intent : intents()) {
            try {
                for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
                    if (info.activityInfo != null && info.activityInfo.packageName != null) {
                        packages.add(info.activityInfo.packageName);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        List<IconPackInfo> result = new ArrayList<>();
        for (String packageName : packages) {
            if (!isKnown(packageName) && !hasAppFilter(pm, packageName)) {
                continue;
            }
            CharSequence label = readLabel(pm, packageName);
            if (label == null) {
                continue;
            }
            result.add(new IconPackInfo(packageName, decorateLabel(context, packageName, label)));
        }
        result.sort((a, b) -> {
            int rank = Integer.compare(rank(a.packageName()), rank(b.packageName()));
            return rank != 0 ? rank : a.label().toString().compareToIgnoreCase(b.label().toString());
        });
        return result;
    }

    private static List<Intent> intents() {
        List<Intent> intents = new ArrayList<>();
        intents.add(new Intent("app.lawnchair.icons.THEMED_ICON").addCategory(Intent.CATEGORY_DEFAULT));
        intents.add(new Intent("com.novalauncher.THEME"));
        intents.add(new Intent("org.adw.launcher.icons.ACTION_PICK_ICON").addCategory(Intent.CATEGORY_DEFAULT));
        intents.add(new Intent("com.dlto.atom.launcher.THEME"));
        intents.add(new Intent(Intent.ACTION_MAIN).addCategory("com.anddoes.launcher.THEME"));
        return intents;
    }

    private static boolean isInstalled(PackageManager pm, String packageName) {
        try {
            pm.getApplicationInfo(packageName, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isKnown(String packageName) {
        return PackageNames.LAWNICONS.equals(packageName) || PackageNames.LAWNICONS_PLAY.equals(packageName) || PackageNames.LAWNICONS_NIGHTLY.equals(packageName);
    }

    private static int rank(String packageName) {
        if (PackageNames.LAWNICONS.equals(packageName)) {
            return 0;
        }
        if (PackageNames.LAWNICONS_PLAY.equals(packageName)) {
            return 1;
        }
        if (PackageNames.LAWNICONS_NIGHTLY.equals(packageName)) {
            return 2;
        }
        return 10;
    }

    private static CharSequence readLabel(PackageManager pm, String packageName) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static CharSequence decorateLabel(Context context, String packageName, CharSequence label) {
        String text = label.toString();
        String lower = text.toLowerCase(Locale.ROOT);
        if (PackageNames.LAWNICONS_PLAY.equals(packageName) && !lower.contains("play")) {
            return text + ModuleStrings.get(context, R.string.pref_pixel_launcher_themed_icon_pack_play, "（Play 版）");
        }
        if (PackageNames.LAWNICONS_NIGHTLY.equals(packageName) && !lower.contains("nightly")) {
            return text + ModuleStrings.get(context, R.string.pref_pixel_launcher_themed_icon_pack_nightly, "（每夜版）");
        }
        return text;
    }

    private static boolean hasAppFilter(PackageManager pm, String packageName) {
        try {
            Resources resources = pm.getResourcesForApplication(packageName);
            if (resources.getIdentifier("appfilter", "xml", packageName) != 0) {
                return true;
            }
            try (InputStream stream = resources.getAssets().open("appfilter.xml")) {
                return true;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }
}
