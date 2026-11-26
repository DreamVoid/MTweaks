package me.dreamvoid.mtweaks.fragments;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import me.dreamvoid.mtweaks.R;
import me.dreamvoid.mtweaks.activities.SettingsActivity;

/**
 * Landing screen: a plain list of feature groups, same visual language as system settings.
 */
public class MainFragment extends Fragment {

    public MainFragment() {
        super(R.layout.fragment_main);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.entry_google).setOnClickListener(v -> open(new GooglePrefFragment(), R.string.pref_header_google));
        view.findViewById(R.id.entry_communication).setOnClickListener(v -> open(new CommunicationPrefFragment(), R.string.pref_header_communication));
        view.findViewById(R.id.entry_system).setOnClickListener(v -> open(new SystemPrefFragment(), R.string.pref_header_system));
    }

    private void open(Fragment fragment, int titleRes) {
        if (getActivity() instanceof SettingsActivity activity) {
            activity.openPreferenceFragment(fragment, getString(titleRes));
        }
    }
}
