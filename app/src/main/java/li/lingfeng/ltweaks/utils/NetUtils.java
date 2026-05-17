package li.lingfeng.ltweaks.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.lang.reflect.Method;

import me.dreamvoid.mtweaks.MyApplication;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by lilingfeng on 2017/7/5.
 */

public class NetUtils {

    public static WifiInfo getWifiInfo() {
        try {
            Context context = MyApplication.instance().getApplicationContext();
            final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                Logger.e("wifi manager null");
                return null;
            }
            final WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null) {
                Logger.e("wifi info null");
                return null;
            }
            String ssid = wifiInfo.getSSID();
            String bssid = wifiInfo.getBSSID();
            Logger.i("wifi info raw: " + ssid + " " + bssid);

            // ADDED FOR NEW ANDROID: On Android 14+, BSSID is always randomized to
            // 02:00:00:00:00:00 for privacy. Accept it as valid.
            boolean hasValidBssid = !StringUtils.isAnyEmpty(bssid)
                    && !bssid.equals("<none>");
            boolean hasValidSsid = !StringUtils.isAnyEmpty(ssid)
                    && !ssid.equals("<unknown ssid>");

            // ADDED FOR NEW ANDROID: Try to get SSID from ConnectivityManager as fallback
            // (works on API 29+ without location permission)
            if (!hasValidSsid) {
                try {
                    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (cm != null) {
                        Network activeNetwork = cm.getActiveNetwork();
                        if (activeNetwork != null) {
                            NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                                    // Use reflection for getSSID() (added in API 29)
                                    Method getSsidMethod = caps.getClass().getMethod("getSSID");
                                    if (getSsidMethod != null) {
                                        String capsSsid = (String) getSsidMethod.invoke(caps);
                                        if (capsSsid != null && capsSsid.length() > 2) {
                                            ssid = capsSsid.substring(1, capsSsid.length() - 1);
                                            hasValidSsid = !StringUtils.isAnyEmpty(ssid)
                                                    && !ssid.equals("<unknown ssid>");
                                            Logger.i("wifi ssid from ConnectivityManager: " + ssid);
                                        }
                                    }
                            }
                        }
                    }
                } catch (Throwable e) {
                    Logger.e("Failed to get ssid from ConnectivityManager, " + e.getMessage());
                }
            }

            if (hasValidSsid && hasValidBssid) {
                Logger.i("wifi info valid: " + ssid + " " + bssid);
                // Return the real WifiInfo but with corrected SSID if we got it from ConnectivityManager
                // Note: bssid may be 02:00:00:00:00:00 (randomized) on Android 14+
                return wifiInfo;
            }
        } catch (Throwable e) {
            Logger.e("Failed to get wifi info, " + e.getMessage());
        }
        return null;
    }
}
