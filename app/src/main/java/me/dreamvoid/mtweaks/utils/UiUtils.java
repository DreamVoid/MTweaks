package me.dreamvoid.mtweaks.utils;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Small Material 3 UI helpers shared by the module's activities.
 */
public final class UiUtils {

    private UiUtils() {
    }

    /**
     * Draws the activity edge-to-edge and keeps its content clear of the system bars.
     */
    public static void enableEdgeToEdge(@NonNull ComponentActivity activity) {
        enableEdgeToEdge(activity, true);
    }

    /**
     * Draws the activity edge-to-edge.
     *
     * @param applyContentInsets whether to pad the content view with the system bar insets; pass
     *                           {@code false} when the layout consumes the insets itself.
     */
    public static void enableEdgeToEdge(@NonNull ComponentActivity activity,
                                        boolean applyContentInsets) {
        EdgeToEdge.enable(
                activity,
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT));

        if (applyContentInsets) {
            View decor = activity.getWindow().getDecorView();
            // Apply the insets once the activity has installed its real content view.
            decor.post(() -> applySystemBarPadding(activity));
        }
    }

    private static void applySystemBarPadding(Activity activity) {
        View target = findInsetTarget(activity);
        if (target == null) {
            return;
        }
        final int paddingLeft = target.getPaddingLeft();
        final int paddingTop = target.getPaddingTop();
        final int paddingRight = target.getPaddingRight();
        final int paddingBottom = target.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(target, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    paddingLeft + systemBars.left,
                    paddingTop + systemBars.top,
                    paddingRight + systemBars.right,
                    paddingBottom + systemBars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(target);
    }

    /**
     * Returns the sub-decor (the direct child of {@link Window#getDecorView()} that contains both
     * the action bar and {@code android.R.id.content}) so that padding it keeps every bar visible.
     */
    private static View findInsetTarget(Activity activity) {
        Window window = activity.getWindow();
        View decor = window.getDecorView();
        View content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return decor;
        }
        if (decor instanceof ViewGroup) {
            View target = content;
            ViewParent parent = content.getParent();
            while (parent instanceof View parentView && parentView != decor) {
                target = parentView;
                parent = parentView.getParent();
            }
            if (parent == decor) {
                return target;
            }
        }
        return content;
    }
}
