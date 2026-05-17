package li.lingfeng.ltweaks.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.preference.*;
import androidx.recyclerview.widget.RecyclerView;
import li.lingfeng.ltweaks.lib.PreferenceClick;
import me.dreamvoid.mtweaks.utils.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Preference fragment based on AndroidX Preference, styled by the Material 3 app theme.
 */
public abstract class BasePrefFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceClickListener {

    private final Map<String, List<Method>> mPrefClickListeners = new HashMap<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure the storage before inflating, otherwise preferences are read from
        // credential protected storage while later writes go to device protected storage.
        PreferenceManager manager = getPreferenceManager();
        manager.setSharedPreferencesName(requireContext().getPackageName() + "_preferences");
        manager.setSharedPreferencesMode(Context.MODE_PRIVATE);
        manager.setStorageDeviceProtected();
        addPreferencesFromResource(getPreferencesResource());
    }

    @XmlRes
    protected abstract int getPreferencesResource();

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        listenPreferenceClicks();

        RecyclerView listView = getListView();
        if (listView != null) {
            listView.setLongClickable(false);
            listView.setHapticFeedbackEnabled(false);
            listView.setClipToPadding(false);
            listView.setPadding(0, 0, 0, 24);
        }
    }

    private void listenPreferenceClicks() {
        Method[] methods = getClass().getDeclaredMethods();
        for (Method method : methods) {
            PreferenceClick preferenceClick = method.getAnnotation(PreferenceClick.class);
            if (preferenceClick == null) {
                continue;
            }

            for (String pref : preferenceClick.prefs()) {
                List<Method> clickMethods = mPrefClickListeners.get(pref);
                if (clickMethods == null) {
                    clickMethods = new ArrayList<>();
                    clickMethods.add(method);
                    mPrefClickListeners.put(pref, clickMethods);
                }
                listenPreferenceClick(pref);
            }
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        List<Method> clickMethods = mPrefClickListeners.get(preference.getKey());
        if (clickMethods != null) {
            for (Method method : clickMethods) {
                try {
                    method.setAccessible(true);
                    method.invoke(this, preference);
                } catch (Exception e) {
                    Logger.e("Can't invoke preference click method " + method + ", " + e.getMessage());
                    Logger.stackTrace(e);
                }
            }
        }
        return true;
    }

    protected void listenPreferenceClick(String key) {
        Preference preference = findPreference(key);
        if (preference == null) {
            Logger.e("Preference not found for key: " + key);
            return;
        }
        preference.setOnPreferenceClickListener(this);
    }
}
