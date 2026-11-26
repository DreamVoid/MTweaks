package me.dreamvoid.mtweaks.xposed.google.iconpack;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.util.HashMap;
import java.util.Map;

import me.dreamvoid.mtweaks.utils.Logger;

/** 解析并加载选中图标包的图标。 */
public final class IconPackManager {

    private static String sPackageName;
    private static IconPackManager sInstance;

    private final PackageManager mPackageManager;
    private final String mPackPackageName;
    private final Map<ComponentName, String> mComponentMap = new HashMap<>();
    private final Map<String, Integer> mGrayscaleMap = new HashMap<>();
    private final Map<String, Integer> mDrawableIds = new HashMap<>();

    private Resources mResources;
    private boolean mLoaded;

    private IconPackManager(Context context, String packPackageName) {
        mPackageManager = context.getPackageManager();
        mPackPackageName = packPackageName;
    }

    public static Drawable getIcon(Context context, String packPackageName,
                                   ComponentName component, int density) {
        try {
            return manager(context, packPackageName).loadIcon(component, density);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Drawable getMonochromeIcon(Context context, String packPackageName,
                                             String appPackageName, int density) {
        try {
            return manager(context, packPackageName).loadMonochrome(appPackageName, density);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static synchronized void invalidate(String packPackageName) {
        if (sInstance != null && packPackageName.equals(sPackageName)) {
            sInstance = null;
            sPackageName = null;
        }
    }

    private static synchronized IconPackManager manager(Context context, String packPackageName) {
        if (sInstance == null || !packPackageName.equals(sPackageName)) {
            sPackageName = packPackageName;
            sInstance = new IconPackManager(context.getApplicationContext(), packPackageName);
        }
        return sInstance;
    }

    private Drawable loadIcon(ComponentName component, int density) {
        ensureLoaded();
        String drawableName = mComponentMap.get(component);
        return drawableName == null ? null : loadDrawable(drawableName, density);
    }

    private Drawable loadMonochrome(String appPackageName, int density) {
        ensureLoaded();
        Integer id = mGrayscaleMap.get(appPackageName);
        if (id == null || id == 0) {
            return null;
        }
        try {
            return mResources.getDrawableForDensity(id, density, null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private synchronized void ensureLoaded() {
        if (mLoaded) {
            return;
        }
        mLoaded = true;
        try {
            mResources = mPackageManager.getResourcesForApplication(mPackPackageName);
        } catch (PackageManager.NameNotFoundException e) {
            Logger.w("Icon pack not installed: " + mPackPackageName);
            return;
        }
        parseAppFilter();
        parseGrayscaleMap();
    }

    private void parseAppFilter() {
        XmlPullParser parser = openXml("appfilter");
        if (parser == null) {
            return;
        }
        try {
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG || !"item".equals(parser.getName())) {
                    continue;
                }
                ComponentName component = parseComponent(parser.getAttributeValue(null, "component"));
                String drawable = normalizeDrawable(parser.getAttributeValue(null, "drawable"));
                if (component != null && drawable != null) {
                    mComponentMap.put(component, drawable);
                }
            }
        } catch (Throwable e) {
            Logger.w("Can't parse appfilter of " + mPackPackageName + ": " + e);
        } finally {
            closeXml(parser);
        }
    }

    private void parseGrayscaleMap() {
        XmlPullParser parser = openXml("grayscale_icon_map");
        if (parser == null) {
            return;
        }
        try {
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG || !"icon".equals(parser.getName())) {
                    continue;
                }
                String packageName = parser.getAttributeValue(null, "package");
                int id = 0;
                if (parser instanceof XmlResourceParser) {
                    id = ((XmlResourceParser) parser).getAttributeResourceValue(null, "drawable", 0);
                }
                if (id == 0) {
                    id = resolveDrawableId(parser.getAttributeValue(null, "drawable"));
                }
                if (packageName != null && !packageName.isEmpty() && id != 0) {
                    mGrayscaleMap.put(packageName, id);
                }
            }
        } catch (Throwable e) {
            Logger.w("Can't parse grayscale map of " + mPackPackageName + ": " + e);
        } finally {
            closeXml(parser);
        }
    }

    private Drawable loadDrawable(String name, int density) {
        int id = resolveDrawableId(name);
        if (id == 0) {
            return null;
        }
        try {
            return mResources.getDrawableForDensity(id, density, null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private synchronized int resolveDrawableId(String name) {
        String key = normalizeDrawable(name);
        if (key == null || mResources == null) {
            return 0;
        }
        Integer cached = mDrawableIds.get(key);
        if (cached != null) {
            return cached;
        }
        int id = mResources.getIdentifier(key, "drawable", mPackPackageName);
        mDrawableIds.put(key, id);
        return id;
    }

    private XmlPullParser openXml(String name) {
        if (mResources == null) {
            return null;
        }
        int id = mResources.getIdentifier(name, "xml", mPackPackageName);
        if (id != 0) {
            try {
                return mResources.getXml(id);
            } catch (Throwable ignored) {
            }
        }
        try {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(mResources.getAssets().open(name + ".xml"), "UTF-8");
            return parser;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void closeXml(XmlPullParser parser) {
        if (parser instanceof XmlResourceParser) {
            ((XmlResourceParser) parser).close();
        }
    }

    private static ComponentName parseComponent(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("ComponentInfo{")) {
            value = value.substring("ComponentInfo{".length());
            if (value.endsWith("}")) {
                value = value.substring(0, value.length() - 1);
            }
        }
        int slash = value.indexOf('/');
        if (slash <= 0 || slash >= value.length() - 1) {
            return null;
        }
        String packageName = value.substring(0, slash).trim();
        String className = value.substring(slash + 1).trim();
        if (className.startsWith(".")) {
            className = packageName + className;
        }
        return new ComponentName(packageName, className);
    }

    private static String normalizeDrawable(String name) {
        if (name == null) {
            return null;
        }
        String value = name.trim();
        if (value.startsWith("@drawable/")) {
            value = value.substring("@drawable/".length());
        }
        return value.isEmpty() ? null : value;
    }
}
