package net.kdt.pojavlaunch.prefs.screens;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Local Java world manager for the currently selected Minecraft installation.
 * Imports are staged and validated before a new world folder is added.
 */
public class LauncherPreferenceWorldsFragment extends LauncherPreferenceFragment {
    private static final long MAX_ARCHIVE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_WORLD_BYTES = 512L * 1024L * 1024L;
    private static final int MAX_ARCHIVE_ENTRIES = 10000;

    private PreferenceCategory worldListCategory;
    private File pendingExportWorld;

    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onImportPicked);

    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument(), this::onExportPicked);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_worlds);
        worldListCategory = requirePreference("worlds_list", PreferenceCategory.class);

        requirePreference("worlds_import").setOnPreferenceClickListener(preference -> {
            importLauncher.launch(new String[] {
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream"
            });
            return true;
        });
        requirePreference("worlds_refresh").setOnPreferenceClickListener(preference -> {
            refreshWorldList();
            return true;
        });

        bindContentFolder("worlds_open_mods", "mods");
        bindContentFolder("worlds_open_resourcepacks", "resourcepacks");
        bindContentFolder("worlds_open_shaderpacks", "shaderpacks");
        bindContentFolder("worlds_open_screenshots", "screenshots");

        updateActiveProfileSummary();
        refreshWorldList();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (worldListCategory != null) {
            updateActiveProfileSummary();
            refreshWorldList();
        }
    }

    private void bindContentFolder(String preferenceKey, String folderName) {
        requirePreference(preferenceKey).setOnPreferenceClickListener(preference -> {
            File folder = new File(getGameDirectory(), folderName);
            try {
                FileUtils.ensureDirectory(folder);
                Tools.openPath(requireContext(), folder, false);
            } catch (Exception error) {
                Toast.makeText(requireContext(),
                        getString(R.string.novacraft_worlds_folder_open_failed, folderName),
                        Toast.LENGTH_LONG).show();
            }
            return true;
        });
    }

    private File getGameDirectory() {
        Tools.initStorageConstants(requireContext());
        LauncherProfiles.load();
        String profileKey = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        MinecraftProfile selected = LauncherProfiles.mainProfileJson.profiles.get(profileKey);
        return selected == null ? new File(Tools.DIR_GAME_NEW) : Tools.getGameDirPath(selected);
    }

    private File getSavesDirectory() {
        return new File(getGameDirectory(), "saves");
    }

    private void updateActiveProfileSummary() {
        try {
            File gameDirectory = getGameDirectory();
            String profileName = LauncherProfiles.getCurrentProfile().name;
            if (profileName == null || profileName.trim().isEmpty()) profileName = getString(R.string.unnamed);
            requirePreference("worlds_active_profile").setSummary(
                    getString(R.string.novacraft_worlds_active_profile_value, profileName, gameDirectory.getAbsolutePath()));
        } catch (Exception error) {
            requirePreference("worlds_active_profile").setSummary(
                    getString(R.string.novacraft_worlds_profile_unavailable));
        }
    }

    private void refreshWorldList() {
        if (worldListCategory == null || !isAdded()) return;
        worldListCategory.removeAll();
        File savesDirectory;
        try {
            savesDirectory = getSavesDirectory();
            FileUtils.ensureDirectory(savesDirectory);
        } catch (IOException | RuntimeException error) {
            addEmptyWorldMessage(R.string.novacraft_worlds_folder_unavailable);
            return;
        }

        File[] entries = savesDirectory.listFiles();
        ArrayList<File> worlds = new ArrayList<>();
        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory() && new File(entry, "level.dat").isFile()) worlds.add(entry);
            }
        }
        Collections.sort(worlds, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        if (worlds.isEmpty()) {
            addEmptyWorldMessage(R.string.novacraft_worlds_empty);
            return;
        }

        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        for (File world : worlds) {
            Preference item = new Preference(requireContext());
            item.setTitle(world.getName());
            item.setSummary(getString(R.string.novacraft_worlds_last_modified,
                    dateFormat.format(world.lastModified())));
            item.setOnPreferenceClickListener(preference -> {
                showWorldActions(world);
                return true;
            });
            worldListCategory.addPreference(item);
        }
    }

    private void addEmptyWorldMessage(int messageId) {
        Preference empty = new Preference(requireContext());
        empty.setTitle(messageId);
        empty.setSelectable(false);
        empty.setPersistent(false);
        worldListCategory.addPreference(empty);
    }

    private void showWorldActions(File world) {
        if (!isDirectWorldChild(world)) {
            Toast.makeText(requireContext(), R.string.novacraft_worlds_invalid_folder,
                    Toast.LENGTH_LONG).show();
            return;
        }
        String[] actions = {
                getString(R.string.novacraft_worlds_action_backup),
                getString(R.string.novacraft_worlds_action_rename),
                getString(R.string.novacraft_worlds_action_open),
                getString(R.string.novacraft_worlds_action_delete)
        };
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(world.getName())
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) startWorldExport(world);
                    else if (which == 1) showRenameDialog(world);
                    else if (which == 2) openWorldFolder(world);
                    else if (which == 3) confirmWorldDelete(world);
                })
                .show();
    }

    private boolean isDirectWorldChild(File world) {
        try {
            File saves = getSavesDirectory().getCanonicalFile();
            File candidate = world.getCanonicalFile();
            return candidate.getParentFile() != null
                    && candidate.getParentFile().equals(saves)
                    && candidate.isDirectory()
                    && new File(candidate, "level.dat").isFile();
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    private void openWorldFolder(File world) {
        try {
            Tools.openPath(requireContext(), world, false);
        } catch (Exception error) {
            Toast.makeText(requireContext(), R.string.novacraft_worlds_folder_open_failed_generic,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showRenameDialog(File world) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(world.getName());
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.novacraft_worlds_rename_title)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_save, (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!isValidWorldName(newName)) {
                        Toast.makeText(requireContext(), R.string.novacraft_worlds_bad_name,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    File destination = new File(getSavesDirectory(), newName);
                    if (destination.exists()) {
                        Toast.makeText(requireContext(), R.string.novacraft_worlds_name_exists,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (!isDirectWorldChild(world) || !world.renameTo(destination)) {
                        Toast.makeText(requireContext(), R.string.novacraft_worlds_rename_failed,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), R.string.novacraft_worlds_renamed,
                                Toast.LENGTH_SHORT).show();
                    }
                    refreshWorldList();
                })
                .show();
    }

    private boolean isValidWorldName(String name) {
        if (name == null || name.isEmpty() || name.length() > 80
                || ".".equals(name) || "..".equals(name)) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c < 32) return false;
        }
        return true;
    }

    private void confirmWorldDelete(File world) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.novacraft_worlds_delete_title)
                .setMessage(getString(R.string.novacraft_worlds_delete_message, world.getName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_delete, (dialog, which) -> {
                    if (!isDirectWorldChild(world) || !deleteRecursively(world)) {
                        Toast.makeText(requireContext(), R.string.novacraft_worlds_delete_failed,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), R.string.novacraft_worlds_deleted,
                                Toast.LENGTH_SHORT).show();
                    }
                    refreshWorldList();
                })
                .show();
    }

    private boolean deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && !deleteRecursively(child)) return false;
                if (!child.delete()) return false;
            }
        }
        return file.delete();
    }

    private void startWorldExport(File world) {
        if (!isDirectWorldChild(world)) {
            Toast.makeText(requireContext(), R.string.novacraft_worlds_invalid_folder,
                    Toast.LENGTH_LONG).show();
            return;
        }
        pendingExportWorld = world;
        exportLauncher.launch(world.getName() + "-NovaCraft-backup.zip");
    }

    private void onExportPicked(Uri destination) {
        final File world = pendingExportWorld;
        pendingExportWorld = null;
        if (destination == null || world == null) return;
        final Activity host = getActivity();
        if (host == null) return;

        PojavApplication.sExecutorService.execute(() -> {
            String errorMessage = null;
            try (OutputStream raw = host.getContentResolver().openOutputStream(destination);
                 ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw))) {
                if (raw == null) throw new IOException("Could not open destination");
                if (!isDirectWorldChild(world)) throw new IOException("World folder is no longer available");
                zip.putNextEntry(new ZipEntry(world.getName() + "/"));
                zip.closeEntry();
                long[] totalBytes = {0L};
                int[] fileCount = {0};
                Set<String> visitedDirectories = new HashSet<>();
                addDirectoryToZip(world, world, zip, totalBytes, fileCount, visitedDirectories);
            } catch (Exception error) {
                errorMessage = error.getMessage() == null ? "Unknown error" : error.getMessage();
            }
            final String result = errorMessage;
            host.runOnUiThread(() -> {
                if (!isAdded()) return;
                if (result == null) Toast.makeText(host, R.string.novacraft_worlds_backup_done,
                        Toast.LENGTH_LONG).show();
                else Toast.makeText(host, getString(R.string.novacraft_worlds_backup_failed, result),
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void addDirectoryToZip(File directory, File worldRoot, ZipOutputStream zip,
                                   long[] totalBytes, int[] fileCount,
                                   Set<String> visitedDirectories) throws IOException {
        String rootCanonical = worldRoot.getCanonicalPath();
        String directoryCanonical = directory.getCanonicalPath();
        if (!directoryCanonical.equals(rootCanonical)
                && !directoryCanonical.startsWith(rootCanonical + File.separator)) return;
        if (!visitedDirectories.add(directoryCanonical)) return;

        File[] children = directory.listFiles();
        if (children == null) throw new IOException("Cannot read world folder");
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File child : children) {
            String canonical = child.getCanonicalPath();
            if (!canonical.startsWith(rootCanonical + File.separator)) continue;
            String relative = directory.getAbsolutePath().equals(worldRoot.getAbsolutePath())
                    ? child.getName()
                    : directory.getAbsolutePath().substring(worldRoot.getAbsolutePath().length() + 1)
                    + "/" + child.getName();

            if (child.isDirectory()) {
                addDirectoryToZip(child, worldRoot, zip, totalBytes, fileCount, visitedDirectories);
            } else if (child.isFile()) {
                if (++fileCount[0] > MAX_ARCHIVE_ENTRIES) throw new IOException("World contains too many files");
                try (InputStream input = new BufferedInputStream(new FileInputStream(child))) {
                    zip.putNextEntry(new ZipEntry(worldRoot.getName() + "/" + relative));
                    byte[] buffer = new byte[32768];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        totalBytes[0] += read;
                        if (totalBytes[0] > MAX_WORLD_BYTES) throw new IOException("World backup exceeds 512 MB");
                        zip.write(buffer, 0, read);
                    }
                    zip.closeEntry();
                }
            }
        }
    }

    private void onImportPicked(Uri source) {
        if (source == null) return;
        final Activity host = getActivity();
        if (host == null) return;
        final File savesDirectory = getSavesDirectory();

        PojavApplication.sExecutorService.execute(() -> {
            File archive = new File(host.getCacheDir(), "novacraft-world-" + UUID.randomUUID() + ".zip");
            File staging = null;
            String importedName = null;
            String errorMessage = null;
            try {
                copyArchiveToCache(host, source, archive);
                try (ZipFile zip = new ZipFile(archive)) {
                    String levelDatPath = findLevelDat(zip);
                    int lastSlash = levelDatPath.lastIndexOf('/');
                    String rootPrefix = lastSlash < 0 ? "" : levelDatPath.substring(0, lastSlash + 1);
                    String suggestedName = rootPrefix.isEmpty()
                            ? baseName(getDisplayName(host, source))
                            : rootPrefix.substring(0, rootPrefix.length() - 1);
                    int nameSlash = suggestedName.lastIndexOf('/');
                    if (nameSlash >= 0) suggestedName = suggestedName.substring(nameSlash + 1);
                    suggestedName = sanitizeImportedName(suggestedName);
                    if (suggestedName.isEmpty()) suggestedName = "Imported World";

                    FileUtils.ensureDirectory(savesDirectory);
                    staging = new File(savesDirectory, ".novacraft-import-" + UUID.randomUUID());
                    if (!staging.mkdir()) throw new IOException("Could not create staging folder");

                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    byte[] buffer = new byte[32768];
                    long totalBytes = 0L;
                    int entryCount = 0;
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (++entryCount > MAX_ARCHIVE_ENTRIES) throw new IOException("Archive contains too many entries");
                        if (entry.isDirectory()) continue;
                        String normalized = normalizeZipEntry(entry.getName());
                        if (!normalized.startsWith(rootPrefix)) continue;
                        String relative = normalized.substring(rootPrefix.length());
                        if (relative.isEmpty()) continue;

                        File output = new File(staging, relative);
                        String stagingCanonical = staging.getCanonicalPath() + File.separator;
                        if (!output.getCanonicalPath().startsWith(stagingCanonical)) {
                            throw new IOException("Unsafe archive path");
                        }
                        if (output.exists()) throw new IOException("Archive contains duplicate file paths");
                        FileUtils.ensureParentDirectory(output);
                        try (InputStream input = new BufferedInputStream(zip.getInputStream(entry));
                             OutputStream target = new BufferedOutputStream(new FileOutputStream(output))) {
                            int read;
                            while ((read = input.read(buffer)) != -1) {
                                totalBytes += read;
                                if (totalBytes > MAX_WORLD_BYTES) throw new IOException("World archive exceeds 512 MB unpacked");
                                target.write(buffer, 0, read);
                            }
                        }
                    }

                    File levelDat = new File(staging, "level.dat");
                    if (!levelDat.isFile() || levelDat.length() == 0) {
                        throw new IOException("The selected archive did not contain a valid level.dat");
                    }

                    String uniqueName = findAvailableWorldName(savesDirectory, suggestedName);
                    File destination = new File(savesDirectory, uniqueName);
                    if (!staging.renameTo(destination)) throw new IOException("Could not move the imported world into saves");
                    importedName = uniqueName;
                    staging = null;
                }
            } catch (Exception error) {
                errorMessage = error.getMessage() == null ? "Unknown error" : error.getMessage();
            } finally {
                archive.delete();
                if (staging != null) deleteRecursively(staging);
            }

            final String imported = importedName;
            final String failure = errorMessage;
            host.runOnUiThread(() -> {
                if (!isAdded()) return;
                if (imported != null) {
                    Toast.makeText(host, getString(R.string.novacraft_worlds_import_done, imported),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(host, getString(R.string.novacraft_worlds_import_failed, failure),
                            Toast.LENGTH_LONG).show();
                }
                refreshWorldList();
            });
        });
    }

    private void copyArchiveToCache(Context context, Uri source, File destination) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(source);
             OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            if (input == null) throw new IOException("Could not open selected archive");
            byte[] buffer = new byte[32768];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_ARCHIVE_BYTES) throw new IOException("ZIP file exceeds 512 MB");
                output.write(buffer, 0, read);
            }
        }
    }

    private String findLevelDat(ZipFile zip) throws IOException {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        String found = null;
        int count = 0;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            String normalized = normalizeZipEntry(entry.getName());
            if ("level.dat".equals(normalized) || normalized.endsWith("/level.dat")) {
                found = normalized;
                if (++count > 1) throw new IOException("Archive contains more than one world");
            }
        }
        if (found == null) throw new IOException("No Java world level.dat was found in this ZIP");
        return found;
    }

    private String normalizeZipEntry(String raw) throws IOException {
        String path = raw.replace('\\', '/');
        if (path.startsWith("/") || path.matches("(?i)^[a-z]:.*")) {
            throw new IOException("Archive contains an absolute path");
        }
        StringBuilder normalized = new StringBuilder();
        String[] parts = path.split("/");
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) throw new IOException("Archive contains an unsafe path");
            if (normalized.length() > 0) normalized.append('/');
            normalized.append(part);
        }
        return normalized.toString();
    }

    private String getDisplayName(Context context, Uri source) {
        try (android.database.Cursor cursor = context.getContentResolver().query(
                source, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        return source.getLastPathSegment() == null ? "Imported World" : source.getLastPathSegment();
    }

    private String baseName(String displayName) {
        if (displayName == null) return "Imported World";
        String name = displayName;
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name;
    }

    private String sanitizeImportedName(String value) {
        if (value == null) return "";
        String name = value.trim();
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < name.length() && output.length() < 80; i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '.' || c == '_' || c == '-') output.append(c);
        }
        String result = output.toString().trim();
        while (result.startsWith(".")) result = result.substring(1);
        return ".".equals(result) || "..".equals(result) ? "" : result;
    }

    private String findAvailableWorldName(File saves, String requested) {
        String candidate = requested;
        int suffix = 2;
        while (new File(saves, candidate).exists()) {
            String suffixText = " (" + suffix++ + ")";
            int maxBase = Math.max(1, 80 - suffixText.length());
            String base = requested.length() > maxBase ? requested.substring(0, maxBase) : requested;
            candidate = base + suffixText;
        }
        return candidate;
    }
}
