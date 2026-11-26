package me.dreamvoid.mtweaks.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDividerItemDecoration;
import li.lingfeng.ltweaks.prefs.IntentActions;
import me.dreamvoid.mtweaks.R;
import me.dreamvoid.mtweaks.databinding.ActivityShareFilterBinding;
import me.dreamvoid.mtweaks.databinding.ItemShareFilterBinding;
import me.dreamvoid.mtweaks.prefs.Prefs;
import me.dreamvoid.mtweaks.prefs.ShareFilterStore;
import me.dreamvoid.mtweaks.utils.UiUtils;

import java.text.Collator;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 选择要从系统分享面板中隐藏的分享入口。
 */
public class ShareFilterActivity extends AppCompatActivity {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_HIDDEN = 1;
    private static final int FILTER_VISIBLE = 2;
    private static final int MENU_RESTORE_ALL = 1;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final List<TargetItem> mTargets = new ArrayList<>();
    private final Set<String> mHidden = new HashSet<>();
    private final Map<String, Drawable> mIconCache = new HashMap<>();

    private ActivityShareFilterBinding mBinding;
    private TargetAdapter mAdapter;
    private String mQuery = "";
    private int mFilter = FILTER_ALL;
    private boolean mLoaded;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UiUtils.enableEdgeToEdge(this, false);

        mBinding = ActivityShareFilterBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        applyInsets();

        setSupportActionBar(mBinding.toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }
        mBinding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        setTitle(R.string.pref_system_share_filter);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mBinding.list.setLayoutManager(layoutManager);
        MaterialDividerItemDecoration divider = new MaterialDividerItemDecoration(this, RecyclerView.VERTICAL);
        divider.setLastItemDecorated(false);
        mBinding.list.addItemDecoration(divider);
        mAdapter = new TargetAdapter();
        mBinding.list.setAdapter(mAdapter);

        mBinding.chipAll.setOnClickListener(v -> selectFilter(FILTER_ALL));
        mBinding.chipHidden.setOnClickListener(v -> selectFilter(FILTER_HIDDEN));
        mBinding.chipVisible.setOnClickListener(v -> selectFilter(FILTER_VISIBLE));

