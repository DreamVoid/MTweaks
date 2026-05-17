package me.dreamvoid.mtweaks;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import me.dreamvoid.mtweaks.fragments.MainFragment;
import me.dreamvoid.mtweaks.utils.UiUtils;

public class SettingsActivity extends AppCompatActivity {

    private static final String STATE_TITLE = "title";
    private static final String LAUNCHER_ALIAS = "me.dreamvoid.mtweaks.LauncherAlias";

    private CharSequence mRootTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiUtils.enableEdgeToEdge(this);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mRootTitle = getString(R.string.app_name);

        if (savedInstanceState == null) {
            setTitle(mRootTitle);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new MainFragment())
                    .commit();
        } else {
            CharSequence title = savedInstanceState.getCharSequence(STATE_TITLE);
            setTitle(title != null ? title : mRootTitle);
        }
        updateUpState();

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            updateUpState();
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                setTitle(mRootTitle);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharSequence(STATE_TITLE, getTitle());
    }

    /**
     * Replaces the content with a feature group and keeps the landing screen in the back stack.
     */
    public void openPreferenceFragment(@NonNull Fragment fragment, @NonNull CharSequence title) {
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
        setTitle(title);
        updateUpState();
    }

    private void updateUpState() {
        if (getSupportActionBar() == null) {
            return;
        }
        boolean canGoBack = getSupportFragmentManager().getBackStackEntryCount() > 0;
        getSupportActionBar().setDisplayHomeAsUpEnabled(canGoBack);
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            return true;
        }
        return super.onSupportNavigateUp();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        updateLauncherIconMenuTitle(menu.findItem(R.id.menu_toggle_launcher_icon));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_about) {
            showAbout();
            return true;
        } else if (id == R.id.menu_toggle_launcher_icon) {
            setLauncherIconHidden(!isLauncherIconHidden());
            updateLauncherIconMenuTitle(item);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // 组件默认状态（DEFAULT）表示可见，只有显式禁用才算隐藏。
    private boolean isLauncherIconHidden() {
        return getPackageManager().getComponentEnabledSetting(
                new ComponentName(this, LAUNCHER_ALIAS))
                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    private void setLauncherIconHidden(boolean hidden) {
        getPackageManager().setComponentEnabledSetting(
                new ComponentName(this, LAUNCHER_ALIAS),
                hidden ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    private void updateLauncherIconMenuTitle(MenuItem item) {
        if (item != null) {
            item.setTitle(isLauncherIconHidden()
                    ? R.string.app_show_launcher_icon
                    : R.string.app_hide_launcher_icon);
        }
    }

    private void showAbout() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.app_about_summary)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        ((TextView) dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }
}
