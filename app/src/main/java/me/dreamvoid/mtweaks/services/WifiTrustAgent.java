package me.dreamvoid.mtweaks.services;

import android.content.*;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.service.trust.TrustAgentService;
import androidx.core.content.ContextCompat;
import me.dreamvoid.mtweaks.R;
import me.dreamvoid.mtweaks.prefs.Prefs;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.utils.NetUtils;
import me.dreamvoid.mtweaks.utils.Utils;
import me.dreamvoid.mtweaks.utils.WifiConnection;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Trust agent that keeps the device unlocked while connected to a trusted Wi-Fi network.
 */
public class WifiTrustAgent extends TrustAgentService implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final CharSequence TRUST_MESSAGE = "MTweaks wifi trust";

    private Receiver mReceiver;
    private ConnectivityManager.NetworkCallback mNetworkCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.d("WifiTrustAgent onCreate");
        Prefs.attachModuleProcess(this);
        setTrustAgentActive(true);

        setManagingTrust(true);
        SharedPreferences prefs = Prefs.get();
        if (prefs != null) {
            prefs.registerOnSharedPreferenceChangeListener(this);
        }

        mReceiver = new Receiver();
        IntentFilter filter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.setPriority(-1);
        ContextCompat.registerReceiver(this, mReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        registerNetworkCallback();
        updateTrust();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.d("WifiTrustAgent onDestroy");
        try {
            if (mReceiver != null) {
                unregisterReceiver(mReceiver);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (mNetworkCallback != null) {
                ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (manager != null) {
                    manager.unregisterNetworkCallback(mNetworkCallback);
                }
            }
        } catch (Throwable ignored) {
        }
        SharedPreferences prefs = Prefs.get();
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(this);
        }
        setTrustAgentActive(false);
        revokeTrust();
    }

    private void setTrustAgentActive(boolean active) {
        SharedPreferences prefs = Prefs.get();
        if (prefs == null) {
            return;
        }
        prefs.edit().putBoolean(getString(R.string.key_trust_agent_active), active).apply();
    }

    private void registerNetworkCallback() {
        try {
            ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return;
            }
            mNetworkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(Network network, android.net.NetworkCapabilities capabilities) {
                    updateTrust();
                }

                @Override
                public void onLost(Network network) {
                    updateTrust();
                }
            };
            manager.registerDefaultNetworkCallback(mNetworkCallback);
        } catch (Throwable e) {
            Logger.w("registerDefaultNetworkCallback failed: " + e);
        }
    }

    private void updateTrust() {
        try {
            WifiConnection connection = NetUtils.getCurrentWifi(this);
            if (connection == null || !connection.valid()) {
                Logger.i("revokeTrust, " + (connection == null ? "no wifi" : connection.reason()));
                revokeTrust();
                return;
            }

            Set<String> trusted = Prefs.getStringSet(R.string.key_trust_agent_wifi_aps, Set.of());
            for (String ap : trusted) {
                String[] parts = Utils.splitByLastChar(ap, ',');
                if (parts.length == 2 && connection.matches(parts[0], parts[1])) {
                    Logger.i("grantTrust by " + ap);
                    grantTrustCompat();
                    return;
                }
            }
            Logger.i("revokeTrust by ap loss (" + connection + ")");
            revokeTrust();
        } catch (Throwable e) {
            Logger.e("Wifi trust grant or revoke error", e);
            revokeTrust();
        }
    }

    private void grantTrustCompat() {
        int flags = TrustAgentService.FLAG_GRANT_TRUST_DISMISS_KEYGUARD;
        Method grantMethod = Arrays.stream(TrustAgentService.class.getDeclaredMethods())
                .filter(method -> "grantTrust".equals(method.getName()))
                .filter(method -> method.getParameterCount() == 4)
                .findFirst()
                .orElse(null);
        if (grantMethod != null) {
            try {
                grantMethod.setAccessible(true);
                Consumer<Object> callback = result -> Logger.d("grantTrust result: " + result);
                grantMethod.invoke(this, TRUST_MESSAGE, 0L, flags, callback);
                return;
            } catch (Throwable e) {
                Logger.w("4-arg grantTrust unavailable: " + e);
            }
        }
        grantTrust(TRUST_MESSAGE, 0L, flags);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (getString(R.string.key_trust_agent_wifi_aps).equals(key)) {
            updateTrust();
        }
    }

    private class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                return;
            }
            updateTrust();
        }
    }
}