        mBinding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });

        mHidden.addAll(Prefs.getStringSet(R.string.key_system_share_filter_activities, Set.of()));
        loadTargets();
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            mBinding.appBar.setPadding(0, systemBars.top, 0, 0);
            mBinding.list.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            mBinding.emptyView.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(mBinding.getRoot());
    }

    private void loadTargets() {
        mBinding.progress.setVisibility(View.VISIBLE);
        mExecutor.execute(() -> {
            List<TargetItem> targets = queryTargets();
            mMainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                mTargets.clear();
                mTargets.addAll(targets);
                mLoaded = true;
                mBinding.progress.setVisibility(View.GONE);
                applyFilter();
            });
        });
    }

    private List<TargetItem> queryTargets() {
        PackageManager packageManager = getPackageManager();
        Map<String, TargetItem> map = new LinkedHashMap<>();
        for (String action : IntentActions.sSendActions) {
            Intent intent = new Intent(action).setType("*/*");
            packageManager.queryIntentActivities(intent, 0).forEach(info -> {
                ActivityInfo activityInfo = info.activityInfo;
                if (activityInfo == null) {
                    return;
                }
                String component = activityInfo.packageName + "/" + activityInfo.name;
                if (map.containsKey(component)) {
                    return;
                }
                CharSequence appLabel = activityInfo.applicationInfo != null ? activityInfo.applicationInfo.loadLabel(packageManager) : activityInfo.packageName;
                CharSequence activityLabel = info.loadLabel(packageManager);
                map.put(component, new TargetItem(component, activityInfo.packageName, activityInfo.name, info, appLabel, activityLabel));
            });
        }
        List<TargetItem> result = new ArrayList<>(map.values());
        Collator collator = Collator.getInstance(Locale.getDefault());
        result.sort((left, right) -> {
            int compare = collator.compare(left.appLabel().toString(), right.appLabel().toString());
            return compare != 0 ? compare : collator.compare(left.activityName(), right.activityName());
        });
        return result;
    }

    private void selectFilter(int filter) {
        mFilter = filter;
        mBinding.chipAll.setChecked(filter == FILTER_ALL);
        mBinding.chipHidden.setChecked(filter == FILTER_HIDDEN);
        mBinding.chipVisible.setChecked(filter == FILTER_VISIBLE);
        applyFilter();
    }

    private void applyFilter() {
        List<TargetItem> filtered = mTargets.stream().filter(item -> mFilter != FILTER_HIDDEN || mHidden.contains(item.component())).filter(item -> mFilter != FILTER_VISIBLE || !mHidden.contains(item.component())).filter(item -> mQuery.isEmpty() || item.matches(mQuery)).collect(Collectors.toList());
        mAdapter.submit(filtered);
        updateEmptyState(filtered.isEmpty());
    }

    private void updateEmptyState(boolean empty) {
        boolean show = mLoaded && empty;
        mBinding.emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
        mBinding.list.setVisibility(show ? View.GONE : View.VISIBLE);
        if (!show) {
            return;
        }
        int textRes = !mQuery.isEmpty() ? R.string.share_filter_no_results : switch (mFilter) {
            case FILTER_HIDDEN -> R.string.share_filter_empty_hidden;
            default -> R.string.share_filter_empty;
        };
        mBinding.emptyText.setText(textRes);
    }

    private void setHidden(TargetItem item, boolean hidden) {
        if (hidden) {
            mHidden.add(item.component());
        } else {
            mHidden.remove(item.component());
        }
        persistHidden();
        if (mFilter != FILTER_ALL) {
            mBinding.list.post(this::applyFilter);
        }
    }

    private void persistHidden() {
        if (Prefs.get() == null) {
            return;
        }
        Set<String> hidden = new HashSet<>(mHidden);
        Prefs.get().edit().putStringSet(getString(R.string.key_system_share_filter_activities), hidden).apply();
        ShareFilterStore.persist(hidden);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_RESTORE_ALL, Menu.NONE, R.string.share_filter_restore_all).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == MENU_RESTORE_ALL) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.share_filter_restore_all)
                    .setMessage(R.string.share_filter_restore_all_confirm)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        mHidden.clear();
                        persistHidden();
                        applyFilter();
                    }).setNegativeButton(android.R.string.cancel, null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    private Drawable iconFor(TargetItem item) {
        Drawable cached = mIconCache.get(item.packageName());
        if (cached != null) {
            return cached;
        }
        Drawable icon = item.resolveInfo().loadIcon(getPackageManager());
        mIconCache.put(item.packageName(), icon);
        return icon;
    }

    private record TargetItem(String component, String packageName, String activityName, ResolveInfo resolveInfo,
                              CharSequence appLabel, CharSequence activityLabel) {

        CharSequence description() {
            if (activityLabel != null && !TextUtils.equals(activityLabel, appLabel)) {
                return activityLabel;
            }
            int dot = activityName.lastIndexOf('.');
            return dot >= 0 ? activityName.substring(dot + 1) : activityName;
        }

        boolean matches(String query) {
            return contains(appLabel, query) || contains(activityLabel, query) || packageName.toLowerCase(Locale.ROOT).contains(query) || activityName.toLowerCase(Locale.ROOT).contains(query);
        }

        private static boolean contains(CharSequence value, String query) {
            return value != null && value.toString().toLowerCase(Locale.ROOT).contains(query);
        }
    }

    private final class TargetAdapter extends RecyclerView.Adapter<TargetAdapter.ViewHolder> {

        private final List<TargetItem> mItems = new ArrayList<>();

        @SuppressLint("NotifyDataSetChanged")
        void submit(List<TargetItem> items) {
            mItems.clear();
            mItems.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemShareFilterBinding binding = ItemShareFilterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TargetItem item = mItems.get(position);
            holder.binding.icon.setImageDrawable(iconFor(item));
            holder.binding.title.setText(item.appLabel());
            holder.binding.description.setText(item.description());

            holder.binding.checkbox.setOnCheckedChangeListener(null);
            holder.binding.checkbox.setChecked(mHidden.contains(item.component()));
            holder.binding.checkbox.setOnCheckedChangeListener((button, checked) -> setHidden(item, checked));

            holder.itemView.setOnClickListener(v -> holder.binding.checkbox.toggle());
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        static final class ViewHolder extends RecyclerView.ViewHolder {

            final ItemShareFilterBinding binding;

            ViewHolder(ItemShareFilterBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
