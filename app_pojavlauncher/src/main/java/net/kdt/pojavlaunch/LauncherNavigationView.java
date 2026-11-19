package net.kdt.pojavlaunch;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.fragments.ModpackCreateFragment;
import net.kdt.pojavlaunch.fragments.ProfileEditorFragment;
import net.kdt.pojavlaunch.fragments.ProfileTypeSelectFragment;
import net.kdt.pojavlaunch.fragments.SearchModFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;

/** Navigation rail for launcher sections. It routes into the existing fragment flows. */
public class LauncherNavigationView extends LinearLayout {
    private static final int HOME = 0;
    private static final int VERSIONS = 1;
    private static final int MODS = 2;
    private static final int SETTINGS = 3;

    private final NavigationItem[] mItems = new NavigationItem[4];
    private FragmentManager mFragmentManager;
    private FragmentManager.FragmentLifecycleCallbacks mLifecycleCallbacks;

    public LauncherNavigationView(@NonNull Context context) {
        this(context, null);
    }

    public LauncherNavigationView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setGravity(Gravity.CENTER);
        setBackgroundColor(ContextCompat.getColor(context, R.color.launcher_nav_background));
        buildItems(context);
    }

    private void buildItems(Context context) {
        addItem(context, HOME, R.drawable.ic_menu_home, R.string.launcher_nav_home);
        addItem(context, VERSIONS, R.drawable.ic_edit_profile, R.string.launcher_nav_versions);
        addItem(context, MODS, R.drawable.ic_menu_install_jar, R.string.launcher_nav_mods);
        addItem(context, SETTINGS, R.drawable.ic_menu_settings, R.string.launcher_nav_settings);
    }

    private void addItem(Context context, int destination, int iconRes, int labelRes) {
        boolean verticalRail = getOrientation() == VERTICAL;
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(3), dp(4), dp(3), dp(4));
        item.setBackgroundResource(R.drawable.launcher_nav_item_background);
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(context.getString(labelRes));
        item.setOnClickListener(v -> navigate(destination));

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LayoutParams iconParams = new LayoutParams(dp(23), dp(23));
        item.addView(icon, iconParams);

        TextView label = new TextView(context);
        label.setText(labelRes);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, verticalRail ? 9 : 10);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        LayoutParams labelParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(2);
        item.addView(label, labelParams);

        LayoutParams itemParams = verticalRail
                ? new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
                : new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        int margin = dp(3);
        itemParams.setMargins(margin, margin, margin, margin);
        addView(item, itemParams);

        NavigationItem navigationItem = new NavigationItem(destination, item, icon, label);
        mItems[destination] = navigationItem;
        applyItemState(navigationItem, false);
    }

    private void navigate(int destination) {
        if (!(getContext() instanceof FragmentActivity)) return;
        FragmentActivity activity = (FragmentActivity) getContext();
        Fragment current = activity.getSupportFragmentManager().findFragmentById(R.id.container_fragment);

        select(destination);
        switch (destination) {
            case HOME:
                Tools.backToMainMenu(activity);
                break;
            case VERSIONS:
                if (!(current instanceof ProfileTypeSelectFragment)) {
                    Tools.swapFragment(activity, ProfileTypeSelectFragment.class,
                            ProfileTypeSelectFragment.TAG, null);
                }
                break;
            case MODS:
                if (!(current instanceof ModpackCreateFragment)) {
                    Tools.swapFragment(activity, ModpackCreateFragment.class,
                            ModpackCreateFragment.TAG, null);
                }
                break;
            case SETTINGS:
                if (!(current instanceof LauncherPreferenceFragment)) {
                    Tools.swapFragment(activity, LauncherPreferenceFragment.class,
                            LauncherActivity.SETTING_FRAGMENT_TAG, null);
                }
                break;
        }
    }

    private void select(int destination) {
        for (NavigationItem item : mItems) {
            if (item != null) applyItemState(item, item.destination == destination);
        }
    }

    private void applyItemState(NavigationItem item, boolean selected) {
        item.container.setSelected(selected);
        item.icon.setColorFilter(ContextCompat.getColor(getContext(),
                selected ? R.color.launcher_accent : R.color.launcher_nav_text));
        item.label.setTextColor(ContextCompat.getColor(getContext(),
                selected ? R.color.primary_text : R.color.launcher_nav_text));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!(getContext() instanceof FragmentActivity)) return;
        mFragmentManager = ((FragmentActivity) getContext()).getSupportFragmentManager();
        mLifecycleCallbacks = new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment fragment) {
                int destination = destinationFor(fragment);
                if (destination >= 0) select(destination);
            }
        };
        mFragmentManager.registerFragmentLifecycleCallbacks(mLifecycleCallbacks, true);
        Fragment current = mFragmentManager.findFragmentById(R.id.container_fragment);
        int destination = destinationFor(current);
        if (destination >= 0) select(destination);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mFragmentManager != null && mLifecycleCallbacks != null) {
            mFragmentManager.unregisterFragmentLifecycleCallbacks(mLifecycleCallbacks);
        }
        mFragmentManager = null;
        mLifecycleCallbacks = null;
        super.onDetachedFromWindow();
    }

    private int destinationFor(@Nullable Fragment fragment) {
        if (fragment instanceof MainMenuFragment) return HOME;
        if (fragment instanceof ProfileTypeSelectFragment || fragment instanceof ProfileEditorFragment) return VERSIONS;
        if (fragment instanceof ModpackCreateFragment || fragment instanceof SearchModFragment) return MODS;
        if (fragment instanceof LauncherPreferenceFragment) return SETTINGS;
        return -1;
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private static final class NavigationItem {
        final int destination;
        final View container;
        final ImageView icon;
        final TextView label;

        NavigationItem(int destination, View container, ImageView icon, TextView label) {
            this.destination = destination;
            this.container = container;
            this.icon = icon;
            this.label = label;
        }
    }
}
