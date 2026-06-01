package net.kdt.pojavlaunch.fragments;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import java.io.File;

/**
 * Default content of the right pane in landscape two-pane mode.
 * Shows a custom background (if set), otherwise a plain transparent pane.
 * Wiki and Discord buttons are pinned at the top.
 */
public class RightPaneHomeFragment extends Fragment {

    public static final String TAG = "RightPaneHomeFragment";
    /** File path where the custom launcher background image is stored. */
    public static final String CUSTOM_BG_PATH = Tools.DIR_DATA + "/custom_launcher_bg";

    public RightPaneHomeFragment() {
        super(R.layout.fragment_right_pane_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.news_button_pane).setOnClickListener(
                v -> Tools.openURL(requireActivity(), Tools.URL_HOME));

        view.findViewById(R.id.discord_button_pane).setOnClickListener(
                v -> Tools.openURL(requireActivity(), getString(R.string.discord_invite)));

        loadBackground(view);
    }

    /**
     * Called after saving or removing a custom background so the pane
     * refreshes without needing a full fragment recreate.
     */
    public void reloadBackground() {
        View v = getView();
        if (v != null) loadBackground(v);
    }

    private void loadBackground(@NonNull View view) {
        ImageView wallpaper = view.findViewById(R.id.right_pane_wallpaper);
        File bgFile = new File(CUSTOM_BG_PATH);
        if (bgFile.exists()) {
            Drawable d = Drawable.createFromPath(bgFile.getAbsolutePath());
            if (d != null) {
                wallpaper.setImageDrawable(d);
                wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
                wallpaper.setBackground(null);
                wallpaper.setVisibility(View.VISIBLE);
                return;
            }
        }
        // No custom bg — show the gradient drawable as the pane background if gradient is on,
        // otherwise stay transparent (root fragment_launcher bg shows through).
        wallpaper.setImageDrawable(null);
        TypedValue tv = new TypedValue();
        view.getContext().getTheme().resolveAttribute(R.attr.bgMainDrawable, tv, true);
        if (tv.resourceId != 0) {
            wallpaper.setBackgroundResource(tv.resourceId);
            wallpaper.setVisibility(View.VISIBLE);
        } else {
            wallpaper.setBackground(null);
            wallpaper.setVisibility(View.GONE);
        }
    }
}