package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.dialogOnUiThread;
import static net.kdt.pojavlaunch.Tools.hasMods;
import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;
import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;
import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.runOnUiThread;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.modloaders.LWJGL3ifyUtils;
import net.kdt.pojavlaunch.modloaders.modpacks.ModCompatibility;
import net.kdt.pojavlaunch.modloaders.modpacks.ModHealthReport;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;
import net.kdt.pojavlaunch.utils.NovaCraftAppearance;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceWorldsFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceNewsFragment;

import java.io.File;
import java.util.List;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private ObjectAnimator mAmbientAnimator;

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mDiscordButton = view.findViewById(R.id.discord_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);
        Button mWorldsButton = view.findViewById(R.id.worlds_button);
        Button mAccountsButton = view.findViewById(R.id.accounts_button);
        Button mModsButton = view.findViewById(R.id.mods_button);
        Button mPacksButton = view.findViewById(R.id.packs_button);
        Button mRuntimeManagerButton = view.findViewById(R.id.runtime_manager_button);
        Button mPerformanceButton = view.findViewById(R.id.performance_optimizer_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        NovaCraftAppearance.applyHome(view, requireContext());
        NovaCraftAppearance.applyHeader(requireActivity());

        mNewsButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), LauncherPreferenceNewsFragment.class, "NOVACRAFT_MINECRAFT_NEWS", null));
        mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.discord_invite)));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        if (hasOnlineProfile()) {
            mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
            mInstallJarButton.setOnLongClickListener(v -> {
                runInstallerWithConfirmation(true);
                return true;
            });
        } else mInstallJarButton.setOnClickListener(v -> hasNoOnlineProfileDialog(requireActivity()));
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> runModPreflight(mPlayButton));

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));
        mWorldsButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), LauncherPreferenceWorldsFragment.class, "NOVACRAFT_WORLDS_MANAGER", null));
        mAccountsButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), AccountManagerFragment.class, AccountManagerFragment.TAG, null));
        mModsButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), ModManagerFragment.class, ModManagerFragment.TAG, null));
        mPacksButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), PackManagerFragment.class, PackManagerFragment.TAG, null));
        mRuntimeManagerButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), JavaRuntimeManagerFragment.class, JavaRuntimeManagerFragment.TAG, null));
        mPerformanceButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), PerformanceOptimizerFragment.class, PerformanceOptimizerFragment.TAG, null));

        mOpenDirectoryButton.setOnClickListener((v)-> {
            if (Tools.isDemoProfile(v.getContext())){ // Say a different message when on demo profile since they might see the hidden demo folder
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
            } else if (!hasOnlineProfile()) { // Otherwise display the generic pop-up to log in
                hasNoOnlineProfileDialog(requireActivity());
            } else openPath(v.getContext(), getCurrentProfileDirectory(), false);

        });


        mNewsButton.setOnLongClickListener((v)->{
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
    }

    private File getCurrentProfileDirectory() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if(!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if(profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mVersionSpinner != null) {
            mVersionSpinner.reloadProfiles();
        }
        if (getView() != null) {
            NovaCraftAppearance.applyHome(getView(), requireContext());
            NovaCraftAppearance.applyHeader(requireActivity());
            updateAmbientAnimation();
        }
    }

    /** Runs an offline metadata audit before starting Minecraft. Warnings never silently block the user. */
    private void runModPreflight(Button playButton) {
        if (playButton == null || !isAdded()) return;
        playButton.setEnabled(false);
        Toast.makeText(requireContext(), R.string.novacraft_launch_mod_checking, Toast.LENGTH_SHORT).show();

        MinecraftProfile profile;
        try {
            profile = LauncherProfiles.getCurrentProfile();
        } catch (Exception error) {
            playButton.setEnabled(true);
            continuePlayAfterModCheck();
            return;
        }
        File gameDirectory = Tools.getGameDirPath(profile);
        File modsDirectory = new File(gameDirectory, "mods");
        String minecraftVersion = ModCompatibility.detectMinecraftVersion(profile.lastVersionId);
        String loader = ModCompatibility.detectLoader(profile);

        new Thread(() -> {
            java.util.List<net.kdt.pojavlaunch.modloaders.modpacks.ModManagerAdapter.ModEntry> entries =
                    ModHealthReport.scanDirectory(modsDirectory, loader);
            ModHealthReport.Report report = ModHealthReport.analyze(entries, minecraftVersion, loader);
            if (!isAdded() || getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                playButton.setEnabled(true);
                if (!report.hasProblems()) {
                    continuePlayAfterModCheck();
                    return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.novacraft_launch_mod_warning_title)
                        .setMessage(report.toDisplayText() + "\n\n" +
                                getString(R.string.novacraft_launch_mod_warning_message))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setNeutralButton(R.string.novacraft_launch_mod_manage, (dialog, which) ->
                                Tools.swapFragment(requireActivity(), ModManagerFragment.class, ModManagerFragment.TAG, null))
                        .setPositiveButton(R.string.novacraft_launch_mod_continue, (dialog, which) ->
                                continuePlayAfterModCheck())
                        .show();
            });
        }, "NovaCraft-Launch-Preflight").start();
    }

    /** Preserve the existing Sodium warning after the new compatibility preflight. */
    private void continuePlayAfterModCheck() {
        if (Tools.hasMods("sodium") && !(LauncherPreferences.DEFAULT_PREF.getBoolean("sodium_override", false))) {
            AlertDialog sodiumWarningDialog = new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.sodium_warning_title)
                    .setMessage(R.string.sodium_warning_message)
                    .setNeutralButton(R.string.delete_sodium, (d,w)-> {
                        Tools.deleteSodiumMods();
                        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                    })
                    .setPositiveButton(R.string.novacraft_launch_mod_continue,
                            (d,w) -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true))
                    .create();
            sodiumWarningDialog.show();
        } else {
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        }
    }

    private void updateAmbientAnimation() {
        View root = getView();
        if (root == null) return;
        View ambientGlow = root.findViewById(R.id.novacraft_ambient_glow);
        boolean shouldAnimate = NovaCraftAppearance.shouldAnimate(requireContext());

        if (!shouldAnimate || ambientGlow == null) {
            if (mAmbientAnimator != null) {
                mAmbientAnimator.cancel();
                mAmbientAnimator = null;
            }
            return;
        }

        if (mAmbientAnimator == null) {
            mAmbientAnimator = ObjectAnimator.ofFloat(ambientGlow, View.ALPHA, 0.16f, 0.38f);
            mAmbientAnimator.setDuration(5600L);
            mAmbientAnimator.setRepeatCount(ValueAnimator.INFINITE);
            mAmbientAnimator.setRepeatMode(ValueAnimator.REVERSE);
            mAmbientAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        }

        if (!mAmbientAnimator.isStarted()) {
            mAmbientAnimator.start();
        }
    }

    @Override
    public void onPause() {
        if (mAmbientAnimator != null) {
            mAmbientAnimator.cancel();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (mAmbientAnimator != null) {
            mAmbientAnimator.cancel();
            mAmbientAnimator = null;
        }
        super.onDestroyView();
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
