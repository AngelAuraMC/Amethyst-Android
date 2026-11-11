package net.kdt.pojavlaunch.prefs.screens;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.GLInfoUtils;

import java.util.Locale;

/**
 * Read-only device overview used by NovaCraft's settings centre.
 * Values are collected from Android system APIs and are refreshed whenever this screen opens.
 */
public class LauncherPreferenceDeviceFragment extends LauncherPreferenceFragment {
    private static final long GIB = 1024L * 1024L * 1024L;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_device_info);

        Context context = requireContext();
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "Unknown device" : Build.MODEL.trim();
        String deviceName = manufacturer.isEmpty() || model.toLowerCase(Locale.ROOT).startsWith(manufacturer.toLowerCase(Locale.ROOT))
                ? model : manufacturer + " " + model;
        requirePreference("device_info_model").setSummary(deviceName);

        requirePreference("device_info_android").setSummary(
                getString(R.string.novacraft_device_android_value, Build.VERSION.RELEASE, Build.VERSION.SDK_INT));

        String securityPatch = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? Build.VERSION.SECURITY_PATCH : getString(R.string.novacraft_device_unavailable);
        requirePreference("device_info_security").setSummary(securityPatch);

        String[] supportedAbis = Build.SUPPORTED_ABIS;
        String abiSummary = supportedAbis == null || supportedAbis.length == 0
                ? Architecture.archAsString(Architecture.getDeviceArchitecture())
                : android.text.TextUtils.join(", ", supportedAbis);
        requirePreference("device_info_architecture").setSummary(
                getString(R.string.novacraft_device_architecture_value,
                        Architecture.is64BitsDevice() ? "64-bit" : "32-bit", abiSummary));
        requirePreference("device_info_cpu").setSummary(
                getString(R.string.novacraft_device_cpu_value, Runtime.getRuntime().availableProcessors()));

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
        }
        long totalMemory = memoryInfo.totalMem;
        long availableMemory = memoryInfo.availMem;
        requirePreference("device_info_total_memory").setSummary(formatMemory(totalMemory));
        requirePreference("device_info_available_memory").setSummary(formatMemory(availableMemory));
        requirePreference("device_info_game_memory").setSummary(
                getString(R.string.novacraft_device_game_memory_value, LauncherPreferences.PREF_RAM_ALLOCATION));

        try {
            GLInfoUtils.GLInfo glInfo = GLInfoUtils.getGlInfo();
            requirePreference("device_info_gpu").setSummary(
                    getString(R.string.novacraft_device_gpu_value,
                            safeValue(glInfo.vendor), safeValue(glInfo.renderer), glInfo.glesMajorVersion));
        } catch (Throwable error) {
            requirePreference("device_info_gpu").setSummary(
                    getString(R.string.novacraft_device_unavailable));
        }

        DisplayMetrics display = context.getResources().getDisplayMetrics();
        requirePreference("device_info_display").setSummary(
                getString(R.string.novacraft_device_display_value,
                        display.widthPixels, display.heightPixels, display.density));

        StringBuilder compatibility = new StringBuilder();
        if (Architecture.is64BitsDevice()) {
            compatibility.append(getString(R.string.novacraft_device_note_64bit));
        } else {
            compatibility.append(getString(R.string.novacraft_device_note_32bit));
        }
        if (totalMemory > 0 && totalMemory < 4L * GIB) {
            compatibility.append("\n\n").append(getString(R.string.novacraft_device_note_low_memory));
        }
        compatibility.append("\n\n").append(getString(R.string.novacraft_device_note_general));
        requirePreference("device_info_compatibility").setSummary(compatibility.toString());
    }

    private String formatMemory(long bytes) {
        if (bytes <= 0) return getString(R.string.novacraft_device_unavailable);
        return String.format(Locale.getDefault(), "%.2f GB", bytes / (double) GIB);
    }

    private String safeValue(String value) {
        return value == null || value.trim().isEmpty()
                ? getString(R.string.novacraft_device_unavailable) : value.trim();
    }
}
