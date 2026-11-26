package me.dreamvoid.mtweaks.xposed.google;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import li.lingfeng.ltweaks.lib.XposedLoad;
import me.dreamvoid.mtweaks.prefs.PackageNames;
import me.dreamvoid.mtweaks.utils.Logger;
import me.dreamvoid.mtweaks.xposed.core.FeatureModule;
import me.dreamvoid.mtweaks.xposed.core.Hooks;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;


/**
 * Adds a "clear all" button with a close icon to the bottom action row of the recents screen,
 * styled like the original screenshot / select buttons.
 * <p>
 * The launcher keeps owning its own clear all button, the added one only forwards the clicks.
 */
@XposedLoad(packages = { PackageNames.PIXEL_LAUNCHER},
        prefs = "key_pixel_launcher_clear_all_bottom")
public class XposedPixelLauncherClearAll extends FeatureModule {

    private static final String CLEAR_ALL_BUTTON = "com.android.quickstep.views.ClearAllButton";
    private static final String RECENTS_VIEW = "com.android.quickstep.views.RecentsView";
    private static final String TAG_SETUP = "mtweaks_clear_all_setup";
    private static final String TAG_ADDED = "mtweaks_clear_all_added";
    private static final String[] ACTION_IDS = { "action_screenshot", "action_lens", "action_select" };

    private Handler mHandler;

    @Override
    protected void onLoad() {
        Class<?> clearAllClass = null;
        try {
            clearAllClass = findClass(CLEAR_ALL_BUTTON);
        } catch (Throwable e) {
            Logger.w("No Quickstep clear all button in " + context().packageName());
            return;
        }

        for (Constructor<?> constructor : clearAllClass.getDeclaredConstructors()) {
            try {
                final Class<?> type = clearAllClass;
                Hooks.hook(constructor, chain -> {
                    Object result = chain.proceed();
                    scheduleSetup(type, chain.getThisObject());
                    return result;
                });
            } catch (Throwable e) {
                Logger.w("Can't hook clear all constructor: " + e);
            }
        }

        // The recents view may already have created its clear all button before the module was
        // loaded, so also pick it up every time the recents view is laid out.
        try {
            final Class<?> type = clearAllClass;
            Class<?> recentsView = findClass(RECENTS_VIEW);
            Method onLayout = findOnLayout(recentsView);
            if (onLayout != null) {
                Hooks.hook(onLayout, chain -> {
                    Object result = chain.proceed();
                    Object self = chain.getThisObject();
                    if (self instanceof ViewGroup group) {
                        View clearAll = findClearAllChild(group, type);
                        if (clearAll != null && !TAG_SETUP.equals(clearAll.getTag())) {
                            mainHandler().post(() -> setup(clearAll, 0));
                        }
                    }
                    return result;
                });
            }
        } catch (Throwable e) {
            Logger.w("Can't hook recents layout: " + e);
        }
        Logger.i("Hooked clear all button of " + context().packageName());
    }

