package li.lingfeng.ltweaks.fragments;

import android.os.Bundle;
import android.preference.ListPreference;
import li.lingfeng.ltweaks.lib.PreferenceChange;
import me.dreamvoid.mtweaks.R;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Created by smallville on 2016/12/24.
 */

public class GooglePrefFragment extends BasePrefFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_google);
    }

    @PreferenceChange(prefs = "key_youtube_set_quality", refreshAtStart = true)
    private void setYoutubeQuality(ListPreference preference, String intValue) {
        int index = ArrayUtils.indexOf(getResources().getStringArray(R.array.youtube_quality_int), intValue);
        preference.setSummary(getResources().getStringArray(R.array.youtube_quality_string)[index]);
    }
}
