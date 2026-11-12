package net.kdt.pojavlaunch.fragments;

import android.app.Activity;
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
import net.kdt.pojavlaunch.modloaders.modpacks.ModManagerAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.ModMetadataReader;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private TextView emptyText;
    private ModManagerAdapter adapter;
    private File modsDirectory;
    private String activeLoader;
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
        emptyText = view.findViewById(R.id.mod_manager_empty);
        modList = view.findViewById(R.id.mod_manager_list);
        adapter = new ModManagerAdapter(this);
        modList.setLayoutManager(new LinearLayoutManager(requireContext()));
        modList.setAdapter(adapter);

        Button importButton = view.findViewById(R.id.mod_manager_import);
        Button browseButton = view.findViewById(R.id.mod_manager_browse);
        Button refreshButton = view.findViewById(R.id.mod_manager_refresh);

        importButton.setOnClickListener(v -> importLauncher.launch("*/*"));
        browseButton.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean(SearchModFragment.ARG_IS_MODPACK, false);
            Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG + "_SINGLE_MODS", args);
        });
        refreshButton.setOnClickListener(v -> refreshMods());
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
        File gameDirectory;
        if (profile == null) gameDirectory = new File(Tools.DIR_GAME_NEW);
        else gameDirectory = Tools.getGameDirPath(profile);
        return new File(gameDirectory, "mods");
    }

    private void refreshMods() {
        if (getContext() == null || adapter == null) return;
        final long token = ++scanToken;
        MinecraftProfile profile = getActiveProfile();
        activeLoader = ModCompatibility.detectLoader(profile);
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
        new Thread(() -> {
            ArrayList<ModManagerAdapter.ModEntry> entries = new ArrayList<>();
            File[] files = scanDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isFile()) continue;
                    String lower = file.getName().toLowerCase(Locale.ROOT);
                    boolean enabled = lower.endsWith(".jar");
                    if (!enabled && !lower.endsWith(".jar.disabled")) continue;
                    ModMetadataReader.Info info = ModMetadataReader.read(file.getName(), file);
                    entries.add(new ModManagerAdapter.ModEntry(file, info, enabled, activeLoader));
                }
            }
            Collections.sort(entries, Comparator.comparing(entry -> entry.file.getName().toLowerCase(Locale.ROOT)));
            mainHandler.post(() -> {
                if (token != scanToken || getView() == null) return;
                adapter.setEntries(entries);
                int enabled = 0;
                for (ModManagerAdapter.ModEntry entry : entries) if (entry.enabled) enabled++;
                summaryText.setText(getString(R.string.novacraft_mods_summary, enabled, entries.size() - enabled));
                emptyText.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }, "NovaCraft-Mod-Scanner").start();
    }

    @Override
    public void onToggle(ModManagerAdapter.ModEntry entry) {
        if (!entry.enabled && entry.info != null && entry.info.hasLoaderMismatch(activeLoader)) {
            String message = getString(R.string.novacraft_mod_loader_mismatch, entry.info.loader, activeLoader);
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.novacraft_mod_compatibility_warning)
                    .setMessage(message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.novacraft_mod_enable_anyway, (dialog, which) -> toggleFile(entry.file))
                    .show();
            return;
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
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.novacraft_mod_remove_title)
                .setMessage(getString(R.string.novacraft_mod_remove_message, entry.file.getName()))
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

    private File uniqueDestination(File desired) {
        if (!desired.exists()) return desired;
        String name = desired.getName();
        String lowerName = name.toLowerCase(Locale.ROOT);
        String stem;
        String extension;
        if (lowerName.endsWith(".jar.disabled")) {
            int suffixLength = ".jar.disabled".length();
            stem = name.substring(0, name.length() - suffixLength);
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
        new Thread(() -> {
            File temporary = null;
            try {
                if (modsDirectory == null) throw new java.io.IOException("The active profile mods folder is unavailable.");
                String displayName = getDisplayName(resolver, uri);
                if (displayName == null || !displayName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    throw new java.io.IOException("Choose a .jar mod file.");
                }
                String safeName = displayName.replaceAll("[^A-Za-z0-9._-]", "_");
                File destination = uniqueDestination(new File(modsDirectory, safeName));
                temporary = new File(modsDirectory, destination.getName() + ".importing");
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
