package me.dreamvoid.mtweaks.fragments;

import android.content.Intent;
import android.widget.Toast;

import androidx.preference.Preference;
import li.lingfeng.ltweaks.fragments.BasePrefFragment;
import li.lingfeng.ltweaks.lib.PreferenceClick;
import me.dreamvoid.mtweaks.R;
import me.dreamvoid.mtweaks.activities.ShareFilterActivity;
import me.dreamvoid.mtweaks.utils.TrustAgentUtils;

/**
 * Created by smallville on 2017/1/4.
 */

public class SystemPrefFragment extends BasePrefFragment {

    @Override
    protected int getPreferencesResource() {
        return R.xml.pref_system;
    }

    @PreferenceClick(prefs = "key_system_share_filter")
    private void systemShareFilter(Preference preference) {
        startActivity(new Intent(requireContext(), ShareFilterActivity.class));
    }

    @PreferenceClick(prefs = "key_trust_agent_wlan")
    private void setSmartLockWifiList(Preference preference) {
        if (!TrustAgentUtils.openTrustAgentSettings(requireContext())) {
            Toast.makeText(requireContext(), R.string.trust_agent_settings_unavailable, Toast.LENGTH_SHORT).show();
        }
    }
}
