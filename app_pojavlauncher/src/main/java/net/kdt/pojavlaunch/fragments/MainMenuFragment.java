package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;
import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;
import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private FrameLayout mRightPane;
    private View mBottomBarBg;   // stub — kept so mTaskCountListener check compiles
    private View mPlayButton;
    private View mEditProfileButton;
    private View mBottomBar;     // the single LinearLayout container for the whole bar
    // Intercepts Back when the right pane has something above home
    private OnBackPressedCallback mRightPaneBackCallback;

    // ─── Two-pane helpers ────────────────────────────────────────────────────

    /** True when the two-pane landscape layout is active. */
    private boolean isTwoPane() {
        return mRightPane != null;
    }

    /**
     * True when the right pane has a non-home fragment on the back stack.
     * Used by LauncherActivity to decide gear = home vs gear = settings.
     */
    public boolean isRightPaneActive() {
        return isTwoPane() && getChildFragmentManager().getBackStackEntryCount() > 0;
    }

    /**
     * Pops one entry off the right pane back stack.
     * Called from LauncherActivity.onBackPressed().
     */
    public void popRightPane() {
        if (!isTwoPane()) return;
        if (getChildFragmentManager().getBackStackEntryCount() > 0) {
            getChildFragmentManager().popBackStack();
        }
    }

    /**
     * Pops everything off the right pane back stack so the home fragment shows again.
     * Safe to call even if back stack is empty.
     */
    public void clearRightPane() {
        if (!isTwoPane()) return;
        int count = getChildFragmentManager().getBackStackEntryCount();
        if (count > 0) {
            getChildFragmentManager().popBackStack(
                    getChildFragmentManager().getBackStackEntryAt(0).getName(),
                    androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    /** Shows/hides the entire bottom bar. GONE collapses it so right pane fills full height. */
    private void setBottomBarVisible(boolean visible) {
        if (mBottomBar != null)
            mBottomBar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // Note: play button visibility during downloads is handled by the activity's
    // ProgressLayout — we do not need a separate TaskCountListener here.
    /**
     * Called by InstancePickerFragment after the user taps an instance.
     * Saves the selection, refreshes the spinner display, and pops back to home.
     */
    public void selectInstance(String profileKey) {
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileKey)
                .apply();
        ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, profileKey);
        clearRightPane();
        if (mVersionSpinner != null) mVersionSpinner.reloadProfiles();
    }

    /** Called externally (e.g. ProfileEditorFragment) to refresh the spinner display. */
    public void reloadSpinner() {
        if (mVersionSpinner != null) mVersionSpinner.reloadProfiles();
    }

    /**
     * Called by child fragments inside the right pane to navigate to another fragment
     * within the pane (landscape) or full-screen (portrait).
     * Use this instead of Tools.swapFragment(requireActivity(), ...) from child fragments.
     */
    public void openChildPane(Class<? extends Fragment> fragmentClass, String tag,
                              @Nullable Bundle args) {
        openPane(fragmentClass, tag, args);
    }

    /**
     * Returns true if the pane was used.
     */
    public boolean tryOpenInRightPane(Class<? extends Fragment> fragmentClass, String tag,
                                      @Nullable Bundle args) {
        if (!isTwoPane()) return false;
        openPane(fragmentClass, tag, args);
        return true;
    }

    /**
     * Internal navigation: right pane in landscape, full-screen swap in portrait.
     */
    private void openPane(Class<? extends Fragment> fragmentClass, String tag,
                          @Nullable Bundle args) {
        if (isTwoPane()) {
            getChildFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.right_pane_container, fragmentClass, args, tag)
                    .addToBackStack(tag)
                    .commit();
        } else {
            Tools.swapFragment(requireActivity(), fragmentClass, tag, args);
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Create the callback once. Lifecycle owner = this fragment, so it is
        // automatically removed when the fragment is DESTROYED (not just view-destroyed).
        mRightPaneBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                // Guard: only act if view is still alive and back stack has entries
                if (mRightPane == null) return;
                if (getChildFragmentManager().getBackStackEntryCount() > 0) {
                    getChildFragmentManager().popBackStackImmediate();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(this, mRightPaneBackCallback);

        // Only register the back-stack listener once per fragment instance.
        // Using a member reference so we can remove it in onDestroyView if needed.
        getChildFragmentManager().addOnBackStackChangedListener(mBackStackListener);
    }

    /** Keeps a stable reference so we never register it twice. */
    private final androidx.fragment.app.FragmentManager.OnBackStackChangedListener
            mBackStackListener = () -> {
        mRightPaneBackCallback.setEnabled(isRightPaneActive());
        if (!isTwoPane()) return;
        // Show bottom bar ONLY on home (back stack empty). Hide on all other panes
        // including instance picker (it has its own back button in the header).
        boolean showBar = getChildFragmentManager().getBackStackEntryCount() == 0;
        setBottomBarVisible(showBar);
    };

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mNewsButton          = view.findViewById(R.id.news_button);
        Button mDiscordButton       = view.findViewById(R.id.discord_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton    = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton     = view.findViewById(R.id.share_logs_button);
        Button mManageModsButton    = view.findViewById(R.id.open_files_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_directory_button);
        Button mModStoreButton      = view.findViewById(R.id.mod_store_button);

        ImageButton mEditProfileBtn = view.findViewById(R.id.edit_profile_button);
        Button mPlayBtn = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        // Detect two-pane landscape layout
        mRightPane = view.findViewById(R.id.right_pane_container);

        // Bottom bar refs
        mBottomBarBg       = view.findViewById(R.id._background_display_view);
        mPlayButton        = mPlayBtn;
        mEditProfileButton = mEditProfileBtn;
        mBottomBar         = view.findViewById(R.id.bottom_bar);

        // Load home fragment into right pane.
        // Check by fragment presence, not savedInstanceState, so rotation works correctly.
        if (isTwoPane()) {
            Fragment existing = getChildFragmentManager()
                    .findFragmentById(R.id.right_pane_container);
            if (existing == null) {
                getChildFragmentManager()
                        .beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.right_pane_container, RightPaneHomeFragment.class, null,
                                RightPaneHomeFragment.TAG)
                        // NOT added to back stack — home is the base, not a destination
                        .commit();
            }
        }

        // ── Sidebar buttons that are hidden in landscape (stubs kept for safety) ──
        // Wiki / Discord are moved to RightPaneHomeFragment in landscape;
        // they stay in the sidebar on portrait via fragment_launcher.xml (no-land).
        if (mNewsButton != null)
            mNewsButton.setOnClickListener(
                    v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        if (mDiscordButton != null)
            mDiscordButton.setOnClickListener(
                    v -> Tools.openURL(requireActivity(), getString(R.string.discord_invite)));

        // Custom controls (always opens as Activity — can't be in the pane)
        mCustomControlButton.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CustomControlsActivity.class)));

        // Mod Store
        if (mModStoreButton != null)
            mModStoreButton.setOnClickListener(v ->
                    openPane(ModsSearchFragment.class, ModsSearchFragment.TAG, null));

        // Execute .jar
        if (hasOnlineProfile()) {
            mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
            mInstallJarButton.setOnLongClickListener(v -> {
                runInstallerWithConfirmation(true);
                return true;
            });
        } else {
            mInstallJarButton.setOnClickListener(
                    v -> hasNoOnlineProfileDialog(requireActivity()));
        }

        // Share logs
        if (mShareLogsButton != null)
            mShareLogsButton.setOnClickListener(v -> shareLog(requireContext()));

        // Manage Mods
        mManageModsButton.setOnClickListener(v ->
                openPane(ManageModsFragment.class, ManageModsFragment.TAG, null));

        // Open game directory
        if (mOpenDirectoryButton != null) {
            mOpenDirectoryButton.setOnClickListener(v -> {
                if (Tools.isDemoProfile(v.getContext())) {
                    hasNoOnlineProfileDialog(getActivity(),
                            getString(R.string.demo_unsupported),
                            getString(R.string.change_account));
                } else if (!hasOnlineProfile()) {
                    hasNoOnlineProfileDialog(requireActivity());
                } else {
                    openPath(v.getContext(), getCurrentProfileDirectory(), false);
                }
            });
        }

        // Edit profile — open in right pane in landscape, full-screen in portrait
        mEditProfileBtn.setOnClickListener(v -> {
            if (isTwoPane()) {
                openPane(net.kdt.pojavlaunch.fragments.ProfileEditorFragment.class,
                        net.kdt.pojavlaunch.fragments.ProfileEditorFragment.TAG, null);
            } else {
                mVersionSpinner.openProfileEditor(requireActivity());
            }
        });

        // In landscape: tapping the spinner opens the instance picker in the right pane
        if (isTwoPane()) {
            mVersionSpinner.setOnClickListener(v ->
                    openPane(InstancePickerFragment.class, InstancePickerFragment.TAG, null));
        }

        // Force correct initial bar state BEFORE registering task listener,
        // so the listener's immediate callback doesn't fight an unset visibility.
        if (isTwoPane()) {
            setBottomBarVisible(getChildFragmentManager().getBackStackEntryCount() == 0);
        }

        // Play button visibility during downloads handled by activity's ProgressLayout

        // Play
        mPlayBtn.setOnClickListener(
                v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        // Long-press wiki → gamepad mapper (hidden feature)
        if (mNewsButton != null)
            mNewsButton.setOnLongClickListener(v -> {
                Tools.swapFragment(requireActivity(), GamepadMapperFragment.class,
                        GamepadMapperFragment.TAG, null);
                return true;
            });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mRightPane = null;
        mBottomBarBg = null;
        mPlayButton = null;
        mEditProfileButton = null;
        mBottomBar = null;
        getChildFragmentManager().removeOnBackStackChangedListener(mBackStackListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mVersionSpinner != null) {
            mVersionSpinner.post(() -> {
                if (mVersionSpinner != null) mVersionSpinner.reloadProfiles();
            });
        }
        if (isTwoPane() && mBottomBar != null) {
            // Post so this runs after any pending task-listener callbacks
            // that might incorrectly hide the bar
            final boolean showBar = getChildFragmentManager().getBackStackEntryCount() == 0;
            mBottomBar.post(() -> setBottomBarVisible(showBar));
        }
    }

    // ─── Private helpers ────────────────────────────────────────────────────

    private File getCurrentProfileDirectory() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF
                .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if (!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject =
                LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if (profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}