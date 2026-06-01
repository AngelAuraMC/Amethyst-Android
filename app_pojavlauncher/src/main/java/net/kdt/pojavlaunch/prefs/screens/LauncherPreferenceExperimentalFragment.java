package net.kdt.pojavlaunch.prefs.screens;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.fragments.RightPaneHomeFragment;
import net.kdt.pojavlaunch.theme.ThemeManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

public class LauncherPreferenceExperimentalFragment extends LauncherPreferenceFragment {

    private final ActivityResultLauncher<String> mImagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) copyImageToBgFile(uri);
            });

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_experimental);
        setupForceLandscape();
        setupCustomBackground();
        setupColourTheme();
    }

    // ── Force landscape ───────────────────────────────────────────────────────

    private void setupForceLandscape() {
        SwitchPreferenceCompat pref = requirePreference("force_landscape", SwitchPreferenceCompat.class);
        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean force = Boolean.TRUE.equals(newValue);
            requireActivity().setRequestedOrientation(
                    force ? SCREEN_ORIENTATION_SENSOR_LANDSCAPE : SCREEN_ORIENTATION_UNSPECIFIED);
            return true;
        });

        SwitchPreferenceCompat gradientPref = requirePreference("enable_bg_gradient", SwitchPreferenceCompat.class);
        gradientPref.setOnPreferenceChangeListener((preference, newValue) -> {
            // Save explicitly before recreate — the framework saves after listener returns
            net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(ThemeManager.KEY_GRADIENT, Boolean.TRUE.equals(newValue))
                .commit(); // commit() not apply() — must be synchronous before recreate
            requireActivity().recreate();
            return true;
        });
    }

    // ── Custom background ─────────────────────────────────────────────────────

    private void setupCustomBackground() {
        requirePreference("set_custom_launcher_bg").setOnPreferenceClickListener(p -> {
            mImagePickerLauncher.launch("image/*");
            return true;
        });

        requirePreference("remove_custom_launcher_bg").setOnPreferenceClickListener(p -> {
            File bgFile = new File(RightPaneHomeFragment.CUSTOM_BG_PATH);
            if (bgFile.exists()) bgFile.delete();
            notifyHomeFragmentBgChanged();
            toast(R.string.preference_custom_bg_removed);
            return true;
        });
    }

    private void copyImageToBgFile(@NonNull Uri uri) {
        File bgFile = new File(RightPaneHomeFragment.CUSTOM_BG_PATH);
        try (InputStream in  = requireContext().getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(bgFile)) {
            if (in == null) throw new Exception("Cannot open URI");
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            notifyHomeFragmentBgChanged();
            toast(R.string.preference_custom_bg_set_success);
        } catch (Exception e) {
            if (bgFile.exists()) bgFile.delete();
            toast(R.string.preference_custom_bg_error);
        }
    }

    // ── Colour theme ──────────────────────────────────────────────────────────

    private void setupColourTheme() {
        // Button 1: preset picker + reset
        requirePreference("colour_theme_presets").setOnPreferenceClickListener(p -> {
            showPresetDialog();
            return true;
        });
    }

    private void showPresetDialog() {
        ThemeManager.Preset[] presets = ThemeManager.PRESETS;
        String[] labels = new String[presets.length + 1];
        for (int i = 0; i < presets.length; i++) labels[i] = presets[i].name;
        labels[presets.length] = getString(R.string.preference_colour_reset);

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.preference_colour_presets_title)
            .setItems(labels, (dialog, which) -> {
                if (which < presets.length) {
                    ThemeManager.applyPreset(presets[which]);
                } else {
                    ThemeManager.resetToDefault();
                }
                requireActivity().recreate();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void toast(int resId) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show();
    }

    private void notifyHomeFragmentBgChanged() {
        MainMenuFragment mmf = (MainMenuFragment) requireActivity()
                .getSupportFragmentManager()
                .findFragmentByTag("ROOT");
        if (mmf == null) return;
        RightPaneHomeFragment home = (RightPaneHomeFragment) mmf
                .getChildFragmentManager()
                .findFragmentByTag(RightPaneHomeFragment.TAG);
        if (home != null) home.reloadBackground();
    }
}