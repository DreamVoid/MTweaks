package me.dreamvoid.mtweaks.utils;

/**
 * Immutable snapshot of the currently connected Wi-Fi network.
 */
public record WifiConnection(
        String ssid,
        String bssid,
        int networkId,
        boolean valid,
        String reason
) {

    public static final String REDACTED_BSSID = "02:00:00:00:00:00";

    public static WifiConnection invalid(String reason) {
        return new WifiConnection(null, null, -1, false, reason);
    }

    public boolean isBssidRedacted() {
        return REDACTED_BSSID.equalsIgnoreCase(bssid);
    }

    public boolean matches(String trustedSsid, String trustedBssid) {
        return valid && ssid != null && ssid.equals(trustedSsid) && bssid != null && bssid.equalsIgnoreCase(trustedBssid);
    }

    @Override
    public String toString() {
        if (!valid) {
            return "WifiConnection{invalid, reason=" + reason + "}";
        }
        return "WifiConnection{" + ssid + " / " + bssid + " / networkId=" + networkId + "}";
    }
}
