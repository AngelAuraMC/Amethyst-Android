package net.kdt.pojavlaunch.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.NewJREUtil;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.multirt.MultiRTConfigDialog;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * NovaCraft dashboard for the launcher's existing MultiRT runtime engine.
 * Downloads, extraction and default-runtime writes remain delegated to the
 * established Pojav runtime manager so we do not maintain a second installer.
 */
public class JavaRuntimeManagerFragment extends Fragment {
    public static final String TAG = "NovaCraftJavaRuntimeManager";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<Object> installRuntimeLauncher;
    private MultiRTConfigDialog runtimeDialog;
    private TextView profileText;
    private TextView versionText;
    private TextView architectureText;
    private TextView activeRuntimeText;
    private TextView requirementText;
    private TextView activeHealthText;
    private TextView autoSelectionText;
    private TextView storageText;
    private TextView summaryText;
    private TextView downloadsText;
    private TextView emptyText;
    private LinearLayout runtimeCards;
    private long refreshToken;

    public JavaRuntimeManagerFragment() {
        super(R.layout.fragment_java_runtime_manager);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installRuntimeLauncher = registerForActivityResult(
                new OpenDocumentWithExtension("xz"),
                this::onRuntimeArchiveSelected
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        profileText = view.findViewById(R.id.runtime_manager_profile);
        versionText = view.findViewById(R.id.runtime_manager_version);
        architectureText = view.findViewById(R.id.runtime_manager_architecture);
        activeRuntimeText = view.findViewById(R.id.runtime_manager_active);
        requirementText = view.findViewById(R.id.runtime_manager_requirement);
        activeHealthText = view.findViewById(R.id.runtime_manager_health);
        autoSelectionText = view.findViewById(R.id.runtime_manager_auto);
        storageText = view.findViewById(R.id.runtime_manager_storage);
        summaryText = view.findViewById(R.id.runtime_manager_summary);
        downloadsText = view.findViewById(R.id.runtime_manager_downloads);
        emptyText = view.findViewById(R.id.runtime_manager_empty);
        runtimeCards = view.findViewById(R.id.runtime_manager_cards);

        Button manageButton = view.findViewById(R.id.runtime_manager_manage);
        Button refreshButton = view.findViewById(R.id.runtime_manager_refresh);
        manageButton.setOnClickListener(v -> openRuntimeDialog());
        refreshButton.setOnClickListener(v -> refreshDashboard());
        refreshDashboard();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (runtimeCards != null) refreshDashboard();
    }

    private void onRuntimeArchiveSelected(@Nullable Uri uri) {
        if (uri == null || getContext() == null) return;
        Tools.installRuntimeFromUri(requireContext(), uri);
        Toast.makeText(requireContext(), R.string.novacraft_runtime_import_started, Toast.LENGTH_LONG).show();
        mainHandler.postDelayed(this::refreshDashboard, 2500L);
    }

    private void openRuntimeDialog() {
        if (!isAdded()) return;
        if (runtimeDialog == null) {
            runtimeDialog = new MultiRTConfigDialog();
            runtimeDialog.prepare(requireContext(), installRuntimeLauncher);
            runtimeDialog.setOnDismissListener(this::refreshDashboard);
        }
        runtimeDialog.show();
    }

    private MinecraftProfile getSelectedProfile() {
        try {
            Tools.initStorageConstants(requireContext());
            LauncherProfiles.load();
            return LauncherProfiles.getCurrentProfile();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void refreshDashboard() {
        if (!isAdded() || getView() == null || runtimeCards == null) return;
        final long token = ++refreshToken;

        try {
            Tools.initEarlyConstants(requireContext());
        } catch (Exception error) {
            emptyText.setText(getString(R.string.novacraft_runtime_scan_failed, error.getMessage()));
            emptyText.setVisibility(View.VISIBLE);
            return;
        }

        final MinecraftProfile profile = getSelectedProfile();
        final String profileName = profile == null || profile.name == null || profile.name.trim().isEmpty()
                ? getString(R.string.novacraft_profile_default) : profile.name;
        final String minecraftVersion = profile == null || profile.lastVersionId == null
                ? getString(R.string.novacraft_profile_unknown) : profile.lastVersionId;
        final String defaultRuntime = LauncherPreferences.PREF_DEFAULT_RUNTIME;
        String selectedRuntime = defaultRuntime;
        if (profile != null) {
            try {
                selectedRuntime = Tools.getSelectedRuntime(profile);
            } catch (Exception ignored) {}
        }
        final String activeRuntimeName = selectedRuntime;
        final Integer requiredJava = detectRequiredJava(profile);
        final boolean automaticSelection = !LauncherPreferences.DEFAULT_PREF.getBoolean("disable_autojre_select", false);
        final int deviceArchitecture = Architecture.getDeviceArchitecture();

        new Thread(() -> {
            ArrayList<Runtime> installed = new ArrayList<>();
            ArrayList<NewJREUtil.ExternalRuntime> downloadable = new ArrayList<>();
            long storageBytes = 0L;
            String scanError = null;
            try {
                installed.addAll(MultiRTUtils.getInstalledRuntimes());
                downloadable.addAll(MultiRTUtils.getRuntimesToDownload());
                storageBytes = directorySize(new File(Tools.MULTIRT_HOME));
            } catch (Exception error) {
                scanError = error.getMessage() == null ? error.toString() : error.getMessage();
            }

            Runtime activeRuntime = activeRuntimeName == null ? null : MultiRTUtils.read(activeRuntimeName);
            int compatibleCount = 0;
            for (Runtime runtime : installed) {
                if (isCompatible(runtime, deviceArchitecture)) compatibleCount++;
            }

            final ArrayList<RuntimeCard> cards = new ArrayList<>();
            for (Runtime runtime : installed) {
                cards.add(new RuntimeCard(
                        runtime,
                        isCompatible(runtime, deviceArchitecture),
                        runtime.versionString == null || runtime.javaVersion <= 0,
                        runtime.name != null && runtime.name.equals(defaultRuntime),
                        runtime.name != null && runtime.name.equals(activeRuntimeName)
                ));
            }

            final int installedCount = installed.size();
            final int readyCount = compatibleCount;
            final long measuredStorage = storageBytes;
            final String errorMessage = scanError;
            final Runtime selectedRuntimeInfo = activeRuntime;
            final ArrayList<NewJREUtil.ExternalRuntime> pendingDownloads = downloadable;

            mainHandler.post(() -> {
                if (!isAdded() || getView() == null || token != refreshToken) return;
                profileText.setText(getString(R.string.novacraft_runtime_profile, profileName));
                versionText.setText(getString(R.string.novacraft_runtime_profile_version, minecraftVersion));
                architectureText.setText(getString(R.string.novacraft_runtime_device,
                        Architecture.archAsString(deviceArchitecture)));
                summaryText.setText(getString(R.string.novacraft_runtime_summary, installedCount, readyCount));
                storageText.setText(getString(R.string.novacraft_runtime_storage, formatStorage(measuredStorage)));
                activeRuntimeText.setText(selectedRuntimeInfo == null || selectedRuntimeInfo.versionString == null
                        ? getString(R.string.novacraft_runtime_active_unknown)
                        : getString(R.string.novacraft_runtime_active,
                                selectedRuntimeInfo.name + " · Java " + selectedRuntimeInfo.javaVersion));

                if (requiredJava == null) {
                    requirementText.setText(R.string.novacraft_runtime_requirement_pending);
                    activeHealthText.setText(R.string.novacraft_runtime_requirement_pending);
                } else {
                    requirementText.setText(getString(R.string.novacraft_runtime_requirement, requiredJava));
                    boolean selectedMeetsRequirement = selectedRuntimeInfo != null
                            && isCompatible(selectedRuntimeInfo, deviceArchitecture)
                            && selectedRuntimeInfo.javaVersion >= requiredJava;
                    activeHealthText.setText(selectedMeetsRequirement
                            ? R.string.novacraft_runtime_active_ok
                            : R.string.novacraft_runtime_active_warn);
                }

                autoSelectionText.setText(automaticSelection
                        ? R.string.novacraft_runtime_auto_on : R.string.novacraft_runtime_auto_off);

                runtimeCards.removeAllViews();
                for (RuntimeCard card : cards) addRuntimeCard(card);
                emptyText.setVisibility(cards.isEmpty() ? View.VISIBLE : View.GONE);
                if (errorMessage != null) {
                    emptyText.setVisibility(View.VISIBLE);
                    emptyText.setText(getString(R.string.novacraft_runtime_scan_failed, errorMessage));
                } else {
                    emptyText.setText(R.string.novacraft_runtime_empty);
                }

                if (pendingDownloads.isEmpty()) {
                    downloadsText.setText(R.string.novacraft_runtime_downloads_none);
                } else {
                    StringBuilder versions = new StringBuilder();
                    for (NewJREUtil.ExternalRuntime runtime : pendingDownloads) {
                        if (versions.length() > 0) versions.append(", ");
                        versions.append("Java ").append(runtime.majorVersion);
                    }
                    downloadsText.setText(getString(R.string.novacraft_runtime_downloads, versions));
                }
            });
        }, "NovaCraft-Runtime-Audit").start();
    }

    private Integer detectRequiredJava(@Nullable MinecraftProfile profile) {
        if (profile == null || profile.lastVersionId == null) return null;
        try {
            String normalized = AsyncMinecraftDownloader.normalizeVersionId(profile.lastVersionId);
            JMinecraftVersionList.Version info = AsyncMinecraftDownloader.getListedVersion(normalized);
            if (info != null && info.javaVersion != null && info.javaVersion.majorVersion > 0) {
                return info.javaVersion.majorVersion;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void addRuntimeCard(RuntimeCard cardData) {
        Runtime runtime = cardData.runtime;
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(10), dp(13), dp(10));
        card.setBackgroundResource(R.drawable.background_overlay);

        TextView title = new TextView(requireContext());
        title.setText(runtime.javaVersion > 0
                ? "Java " + runtime.javaVersion + "  ·  " + runtime.name
                : runtime.name);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_text));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        card.addView(title);

        TextView details = new TextView(requireContext());
        details.setText(getString(R.string.novacraft_runtime_card_details,
                runtime.versionString == null ? "Unknown build" : runtime.versionString,
                runtime.arch == null ? "Unknown ABI" : runtime.arch));
        details.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary_text));
        details.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(4);
        card.addView(details, detailParams);

        TextView status = new TextView(requireContext());
        int statusText;
        int statusColor;
        if (cardData.corrupt) {
            statusText = R.string.novacraft_runtime_status_corrupt;
            statusColor = R.color.novacraft_text_muted;
        } else if (!cardData.compatible) {
            statusText = R.string.novacraft_runtime_status_incompatible;
            statusColor = R.color.novacraft_text_muted;
        } else if (cardData.active) {
            statusText = R.string.novacraft_runtime_status_active;
            statusColor = R.color.novacraft_accent;
        } else if (cardData.isDefault) {
            statusText = R.string.novacraft_runtime_status_default;
            statusColor = R.color.novacraft_accent;
        } else {
            statusText = R.string.novacraft_runtime_status_ready;
            statusColor = R.color.secondary_text;
        }
        status.setText(statusText);
        status.setTextColor(ContextCompat.getColor(requireContext(), statusColor));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        status.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(5);
        card.addView(status, statusParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(8);
        runtimeCards.addView(card, cardParams);
    }

    private static boolean isCompatible(Runtime runtime, int deviceArchitecture) {
        return runtime != null && runtime.versionString != null && runtime.javaVersion > 0
                && runtime.arch != null
                && Architecture.archAsInt(runtime.arch) == deviceArchitecture;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static long directorySize(File file) {
        if (file == null || !file.exists()) return 0L;
        try {
            // Do not follow symlinks in runtime archives; this also avoids symlink loops.
            if (!file.getAbsolutePath().equals(file.getCanonicalPath())) return 0L;
        } catch (Exception ignored) {
            return 0L;
        }
        if (file.isFile()) return file.length();
        File[] children = file.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (File child : children) {
            long length = directorySize(child);
            if (Long.MAX_VALUE - total < length) return Long.MAX_VALUE;
            total += length;
        }
        return total;
    }

    private static String formatStorage(long bytes) {
        double megabytes = bytes / (1024.0 * 1024.0);
        if (megabytes >= 1024.0) {
            return String.format(java.util.Locale.US, "%.2f GB", megabytes / 1024.0);
        }
        return String.format(java.util.Locale.US, "%.1f MB", megabytes);
    }

    private static class RuntimeCard {
        final Runtime runtime;
        final boolean compatible;
        final boolean corrupt;
        final boolean isDefault;
        final boolean active;

        RuntimeCard(Runtime runtime, boolean compatible, boolean corrupt, boolean isDefault, boolean active) {
            this.runtime = runtime;
            this.compatible = compatible;
            this.corrupt = corrupt;
            this.isDefault = isDefault;
            this.active = active;
        }
    }
}
