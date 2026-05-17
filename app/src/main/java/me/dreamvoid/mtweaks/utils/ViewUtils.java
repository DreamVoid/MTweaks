package me.dreamvoid.mtweaks.utils;

import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * View helpers used by the Xposed hooks and the settings screens.
 */
public class ViewUtils {

    public static <T extends View> T findViewByType(ViewGroup rootView, final Class<? extends View> type) {
        return findViewByType(rootView, type, -1);
    }

    @SuppressWarnings("unchecked")
    public static <T extends View> T findViewByType(ViewGroup rootView, final Class<? extends View> type, int maxDeep) {
        List<View> views = traverseViews(rootView, true, (view, deep) -> type.isAssignableFrom(view.getClass()), maxDeep);
        if (!views.isEmpty()) {
            return (T) views.get(0);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T extends View> List<T> traverseViews(ViewGroup rootView, final boolean onlyOne, final ViewTraverseCallback callback, int maxDeep) {
        final List<T> results = new ArrayList<>();
        traverseViews(rootView, (view, deep) -> {
            if (callback.onAddResult(view, deep)) {
                results.add((T) view);
                return onlyOne;
            }
            return false;
        }, maxDeep);
        return results;
    }

    public static void traverseViews(ViewGroup rootView, ViewTraverseCallback2 callback, int maxDeep // start from 0
    ) {
        Queue<Pair<View, Integer>> views = new LinkedList<>();
        for (int i = 0; i < rootView.getChildCount(); ++i) {
            View child = rootView.getChildAt(i);
            views.add(Pair.create(child, 0));
        }

        while (!views.isEmpty()) {
            Pair<View, Integer> pair = views.poll();
            View view = pair.first;
            int deep = pair.second;
            if (callback.onView(view, deep)) {
                return;
            }

            if (view instanceof ViewGroup viewGroup && (maxDeep < 0 || deep < maxDeep)) {
                for (int i = 0; i < viewGroup.getChildCount(); ++i) {
                    View child = viewGroup.getChildAt(i);
                    views.add(Pair.create(child, deep + 1));
                }
            }
        }
    }

    public interface ViewTraverseCallback {
        boolean onAddResult(View view, int deep); // Return true to abort.
    }

    public interface ViewTraverseCallback2 {
        boolean onView(View view, int deep); // Return true to abort.
    }
}
