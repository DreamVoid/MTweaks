package me.dreamvoid.mtweaks.xposed.google;

import android.icu.text.AlphabeticIndex;
import android.icu.util.ULocale;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;
import me.dreamvoid.mtweaks.xposed.core.Reflect;

import java.lang.reflect.Field;
import java.text.Collator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sorts the app drawer by pinyin, mixing CJK and Latin app names.
 */
@XposedLoad(packages = {PackageNames.PIXEL_LAUNCHER}, prefs = "key_pixel_launcher_better_app_sort")
public class XposedPixelLauncherAppSort extends FeatureModule {

    private static final String APP_INFO_COMPARATOR = "com.android.launcher3.allapps.AppInfoComparator";
    private static final String APP_INFO = "com.android.launcher3.model.data.AppInfo";
    private static final String ITEM_INFO = "com.android.launcher3.model.data.ItemInfo";
    private static final String ALPHABETIC_INDEX_COMPAT = "com.android.launcher3.compat.AlphabeticIndexCompat";
    private static final String LABEL_COMPARATOR = "com.android.launcher3.util.LabelComparator";
    private static final String MISC_SECTION = "\u2219";

    private static final ThreadLocal<Collator> COLLATORS = ThreadLocal.withInitial(() -> Collator.getInstance(Locale.SIMPLIFIED_CHINESE));
    private static final Map<String, String> SECTIONS = new ConcurrentHashMap<>();

    private static volatile AlphabeticIndex.ImmutableIndex sIndex;

    private Field mAppTitleField;
    private Field mTitleField;

    @Override
    protected void onLoad() {
        resolveTitleFields();
        if (!hookAppInfoComparator()) {
            hookLabelComparator();
        }
        hookSectionName();
    }

    private void resolveTitleFields() {
        try {
            Class<?> itemInfo = findClass(ITEM_INFO);
            mTitleField = Reflect.findField(itemInfo, "title");
            try {
                mAppTitleField = Reflect.findField(itemInfo, "appTitle");
            } catch (Throwable ignored) {
                // Older launchers don't expose appTitle.
            }
        } catch (Throwable e) {
            Logger.w("Can't resolve app title fields: " + e);
        }
    }

    private boolean hookAppInfoComparator() {
        try {
            Class<?> appInfo = findClass(APP_INFO);
            Class<?> comparator = findClass(APP_INFO_COMPARATOR);
            findAndHookMethod(comparator, "compare", new Class<?>[]{appInfo, appInfo}, chain -> {
                try {
                    int result = compareApps(chain.getArg(0), chain.getArg(1));
                    return result != 0 ? result : chain.proceed();
                } catch (Throwable e) {
                    Logger.w("App sort compare failed: " + e);
                    return chain.proceed();
                }
            });
            Logger.i("Hooked Pixel Launcher app sorting");
            return true;
        } catch (Throwable e) {
            Logger.w("Can't hook AppInfoComparator: " + e);
            return false;
        }
    }

    private void hookLabelComparator() {
        try {
            Class<?> comparator = findClass(LABEL_COMPARATOR);
            findAndHookMethod(comparator, "compare", new Class<?>[]{String.class, String.class}, chain -> {
                try {
                    String a = String.valueOf(chain.getArg(0));
                    String b = String.valueOf(chain.getArg(1));
                    int result = compareTitlesBySection(a, b);
                    return result != 0 ? result : chain.proceed();
                } catch (Throwable e) {
                    Logger.w("Label sort compare failed: " + e);
                    return chain.proceed();
                }
            });
            Logger.i("Hooked Pixel Launcher label sorting");
        } catch (Throwable e) {
            Logger.w("Can't hook LabelComparator: " + e);
        }
    }

    private void hookSectionName() {
        try {
            Class<?> compat = findClass(ALPHABETIC_INDEX_COMPAT);
            findAndHookMethod(compat, "computeSectionName", new Class<?>[]{CharSequence.class}, chain -> {
                try {
                    Object title = chain.getArg(0);
                    return sectionOf(title == null ? "" : title.toString());
                } catch (Throwable e) {
                    Logger.w("Can't compute pinyin section: " + e);
                    return chain.proceed();
                }
            });
            Logger.i("Hooked Pixel Launcher section names");
        } catch (Throwable e) {
            Logger.w("Can't hook AlphabeticIndexCompat: " + e);
        }
    }

    private int compareApps(Object a, Object b) {
        return compareTitlesBySection(titleOf(a), titleOf(b));
    }

    private static int compareTitlesBySection(String titleA, String titleB) {
        int result = sectionOf(titleA).compareTo(sectionOf(titleB));
        if (result != 0) {
            return result;
        }
        boolean aStartsWithLetter = !titleA.isEmpty() && Character.isLetterOrDigit(titleA.codePointAt(0));
        boolean bStartsWithLetter = !titleB.isEmpty() && Character.isLetterOrDigit(titleB.codePointAt(0));
        if (aStartsWithLetter != bStartsWithLetter) {
            return aStartsWithLetter ? -1 : 1;
        }
        return COLLATORS.get().compare(titleA, titleB);
    }

    private String titleOf(Object appInfo) {
        try {
            if (mAppTitleField != null) {
                Object appTitle = mAppTitleField.get(appInfo);
                if (appTitle instanceof CharSequence && ((CharSequence) appTitle).length() > 0) {
                    return appTitle.toString();
                }
            }
            if (mTitleField != null) {
                Object title = mTitleField.get(appInfo);
                if (title instanceof CharSequence) {
                    return title.toString();
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /**
     * Returns the pinyin/Latin section of a title, e.g. "A", "Z", "#" or "∙".
     */
    private static String sectionOf(String title) {
        String key = title == null ? "" : title;
        String cached = SECTIONS.get(key);
        if (cached != null) {
            return cached;
        }
        String section = computeSection(key);
        SECTIONS.putIfAbsent(key, section);
        return section;
    }

    private static String computeSection(String title) {
        try {
            AlphabeticIndex.ImmutableIndex index = index();
            String trimmed = trim(title);
            String label = index.getBucket(index.getBucketIndex(trimmed)).getLabel();
            if (!trim(label).isEmpty() || trimmed.isEmpty()) {
                return label;
            }
            int codePoint = trimmed.codePointAt(0);
            return Character.isDigit(codePoint) ? "#" : MISC_SECTION;
        } catch (Throwable e) {
            return MISC_SECTION;
        }
    }

    private static AlphabeticIndex.ImmutableIndex index() {
        AlphabeticIndex.ImmutableIndex index = sIndex;
        if (index == null) {
            synchronized (XposedPixelLauncherAppSort.class) {
                if (sIndex == null) {
                    sIndex = new AlphabeticIndex(ULocale.forLanguageTag("zh-Hans-CN")).addLabels(ULocale.ENGLISH).buildImmutableIndex();
                }
                index = sIndex;
            }
        }
        return index;
    }

    // Same trimming as com.android.launcher3.Utilities#trim.
    private static String trim(String value) {
        return value == null ? "" : value.replaceAll("(^\\h+|\\h+$)", "").trim();
    }
}
