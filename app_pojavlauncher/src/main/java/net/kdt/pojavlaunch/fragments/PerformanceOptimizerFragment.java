package net.kdt.pojavlaunch.fragments;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceVideoFragment;

import java.util.Locale;

/**
 * Device-aware preset helper. It only adjusts launcher preferences for RAM,
 * render scaling and Android sustained performance; it never edits game worlds.
 */
public class PerformanceOptimizerFragment extends Fragment {
    public static final String TAG = "NovaCraftPerformanceOptimizer";

    private static final String KEY_BACKUP_VALID = "novacraft_perf_backup_valid";
    private static final String KEY_BACKUP_RAM = "novacraft_perf_backup_ram";
    private static final String KEY_BACKUP_SCALE = "novacraft_perf_backup_scale";
    private static final String KEY_BACKUP_SUSTAINED = "novacraft_perf_backup_sustained";
    private static final int RAM_MIN_MB = 256;
    private static final int RAM_STEP_MB = 8;

    private TextView deviceName;
    private TextView deviceStats;
    private TextView displayStats;
    private TextView deviceClass;
    private TextView recommendation;
    private TextView currentSettings;
    private TextView memoryWarning;
    private Button restoreButton;

    public PerformanceOptimizerFragment() {
        super(R.layout.fragment_performance_optimizer);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        deviceName = view.findViewById(R.id.performance_device_name);
        deviceStats = view.findViewById(R.id.performance_device_stats);
        displayStats = view.findViewById(R.id.performance_display_stats);
        deviceClass = view.findViewById(R.id.performance_device_class);
        recommendation = view.findViewById(R.id.performance_recommendation);
        currentSettings = view.findViewById(R.id.performance_current_settings);
        memoryWarning = view.findViewById(R.id.performance_memory_warning);
        restoreButton = view.findViewById(R.id.performance_restore);

        view.findViewById(R.id.performance_preset_lite).setOnClickListener(v -> confirmPreset(Preset.PERFORMANCE));
        view.findViewById(R.id.performance_preset_balanced).setOnClickListener(v -> confirmPreset(Preset.BALANCED));
        view.findViewById(R.id.performance_preset_visual).setOnClickListener(v -> confirmPreset(Preset.VISUAL));
        restoreButton.setOnClickListener(v -> restorePreviousSettings());
        view.findViewById(R.id.performance_open_video).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), LauncherPreferenceVideoFragment.class,
                        "NOVACRAFT_PERFORMANCE_VIDEO_SETTINGS", null));
        view.findViewById(R.id.performance_open_java).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), LauncherPreferenceJavaFragment.class,
                        "NOVACRAFT_PERFORMANCE_JAVA_SETTINGS", null));
        refreshDashboard();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (deviceName != null) refreshDashboard();
    }

    private SharedPreferences preferences() {
        SharedPreferences preferences = LauncherPreferences.DEFAULT_PREF;
        if (preferences == null) {
            preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
            LauncherPreferences.DEFAULT_PREF = preferences;
        }
        return preferences;
    }

    private void refreshDashboard() {
        if (!isAdded() || getView() == null) return;
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager manager = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) manager.getMemoryInfo(memoryInfo);

        long totalBytes = memoryInfo.totalMem;
        long availableBytes = memoryInfo.availMem;
        int totalMb = toMb(totalBytes);
        int availableMb = toMb(availableBytes);
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());

        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "Android device" : Build.MODEL.trim();
        String name = manufacturer.isEmpty() || model.toLowerCase(Locale.ROOT).startsWith(manufacturer.toLowerCase(Locale.ROOT))
                ? model : manufacturer + " " + model;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int minSide = Math.min(width, height);
        String abi = Architecture.archAsString(Architecture.getDeviceArchitecture());

        deviceName.setText(name);
        deviceStats.setText(getString(R.string.novacraft_performance_device_stats, totalMb, availableMb, cores));
        displayStats.setText(getString(R.string.novacraft_performance_display, width, height, abi));

        boolean lowEnd = totalMb < 4096 || !Architecture.is64BitsDevice() || cores <= 4;
        boolean highCapacity = totalMb >= 6144 && Architecture.is64BitsDevice() && cores >= 8;
        deviceClass.setText(lowEnd
                ? R.string.novacraft_performance_class_low
                : highCapacity ? R.string.novacraft_performance_class_high : R.string.novacraft_performance_class_mid);
        recommendation.setText(lowEnd
                ? R.string.novacraft_performance_recommendation_low
                : highCapacity ? R.string.novacraft_performance_recommendation_high
                : R.string.novacraft_performance_recommendation_mid);

        SharedPreferences prefs = preferences();
        int ram = prefs.getInt("allocation", LauncherPreferences.PREF_RAM_ALLOCATION);
        int scale = prefs.getInt("resolutionRatio", Math.round(LauncherPreferences.PREF_SCALE_FACTOR * 100f));
        boolean sustained = prefs.getBoolean("sustainedPerformance", LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);
        currentSettings.setText(getString(R.string.novacraft_performance_current_values,
                ram, scale, getString(sustained ? R.string.novacraft_performance_sustained_yes
                        : R.string.novacraft_performance_sustained_no)));

        int recommendedRam = recommendedRam(totalMb, Architecture.is64BitsDevice());
        boolean lowAvailable = availableMb > 0 && availableMb < ram + 400;
        memoryWarning.setVisibility(lowAvailable ? View.VISIBLE : View.GONE);
        restoreButton.setEnabled(prefs.getBoolean(KEY_BACKUP_VALID, false));
        restoreButton.setAlpha(restoreButton.isEnabled() ? 1f : 0.45f);

        // Store the most recent measurements for the confirm dialog.
        lastTotalMemoryMb = totalMb;
        lastAvailableMemoryMb = availableMb;
        lastRecommendedRamMb = recommendedRam;
        lastMinDisplaySide = minSide;
    }

    private int lastTotalMemoryMb;
    private int lastAvailableMemoryMb;
    private int lastRecommendedRamMb;
    private int lastMinDisplaySide;

    private void confirmPreset(Preset preset) {
        final PresetValues values = buildPreset(preset);
        String presetName = getString(preset.title);
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.novacraft_performance_apply_title, presetName))
                .setMessage(getString(R.string.novacraft_performance_apply_message) + "\n\n"
                        + "Minecraft memory: " + values.ramMb + " MB\n"
                        + "Render scale: " + values.scalePercent + "%")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> applyPreset(values))
                .show();
    }

    private PresetValues buildPreset(Preset preset) {
        int ram;
        int scale;
        boolean sustained = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
        switch (preset) {
            case PERFORMANCE:
                ram = alignRam(Math.max(RAM_MIN_MB, (lastRecommendedRamMb * 3) / 4));
                scale = lastMinDisplaySide > 720 ? 60 : 75;
                break;
            case VISUAL:
                ram = lastRecommendedRamMb;
                scale = 100;
                sustained = LauncherPreferences.PREF_SUSTAINED_PERFORMANCE;
                break;
            case BALANCED:
            default:
                ram = lastRecommendedRamMb;
                scale = lastMinDisplaySide > 720 ? 75 : 100;
                break;
        }
        return new PresetValues(ram, scale, sustained);
    }

    private void applyPreset(PresetValues values) {
        if (!isAdded()) return;
        SharedPreferences prefs = preferences();
        SharedPreferences.Editor editor = prefs.edit();
        if (!prefs.getBoolean(KEY_BACKUP_VALID, false)) {
            editor.putInt(KEY_BACKUP_RAM,
                    prefs.getInt("allocation", LauncherPreferences.PREF_RAM_ALLOCATION));
            editor.putInt(KEY_BACKUP_SCALE,
                    prefs.getInt("resolutionRatio", Math.round(LauncherPreferences.PREF_SCALE_FACTOR * 100f)));
            editor.putBoolean(KEY_BACKUP_SUSTAINED,
                    prefs.getBoolean("sustainedPerformance", LauncherPreferences.PREF_SUSTAINED_PERFORMANCE));
            editor.putBoolean(KEY_BACKUP_VALID, true);
        }
        editor.putInt("allocation", values.ramMb);
        editor.putInt("resolutionRatio", values.scalePercent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            editor.putBoolean("sustainedPerformance", values.sustainedPerformance);
        }
        editor.apply();

        LauncherPreferences.PREF_RAM_ALLOCATION = values.ramMb;
        LauncherPreferences.PREF_SCALE_FACTOR = values.scalePercent / 100f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LauncherPreferences.PREF_SUSTAINED_PERFORMANCE = values.sustainedPerformance;
        }
        Toast.makeText(requireContext(), R.string.novacraft_performance_applied, Toast.LENGTH_LONG).show();
        refreshDashboard();
    }

    private void restorePreviousSettings() {
        SharedPreferences prefs = preferences();
        if (!prefs.getBoolean(KEY_BACKUP_VALID, false)) {
            Toast.makeText(requireContext(), R.string.novacraft_performance_restore_none, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.novacraft_performance_restore_title)
                .setMessage(R.string.novacraft_performance_apply_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int ram = prefs.getInt(KEY_BACKUP_RAM, LauncherPreferences.PREF_RAM_ALLOCATION);
                    int scale = prefs.getInt(KEY_BACKUP_SCALE, Math.round(LauncherPreferences.PREF_SCALE_FACTOR * 100f));
                    boolean sustained = prefs.getBoolean(KEY_BACKUP_SUSTAINED,
                            LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);
                    prefs.edit().putInt("allocation", ram)
                            .putInt("resolutionRatio", scale)
                            .putBoolean("sustainedPerformance", sustained)
                            .putBoolean(KEY_BACKUP_VALID, false)
                            .apply();
                    LauncherPreferences.PREF_RAM_ALLOCATION = ram;
                    LauncherPreferences.PREF_SCALE_FACTOR = scale / 100f;
                    LauncherPreferences.PREF_SUSTAINED_PERFORMANCE = sustained;
                    Toast.makeText(requireContext(), R.string.novacraft_performance_restored, Toast.LENGTH_LONG).show();
                    refreshDashboard();
                })
                .show();
    }

    private static int recommendedRam(int totalMb, boolean is64Bit) {
        int result;
        if (totalMb < 1024) result = 296;
        else if (totalMb < 1536) result = 448;
        else if (totalMb < 2048) result = 656;
        else if (!is64Bit) result = 696;
        else if (totalMb < 3064) result = 936;
        else if (totalMb < 4096) result = 1144;
        else if (totalMb < 6144) result = 1536;
        else result = 2048;
        return alignRam(result);
    }

    private static int alignRam(int ram) {
        int bounded = Math.max(RAM_MIN_MB, ram);
        return (bounded / RAM_STEP_MB) * RAM_STEP_MB;
    }

    private static int toMb(long bytes) {
        return bytes <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, bytes / (1024L * 1024L));
    }

    private enum Preset {
        PERFORMANCE(R.string.novacraft_performance_preset_lite, R.string.novacraft_performance_preset_lite),
        BALANCED(R.string.novacraft_performance_preset_balanced, R.string.novacraft_performance_preset_balanced),
        VISUAL(R.string.novacraft_performance_preset_visual, R.string.novacraft_performance_preset_visual);

        final int title;
        final int label;
        Preset(int title, int label) {
            this.title = title;
            this.label = label;
        }
    }

    private static class PresetValues {
        final int ramMb;
        final int scalePercent;
        final boolean sustainedPerformance;
        PresetValues(int ramMb, int scalePercent, boolean sustainedPerformance) {
            this.ramMb = ramMb;
            this.scalePercent = scalePercent;
            this.sustainedPerformance = sustainedPerformance;
        }
    }
}
