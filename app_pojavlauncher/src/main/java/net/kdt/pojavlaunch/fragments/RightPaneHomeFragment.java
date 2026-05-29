package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

/**
 * Default content of the right pane in landscape two-pane mode.
 * Shows the cat wallpaper, Wiki and Discord buttons at the top.
 */
public class RightPaneHomeFragment extends Fragment {

    public static final String TAG = "RightPaneHomeFragment";

    public RightPaneHomeFragment() {
        super(R.layout.fragment_right_pane_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.news_button_pane).setOnClickListener(
                v -> Tools.openURL(requireActivity(), Tools.URL_HOME));

        view.findViewById(R.id.discord_button_pane).setOnClickListener(
                v -> Tools.openURL(requireActivity(), getString(R.string.discord_invite)));
    }
}