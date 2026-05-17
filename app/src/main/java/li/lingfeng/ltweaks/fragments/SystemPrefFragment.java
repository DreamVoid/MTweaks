package li.lingfeng.ltweaks.fragments;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.widget.Toast;
import me.dreamvoid.mtweaks.R;
import li.lingfeng.ltweaks.activities.*;
import li.lingfeng.ltweaks.fragments.base.Extra;
import li.lingfeng.ltweaks.fragments.sub.system.PreventListDataProvider;
import li.lingfeng.ltweaks.fragments.sub.system.ShareFilterDataProvider;
import li.lingfeng.ltweaks.fragments.sub.system.TextActionDataProvider;
import li.lingfeng.ltweaks.lib.PreferenceChange;
import li.lingfeng.ltweaks.lib.PreferenceClick;
import li.lingfeng.ltweaks.prefs.ActivityRequestCode;
import li.lingfeng.ltweaks.utils.*;
import me.dreamvoid.mtweaks.activities.WLANTrustAgentActivity;

/**
 * Created by smallville on 2017/1/4.
 */

public class SystemPrefFragment extends BasePrefFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_system);

        //uncheckPreferenceByDisabledComponent(R.string.key_text_selectable_text, SelectableTextActivity.class);
        //uncheckPreferenceByDisabledComponent(R.string.key_system_share_qrcode_scan, QrCodeActivity.class);
        //uncheckPreferenceByDisabledComponent(R.string.key_system_share_image_search, ImageSearchActivity.class);
    }


    @PreferenceChange(prefs = "key_text_selectable_text")
    private void enableSelectableText(Preference preference, boolean enabled) {
        ComponentUtils.enableComponent(SelectableTextActivity.class, enabled);
    }

    @PreferenceClick(prefs = "key_text_actions")
    private void manageTextActions(Preference preference) {
        ListCheckActivity.create(getActivity(), TextActionDataProvider.class);
    }

    @PreferenceChange(prefs = "key_system_share_qrcode_scan")
    private void systemShareQrcodeScan(final SwitchPreference preference, boolean enabled) {
        if (enabled) {
            PermissionUtils.requestPermissions(getActivity(), new PermissionUtils.ResultCallback() {
                @Override
                public void onResult(boolean ok) {
                    if (ok) {
                        ComponentUtils.enableComponent(QrCodeActivity.class, true);
                    } else {
                        preference.setChecked(false);
                    }
                }
            }, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } else {
            ComponentUtils.enableComponent(QrCodeActivity.class, false);
        }
    }

    @PreferenceChange(prefs = "key_system_share_image_search")
    private void systemShareImageSearch(final SwitchPreference preference, boolean enabled) {
        if (enabled) {
            PermissionUtils.requestPermissions(getActivity(), new PermissionUtils.ResultCallback() {
                @Override
                public void onResult(boolean ok) {
                    if (ok) {
                        ComponentUtils.enableComponent(ImageSearchActivity.class, true);
                    } else {
                        preference.setChecked(false);
                    }
                }
            }, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } else {
            ComponentUtils.enableComponent(ImageSearchActivity.class, false);
        }
    }

    @PreferenceClick(prefs = "key_system_share_filter")
    private void systemShareFilter(Preference preference) {
        ListCheckActivity.create(getActivity(), ShareFilterDataProvider.class);
    }

    @PreferenceClick(prefs = "key_prevent_running_set_list")
    private void setPreventList(Preference preference) {
        ListCheckActivity.create(getActivity(), PreventListDataProvider.class);
    }

    private void setTypesForListPreference(String[] types, ListPreference listPreference) {
        listPreference.setEntries(types);
        String[] entryValues = new String[types.length];
        for (int i = 0; i < types.length; ++i) {
            entryValues[i] = String.valueOf(i);
        }
        listPreference.setEntryValues(entryValues);
        listPreference.setSummary("%s");
    }

    @PreferenceChange(prefs = "key_quick_settings_tile_set_preconfigured_brightness", refreshAtStart = true)
    private void tileSetPreconfiguredBrightness(SwitchPreference preference, boolean enabled, Extra extra) {
        findPreference(R.string.key_quick_settings_tile_preconfigured_brightness).setEnabled(enabled);
    }

    @PreferenceChange(prefs = "key_quick_settings_tile_preconfigured_brightness", refreshAtStart = true)
    private boolean tilePrecongiruedBrightness(EditTextPreference preference, String intValue, Extra extra) {
        if (intValue.isEmpty()) {
            preference.setSummary("");
            return true;
        }
        int value = Integer.parseInt(intValue);
        if (value > 0 && value < 255) {
            preference.setSummary(intValue);
            return true;
        } else {
            return false;
        }
    }

    @PreferenceClick(prefs = "key_trust_agent_wlan")
    private void setSmartLockWifiList(Preference preference) {
        KeyguardManager keyguardManager = (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager.isKeyguardSecure()) {
            Intent keyguardIntent = keyguardManager.createConfirmDeviceCredentialIntent(getString(R.string.pref_trust_agent_wifi), null);
            startActivityForResult(keyguardIntent, ActivityRequestCode.KEYGUARD);
        } else {
            Toast.makeText(getActivity(), R.string.secure_lock_screen_not_setup, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ActivityRequestCode.KEYGUARD) {
            if (resultCode == Activity.RESULT_OK) {
                Intent intent = new Intent(getActivity(), WLANTrustAgentActivity.class);
                getActivity().startActivity(intent);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @PreferenceChange(prefs = "key_lineage_os_live_display_time", refreshAtStart = true)
    private void customizeLineageOSLiveDisplayTime(SwitchPreference preference, boolean enabled) {
        enablePreference(R.string.key_lineage_os_live_display_time_sunrise, enabled);
        enablePreference(R.string.key_lineage_os_live_display_time_sunset, enabled);
    }

    @PreferenceChange(prefs = "key_display_min_brightness", refreshAtStart = true)
    private boolean setMinBrightness(EditTextPreference preference, String intValue, Extra extra) {
        if (extra.refreshAtStart) {
            PowerManager powerManager = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
            try {
                int defaultMinBrightness = (int) PowerManager.class.getDeclaredMethod("getMinimumScreenBrightnessSetting").invoke(powerManager);
                preference.setDialogTitle(getString(R.string.pref_display_min_brightness_dialog_title, defaultMinBrightness));
            } catch (Throwable e) {
                Logger.e("Can't get default min brightness, " + e);
            }
        }
        if (intValue.isEmpty()) {
            preference.setSummary("");
            return true;
        }
        int value = Integer.parseInt(intValue);
        if (value > 0 && value < 255) {
            preference.setSummary(intValue);
            return true;
        } else {
            return false;
        }
    }
}
