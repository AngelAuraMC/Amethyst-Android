package net.kdt.pojavlaunch.fragments;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.ModCompatibility;
import net.kdt.pojavlaunch.modloaders.modpacks.ModHealthReport;
import net.kdt.pojavlaunch.modloaders.modpacks.ModManagerAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.ModMetadataReader;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ModManagerFragment extends Fragment implements ModManagerAdapter.Listener {
    public static final String TAG = "NovaCraftModManager";
    private static final long MAX_IMPORT_BYTES = 250L * 1024L * 1024L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String> importLauncher;
    private RecyclerView modList;
    private TextView profileText;
    private TextView pathText;
    private TextView summaryText;
    private TextView healthText;
    private TextView emptyText;
    private ModManagerAdapter adapter;
    private File modsDirectory;
    private String activeLoader;
    private String activeMinecraftVersion;
    private ArrayList<ModManagerAdapter.ModEntry> lastEntries = new ArrayList<>();
    private ModHealthReport.Report lastReport;
    private long scanToken;

    public ModManagerFragment() {
        super(R.layout.fragment_mod_manager);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        importLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::importPickedFile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        profileText = view.findViewById(R.id.mod_manager_profile);
        pathText = view.findViewById(R.id.mod_manager_path);
        summaryText = view.findViewById(R.id.mod_manager_summary);
        healthText = view.findViewById(R.id.mod_manager_health);
        emptyText = view.findViewById(R.id.mod_manager_empty);
        modList = view.findViewById(R.id.mod_manager_list);
        adapter = new ModManagerAdapter(this);
        modList.setLayoutManager(new LinearLayoutManager(requireContext()));
        modList.setAdapter(adapter);

        Button importButton = view.findViewById(R.id.mod_manager_import);
        Button browseButton = view.findViewById(R.id.mod_manager_browse);
        Button refreshButton = view.findViewById(R.id.mod_manager_refresh);
        Button checkButton = view.findViewById(R.id.mod_manager_check);
        Button resolveButton = view.findViewById(R.id.mod_manager_resolve);

        importButton.setOnClickListener(v -> importLauncher.launch("*/*"));
        browseButton.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean(SearchModFragment.ARG_IS_MODPACK, false);
            Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG + "_SINGLE_MODS", args);
        });
        refreshButton.setOnClickListener(v -> refreshMods());
        checkButton.setOnClickListener(v -> showCompatibilityReport());
        resolveButton.setOnClickListener(v -> showMissingDependencies());
        refreshMods();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) refreshMods();
    }

    private MinecraftProfile getActiveProfile() {
        try {
            LauncherProfiles.load();
            String key = LauncherPreferences.DEFAULT_PREF == null ? null :
                    LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            if (key != null && LauncherProfiles.mainProfileJson.profiles.containsKey(key)) {
                return LauncherProfiles.mainProfileJson.profiles.get(key);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private File resolveModsDirectory(MinecraftProfile profile) {
        File gameDirectory = profile == null ? new File(Tools.DIR_GAME_NEW) : Tools.getGameDirPath(profile);
        return new File(gameDirectory, "mods");
    }

    private void refreshMods() {
        if (getContext() == null || adapter == null) return;
        final long token = ++scanToken;
        MinecraftProfile profile = getActiveProfile();
        activeLoader = ModCompatibility.detectLoader(profile);
        activeMinecraftVersion = profile == null ? null : ModCompatibility.detectMinecraftVersion(profile.lastVersionId);
        modsDirectory = resolveModsDirectory(profile);
        if (!modsDirectory.exists() && !modsDirectory.mkdirs()) {
            Toast.makeText(requireContext(), R.string.novacraft_mod_folder_error, Toast.LENGTH_LONG).show();
        }

        String version = profile == null ? getString(R.string.novacraft_profile_default) :
                (profile.lastVersionId == null ? getString(R.string.novacraft_profile_unknown) : profile.lastVersionId);
        String loaderLabel = activeLoader == null ? getString(R.string.novacraft_loader_not_detected) : activeLoader;
        profileText.setText(getString(R.string.novacraft_mod_profile_summary, version, loaderLabel));
        pathText.setText(modsDirectory.getAbsolutePath());

        final File scanDirectory = modsDirectory;
        final String scanLoader = activeLoader;
        final String scanVersion = activeMinecraftVersion;
        new Thread(() -> {
            ArrayList<ModManagerAdapter.ModEntry> entries =
                    new ArrayList<>(ModHealthReport.scanDirectory(scanDirectory, scanLoader));
            ModHealthReport.Report report = ModHealthReport.analyze(entries, scanVersion, scanLoader);
            mainHandler.post(() -> {
                if (token != scanToken || getView() == null) return;
                lastEntries = entries;
                lastReport = report;
                adapter.setEntries(entries);
                summaryText.setText(getString(R.string.novacraft_mods_summary, report.enabledMods, report.disabledMods));
                healthText.setText(report.summary());
                emptyText.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }, "NovaCraft-Mod-Scanner").start();
    }

    @Override
    public void onToggle(ModManagerAdapter.ModEntry entry) {
        if (entry.enabled && entry.info != null && entry.info.modId != null) {
            List<String> dependents = ModHealthReport.findRequiredDependents(entry.info.modId, lastEntries);
            if (!dependents.isEmpty()) {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.novacraft_mod_dependency_warning)
                        .setMessage(getString(R.string.novacraft_mod_disable_dependents,
                                android.text.TextUtils.join(", ", dependents)))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.novacraft_mod_disable_anyway, (dialog, which) -> toggleFile(entry.file))
                        .show();
                return;
            }
        }

        if (!entry.enabled && entry.info != null) {
            ArrayList<String> warnings = new ArrayList<>();
            if (entry.info.hasLoaderMismatch(activeLoader)) {
                String profileLoader = activeLoader == null
                        ? getString(R.string.novacraft_loader_not_detected) : activeLoader;
                warnings.add(getString(R.string.novacraft_mod_loader_mismatch, entry.info.loader, profileLoader));
            }
            Boolean versionOk = ModCompatibility.isMinecraftRequirementSatisfied(
                    entry.info.minecraftRequirement, activeMinecraftVersion);
            if (Boolean.FALSE.equals(versionOk)) {
                warnings.add(getString(R.string.novacraft_mod_version_mismatch,
                        entry.info.minecraftRequirement, activeMinecraftVersion));
            }
            if (!warnings.isEmpty()) {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.novacraft_mod_compatibility_warning)
                        .setMessage(android.text.TextUtils.join("\n\n", warnings))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.novacraft_mod_enable_anyway, (dialog, which) -> toggleFile(entry.file))
                        .show();
                return;
            }
        }
        toggleFile(entry.file);
    }

    private void toggleFile(File source) {
        if (source == null || !source.exists() || modsDirectory == null) {
            refreshMods();
            return;
        }
        String sourceName = source.getName();
        boolean currentlyEnabled = sourceName.toLowerCase(Locale.ROOT).endsWith(".jar");
        String newName = currentlyEnabled ? sourceName + ".disabled" :
                sourceName.substring(0, sourceName.length() - ".disabled".length());
        File target = uniqueDestination(new File(modsDirectory, newName));
        try {
            if (!source.getCanonicalFile().getParentFile().equals(modsDirectory.getCanonicalFile())) {
                throw new SecurityException("Mod file is outside the active profile mods folder.");
            }
            if (!source.renameTo(target)) throw new java.io.IOException("Android could not rename this mod file.");
            refreshMods();
        } catch (Exception error) {
            Toast.makeText(requireContext(), getString(R.string.novacraft_mod_file_action_failed, error.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRemove(ModManagerAdapter.ModEntry entry) {
        ArrayList<String> dependents = entry.info == null ? new ArrayList<>() :
                new ArrayList<>(ModHealthReport.findRequiredDependents(entry.info.modId, lastEntries));
        String warning = getString(R.string.novacraft_mod_remove_message, entry.file.getName());
        if (!dependents.isEmpty()) {
            warning += "\n\n" + getString(R.string.novacraft_mod_remove_dependents,
                    android.text.TextUtils.join(", ", dependents));
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.novacraft_mod_remove_title)
                .setMessage(warning)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_remove, (dialog, which) -> {
                    try {
                        if (!entry.file.getCanonicalFile().getParentFile().equals(modsDirectory.getCanonicalFile())) {
                            throw new SecurityException("Mod file is outside the active profile mods folder.");
                        }
                        if (!entry.file.delete()) throw new java.io.IOException("Android could not remove this mod file.");
                        refreshMods();
                    } catch (Exception error) {
                        Toast.makeText(requireContext(), getString(R.string.novacraft_mod_file_action_failed, error.getMessage()), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void showCompatibilityReport() {
        if (lastReport == null) {
            refreshMods();
            Toast.makeText(requireContext(), R.string.novacraft_mod_check_running, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.novacraft_mod_report_title)
                .setMessage(lastReport.toDisplayText())
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.novacraft_mod_resolve, (dialog, which) -> showMissingDependencies())
                .show();
    }

    private void showMissingDependencies() {
        if (lastReport == null) {
            refreshMods();
            return;
        }
        List<ModHealthReport.Issue> missing = lastReport.missingRequiredDependencies();
        ArrayList<String> ids = new ArrayList<>();
        for (ModHealthReport.Issue issue : missing) {
            if (issue.dependencyId != null && !ids.contains(issue.dependencyId)) ids.add(issue.dependencyId);
        }
        if (ids.isEmpty()) {
            Toast.makeText(requireContext(), R.string.novacraft_mod_no_missing_dependencies, Toast.LENGTH_LONG).show();
            return;
        }
        String[] choices = ids.toArray(new String[0]);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.novacraft_mod_missing_dependencies_title)
                .setItems(choices, (dialog, which) -> openDependencySearch(choices[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openDependencySearch(String dependencyId) {
        Bundle args = new Bundle();
        args.putBoolean(SearchModFragment.ARG_IS_MODPACK, false);
        args.putString(SearchModFragment.ARG_INITIAL_QUERY, dependencyId);
        Tools.swapFragment(requireActivity(), SearchModFragment.class,
                SearchModFragment.TAG + "_DEPENDENCY_" + dependencyId, args);
    }

    private File uniqueDestination(File desired) {
        if (!desired.exists()) return desired;
        String name = desired.getName();
        String lowerName = name.toLowerCase(Locale.ROOT);
        String stem;
        String extension;
        if (lowerName.endsWith(".jar.disabled")) {
            stem = name.substring(0, name.length() - ".jar.disabled".length());
            extension = ".jar.disabled";
        } else {
            int dot = name.lastIndexOf('.');
            stem = dot > 0 ? name.substring(0, dot) : name;
            extension = dot > 0 ? name.substring(dot) : "";
        }
        int suffix = 2;
        File candidate;
        do {
            candidate = new File(desired.getParentFile(), stem + "-" + suffix + extension);
            suffix++;
        } while (candidate.exists());
        return candidate;
    }

    private void importPickedFile(@Nullable Uri uri) {
        if (uri == null || getContext() == null) return;
        final ContentResolver resolver = requireContext().getContentResolver();
        final File destinationDirectory = modsDirectory;
        new Thread(() -> {
            File temporary = null;
            try {
                if (destinationDirectory == null) throw new java.io.IOException("The active profile mods folder is unavailable.");
                String displayName = getDisplayName(resolver, uri);
                if (displayName == null || !displayName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    throw new java.io.IOException("Choose a .jar mod file.");
                }
                String safeName = displayName.replaceAll("[^A-Za-z0-9._-]", "_");
                File destination = uniqueDestination(new File(destinationDirectory, safeName));
                temporary = new File(destinationDirectory, destination.getName() + ".importing");
                try (InputStream input = resolver.openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(temporary)) {
                    if (input == null) throw new java.io.IOException("Unable to open the selected file.");
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_IMPORT_BYTES) throw new java.io.IOException("The mod file exceeds the 250 MB import limit.");
                        output.write(buffer, 0, read);
                    }
                }
                if (!temporary.renameTo(destination)) throw new java.io.IOException("Unable to copy the mod into the profile folder.");
                mainHandler.post(() -> {
                    if (getContext() != null) Toast.makeText(requireContext(), R.string.novacraft_mod_import_success, Toast.LENGTH_LONG).show();
                    refreshMods();
                });
            } catch (Exception error) {
                if (temporary != null) temporary.delete();
                String message = error.getMessage() == null ? error.toString() : error.getMessage();
                mainHandler.post(() -> {
                    if (getContext() != null) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                });
            }
        }, "NovaCraft-Mod-Import").start();
    }

    private String getDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {}
        String fallback = uri.getLastPathSegment();
        return fallback == null ? null : Uri.decode(fallback);
    }
}
