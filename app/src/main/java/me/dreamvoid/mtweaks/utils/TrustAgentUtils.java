package me.dreamvoid.mtweaks.utils;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import me.dreamvoid.mtweaks.R;
import me.dreamvoid.mtweaks.prefs.Prefs;
import me.dreamvoid.mtweaks.services.WlanTrustAgent;
import org.xmlpull.v1.XmlPullParser;

import java.util.List;

/**
 * Opens the system advanced security page with the matching trust agent entry highlighted.
 */
public final class TrustAgentUtils {

    private static final String ACTION_MORE_SECURITY_PRIVACY_SETTINGS = "com.android.settings.MORE_SECURITY_PRIVACY_SETTINGS";
    private static final String ACTION_SECURITY_ADVANCED_SETTINGS = "com.android.settings.security.SECURITY_ADVANCED_SETTINGS";
    private static final String EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args";
    private static final String EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key";
    private static final String KEY_MANAGE_TRUST_AGENTS = "manage_trust_agents";
    private static final String KEY_TRUST_AGENT_PREFIX = "trust_agent";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String PERMISSION_PROVIDE_TRUST_AGENT = "android.permission.PROVIDE_TRUST_AGENT";
    private static final String TRUST_AGENT_SERVICE_INTERFACE = "android.service.trust.TrustAgentService";
    private static final String TRUST_AGENT_META_DATA = "android.service.trust.trustagent";

    private TrustAgentUtils() {
    }

    /**
     * Returns whether the WiFi trust agent service is bound, i.e. enabled by the user.
     */
    public static boolean isTrustAgentActive() {
        return Prefs.getBoolean(R.string.key_trust_agent_active, false);
    }

    /**
     * Opens the advanced security page and highlights the trust agent entry to use next.
     *
     * @return whether a settings activity accepted the intent.
     */
    public static boolean openTrustAgentSettings(Context context) {
        String key = buildHighlightKey(context);
        Bundle args = new Bundle();
        args.putString(EXTRA_FRAGMENT_ARG_KEY, key);
        if (startActivity(context, new Intent(ACTION_MORE_SECURITY_PRIVACY_SETTINGS).putExtra(EXTRA_FRAGMENT_ARG_KEY, key).putExtra(EXTRA_SHOW_FRAGMENT_ARGS, args))) {
            return true;
        }
        if (startActivity(context, new Intent(ACTION_SECURITY_ADVANCED_SETTINGS).putExtra(EXTRA_FRAGMENT_ARG_KEY, key).putExtra(EXTRA_SHOW_FRAGMENT_ARGS, args))) {
            return true;
        }
        return startActivity(context, new Intent(Settings.ACTION_SECURITY_SETTINGS));
    }

    private static boolean startActivity(Context context, Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException ignored) {
            return false;
        }
    }

    private static String buildHighlightKey(Context context) {
        if (!isTrustAgentActive()) {
            return KEY_MANAGE_TRUST_AGENTS;
        }
        Integer index = findSettingsEntryIndex(context);
        return index != null ? KEY_TRUST_AGENT_PREFIX + index : KEY_MANAGE_TRUST_AGENTS;
    }

    /**
     * Returns this agent's index among the entries listed by the settings page, that is enabled
     * trust agents with a settings activity and a title.
     */
    private static Integer findSettingsEntryIndex(Context context) {
        PackageManager pm = context.getPackageManager();
        ComponentName own = new ComponentName(context, WlanTrustAgent.class);
        List<ResolveInfo> services = pm.queryIntentServices(new Intent(TRUST_AGENT_SERVICE_INTERFACE), PackageManager.GET_META_DATA);
        int index = 0;
        for (ResolveInfo info : services) {
            ServiceInfo serviceInfo = info.serviceInfo;
            if (serviceInfo == null
                    || pm.checkPermission(PERMISSION_PROVIDE_TRUST_AGENT, serviceInfo.packageName) != PackageManager.PERMISSION_GRANTED
                    || !hasSettingsActivity(pm, serviceInfo)) {
                continue;
            }
            if (own.equals(new ComponentName(serviceInfo.packageName, serviceInfo.name))) {
                return index;
            }
            index++;
        }
        return null;
    }

    private static boolean hasSettingsActivity(PackageManager pm, ServiceInfo serviceInfo) {
        if (serviceInfo.metaData == null) {
            return false;
        }
        try (XmlResourceParser parser = serviceInfo.loadXmlMetaData(pm, TRUST_AGENT_META_DATA)) {
            if (parser == null) {
                return false;
            }
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && type != XmlPullParser.START_TAG) {
                // Skip until the trust-agent root tag.
            }
            if (!"trust-agent".equals(parser.getName())) {
                return false;
            }
            return !TextUtils.isEmpty(parser.getAttributeValue(ANDROID_NS, "title")) && !TextUtils.isEmpty(parser.getAttributeValue(ANDROID_NS, "settingsActivity"));
        } catch (Exception e) {
            return false;
        }
    }
}
