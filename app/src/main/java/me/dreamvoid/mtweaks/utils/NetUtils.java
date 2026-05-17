package me.dreamvoid.mtweaks.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Reads the current Wi-Fi connection in a way that survives Android's SSID/BSSID redaction:
 * first {@link WifiManager#getConnectionInfo()}, then {@code NetworkCapabilities.getTransportInfo()}.
 */
public final class NetUtils {

    private static final String UNKNOWN_SSID = "<unknown ssid>";
    private static final String NONE_BSSID = "<none>";

    private NetUtils() {
    }

    public static WifiConnection getCurrentWifi(Context context) {
        if (context == null) {
            return WifiConnection.invalid("no context");
        }
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;

        String ssid = null;
        String bssid = null;
        int networkId = -1;

        try {
            WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                return WifiConnection.invalid("WifiManager unavailable");
            }
            WifiInfo info = wifiManager.getConnectionInfo();
            if (info != null) {
                ssid = normalizeSsid(info.getSSID());
                bssid = normalizeBssid(info.getBSSID());
                networkId = info.getNetworkId();
            }
        } catch (Throwable e) {
            Logger.w("getConnectionInfo failed: " + e);
        }

        if (ssid == null || bssid == null) {
            WifiInfo transportInfo = getWifiInfoFromConnectivity(appContext);
            if (transportInfo != null) {
                if (ssid == null) {
                    ssid = normalizeSsid(transportInfo.getSSID());
                }
                if (bssid == null) {
                    bssid = normalizeBssid(transportInfo.getBSSID());
                }
                if (networkId < 0) {
                    networkId = transportInfo.getNetworkId();
                }
            }
        }

        if (ssid == null) {
            String capsSsid = getSsidFromNetworkCapabilities(appContext);
            if (capsSsid != null) {
                ssid = capsSsid;
            }
        }

        if (ssid == null) {
            return WifiConnection.invalid("SSID unavailable: grant location/nearby-wi-fi permission and enable location");
        }
        if (bssid == null) {
            return WifiConnection.invalid("BSSID unavailable: grant location/nearby-wi-fi permission and enable location");
        }

        WifiConnection connection = new WifiConnection(ssid, bssid, networkId, true, null);
        if (connection.isBssidRedacted()) {
            Logger.w("BSSID is redacted to " + WifiConnection.REDACTED_BSSID + "; trusted Wi-Fi matching may fail");
        }
        return connection;
    }

    /**
     * API 29+: the active Wi-Fi network exposes its {@link WifiInfo} through NetworkCapabilities.
     */
    private static WifiInfo getWifiInfoFromConnectivity(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null;
        }
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return null;
            }
            Network network = manager.getActiveNetwork();
            if (network == null) {
                return null;
            }
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return null;
            }
            Object transportInfo = Reflect.call(capabilities, "getTransportInfo");
            if (transportInfo instanceof WifiInfo info) {
                return info;
            }
        } catch (Throwable e) {
            Logger.w("getTransportInfo failed: " + e);
        }
        return null;
    }

    private static String getSsidFromNetworkCapabilities(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return null;
            }
            Network network = manager.getActiveNetwork();
            if (network == null) {
                return null;
            }
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return null;
            }
            Object raw = Reflect.call(capabilities, "getSSID");
            if (raw instanceof String rawSsid) {
                String value = normalizeSsid(rawSsid);
                Logger.i("SSID from NetworkCapabilities: " + value);
                return value;
            }
        } catch (Throwable e) {
            Logger.w("NetworkCapabilities#getSSID failed: " + e);
        }
        return null;
    }

    static String normalizeSsid(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        String value = raw;
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isEmpty() || UNKNOWN_SSID.equals(value) || value.startsWith("0x")) {
            return null;
        }
        return value;
    }

    static String normalizeBssid(String raw) {
        if (StringUtils.isEmpty(raw) || NONE_BSSID.equals(raw)) {
            return null;
        }
        return raw;
    }

    /**
     * Minimal reflective helper so the module does not depend on legacy XposedHelpers.
     */
    private static final class Reflect {
        private static Object call(Object target, String methodName, Object... args) throws Exception {
            Method method = Arrays.stream(target.getClass().getMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .filter(candidate -> candidate.getParameterCount() == args.length)
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException(
                            target.getClass().getName() + "#" + methodName));
            method.setAccessible(true);
            return method.invoke(target, args);
        }
    }
}