    private Method findOnLayout(Class<?> cls) {
        Class<?> current = cls;
        while (current != null && !ViewGroup.class.equals(current)) {
            for (Method method : current.getDeclaredMethods()) {
                if ("onLayout".equals(method.getName()) && method.getParameterTypes().length == 5) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private View findClearAllChild(ViewGroup group, Class<?> clearAllClass) {
        for (int i = 0; i < group.getChildCount(); ++i) {
            View child = group.getChildAt(i);
            if (clearAllClass.isInstance(child)) {
                return child;
            }
        }
        return null;
    }

    private void scheduleSetup(Class<?> clearAllClass, Object button) {
        if (!(button instanceof View || clearAllClass.isInstance(button))) {
            return;
        }
        final View clearAll = (View) button;
        mainHandler().postDelayed(() -> setup(clearAll, 0), 800);
    }

    private Handler mainHandler() {
        if (mHandler == null) {
            mHandler = new Handler(Looper.getMainLooper());
        }
        return mHandler;
    }

    private void setup(final View clearAll, final int attempt) {
        try {
            if (TAG_SETUP.equals(clearAll.getTag())) {
                return;
            }
            if (!(clearAll.getRootView() instanceof ViewGroup root)) {
                retry(clearAll, attempt);
                return;
            }
            ViewGroup target = findActionsContainer(root);
            if (target == null) {
                retry(clearAll, attempt);
                return;
            }
            clearAll.setTag(TAG_SETUP);
            if (!TAG_ADDED.equals(target.getTag())) {
                target.addView(createButton(clearAll, target), createLayoutParams(target));
                target.setTag(TAG_ADDED);
                Logger.i("Added clear all button to overview actions.");
            }
        } catch (Throwable e) {
            Logger.e("Can't add clear all button", e);
        }
    }

    private void retry(final View clearAll, final int attempt) {
        if (attempt < 10) {
            mainHandler().postDelayed(() -> setup(clearAll, attempt + 1), 500);
        }
    }

    /**
     * The replica lives in the action row while the original button keeps its own place and its
     * own fade in/out animation, so the launcher behaviour stays untouched.
     */
    @SuppressLint("UseCompatTextViewDrawableApis")
    private Button createButton(View clearAll, ViewGroup target) {
        Context context = clearAll.getContext();
        Button button = new Button(context);
        View sibling = findSiblingAction(target);
        if (clearAll instanceof TextView originalButton) {
            button.setText(originalButton.getText());
        }
        button.setAllCaps(false);
        button.setSingleLine(true);

        // Copy the look of the existing action buttons of the row so that the new button looks
        // like it was part of the original layout.
        float density = context.getResources().getDisplayMetrics().density;
        int iconSize = Math.round(18 * density);
        ColorStateList iconColors = null;
        if (sibling instanceof TextView reference) {
            button.setTextSize(TypedValue.COMPLEX_UNIT_PX, reference.getTextSize());
            button.setTextColor(reference.getTextColors());
            button.setTypeface(reference.getTypeface());
            button.setGravity(reference.getGravity());
            button.setIncludeFontPadding(reference.getIncludeFontPadding());
            button.setPadding(reference.getPaddingLeft(), reference.getPaddingTop(),
                    reference.getPaddingRight(), reference.getPaddingBottom());
            button.setMinimumWidth(reference.getMinimumWidth());
            button.setMinimumHeight(reference.getMinimumHeight());
            button.setCompoundDrawablePadding(reference.getCompoundDrawablePadding());
            Drawable referenceIcon = reference.getCompoundDrawablesRelative()[0];
            if (referenceIcon != null && referenceIcon.getBounds().width() > 0) {
                iconSize = referenceIcon.getBounds().width();
            }
            // Use the state based tint of the row icons so the cross greys out when the row
            // is disabled during swiping.
            iconColors = reference.getCompoundDrawableTintList();
            if (iconColors == null) {
                iconColors = reference.getTextColors();
            }
        }
        if (sibling != null) {
            Drawable background = sibling.getBackground();
            if (background != null && background.getConstantState() != null) {
                button.setBackground(background.getConstantState()
                        .newDrawable(context.getResources()).mutate());
            }
            // A freshly created Button brings its own elevation animation which draws a shadow.
            // The action buttons are flat, so take over the state of the row button.
            button.setStateListAnimator(sibling.getStateListAnimator());
            button.setElevation(sibling.getElevation());
            button.setTranslationZ(sibling.getTranslationZ());
        }

        if (iconColors == null) {
            iconColors = ColorStateList.valueOf(Color.WHITE);
        }
        Drawable icon = new CloseIconDrawable(iconColors, Math.max(2f, iconSize * 0.09f));
        icon.setBounds(0, 0, iconSize, iconSize);
        // The cross paints its own state based color, so it must not be tinted by the button.
        button.setCompoundDrawableTintList(null);
        button.setCompoundDrawablesRelative(icon, null, null, null);
        button.setEnabled(target.isEnabled());

        button.setOnClickListener(v -> {
            boolean clicked = clearAll.performClick();
            Logger.d("Clear all replica clicked, forwarded=" + clicked);
        });
        return button;
    }

    private View findSiblingAction(ViewGroup target) {
        for (int i = 0; i < target.getChildCount(); ++i) {
            View child = target.getChildAt(i);
            if (isActionButton(child)) {
                return child;
            }
        }
        return target.getChildCount() > 0 ? target.getChildAt(0) : null;
    }

    private View findLastAction(ViewGroup target) {
        for (int i = target.getChildCount() - 1; i >= 0; --i) {
            View child = target.getChildAt(i);
            if (isActionButton(child)) {
                return child;
            }
        }
        return target.getChildCount() > 0 ? target.getChildAt(target.getChildCount() - 1) : null;
    }

    /**
     * Finds the bottom action row by looking for the screenshot / lens / select action button
     * and returns its parent container.
     */
    private ViewGroup findActionsContainer(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); ++i) {
            View child = group.getChildAt(i);
            if (isActionButton(child) && child.getParent() instanceof ViewGroup parentGroup) {
                return parentGroup;
            }
            if (child instanceof ViewGroup childGroup) {
                ViewGroup found = findActionsContainer(childGroup);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean isActionButton(View view) {
        if (view.getId() == 0) {
            return false;
        }
        String name = idName(view);
        for (String actionId : ACTION_IDS) {
            if (actionId.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private String idName(View view) {
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable e) {
            return String.valueOf(view.getId());
        }
    }

    private ViewGroup.LayoutParams createLayoutParams(ViewGroup target) {
        // The spacing between the buttons is defined by the margin of the trailing siblings, so
        // the parameters of the last button have to be reused for the button appended at the end.
        View sibling = findLastAction(target);
        if (sibling != null) {
            ViewGroup.LayoutParams params = sibling.getLayoutParams();
            if (params instanceof LinearLayout.LayoutParams linearParams
                    && target instanceof LinearLayout) {
                return new LinearLayout.LayoutParams(linearParams);
            }
            if (params instanceof FrameLayout.LayoutParams frameParams) {
                return new FrameLayout.LayoutParams(frameParams);
            }
            if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
                return new ViewGroup.MarginLayoutParams(marginParams);
            }
            if (params != null) {
                return new ViewGroup.LayoutParams(params);
            }
        }
        if (target instanceof LinearLayout) {
            return new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Draws a plain cross using the color state list of the action row, so it greys out
     * together with the other icons while the recents view is swiping.
     */
    private static final class CloseIconDrawable extends Drawable {

        private static final float INSET_RATIO = 0.18f;

        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ColorStateList mColor;
        private int mAlpha = 255;

        CloseIconDrawable(ColorStateList color, float strokeWidth) {
            mColor = color;
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
            mPaint.setStrokeWidth(strokeWidth);
            updateColor(getState());
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float inset = Math.min(bounds.width(), bounds.height()) * INSET_RATIO;
            canvas.drawLine(bounds.left + inset, bounds.top + inset,
                    bounds.right - inset, bounds.bottom - inset, mPaint);
            canvas.drawLine(bounds.right - inset, bounds.top + inset,
                    bounds.left + inset, bounds.bottom - inset, mPaint);
        }

        @Override
        public boolean isStateful() {
            return mColor.isStateful();
        }

        @Override
        protected boolean onStateChange(int[] state) {
            return updateColor(state);
        }

        private boolean updateColor(int[] state) {
            int color = mColor.getColorForState(state, mColor.getDefaultColor());
            int alpha = Color.alpha(color) * mAlpha / 255;
            int newColor = (color & 0x00FFFFFF) | (alpha << 24);
            if (mPaint.getColor() != newColor) {
                mPaint.setColor(newColor);
                return true;
            }
            return false;
        }

        @Override
        public void setAlpha(int alpha) {
            mAlpha = alpha;
            updateColor(getState());
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            mPaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
