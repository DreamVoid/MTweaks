package li.lingfeng.ltweaks.fragments;

import android.content.Intent;
import android.os.Bundle;
import me.dreamvoid.mtweaks.R;

/**
 * Created by smallville on 2017/1/15.
 */

public class CommunicationPrefFragment extends BasePrefFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_communication);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
}
