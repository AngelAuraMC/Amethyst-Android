package net.kdt.pojavlaunch.fragments;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.backups.BackupArchiveUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class BackupManagerFragment extends Fragment {
    public static final String TAG = "NovaCraftBackupManager";
    private static final long MAX_IMPORT_BYTES = 2L * 1024L * 1024L * 1024L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<String> exportLauncher;

    private TextView profileText;
    private TextView locationText;
    private TextView statusText;
    private TextView emptyText;
    private LinearLayout backupList;
    private CheckBox worlds;
    private CheckBox mods;
    private CheckBox resourcePacks;
    private CheckBox shaderPacks;
    private CheckBox settings;
    private CheckBox skins;
    private File pendingExport;

    public BackupManagerFragment() {
        super(R.layout.fragment_backup_manager);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onBackupImport);
        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument(), this::onBackupExport);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        profileText = view.findViewById(R.id.backup_manager_profile);
        locationText = view.findViewById(R.id.backup_manager_location);
        statusText = view.findViewById(R.id.backup_manager_status);
        emptyText = view.findViewById(R.id.backup_manager_empty);
        backupList = view.findViewById(R.id.backup_manager_list);
        worlds = view.findViewById(R.id.backup_include_worlds);
        mods = view.findViewById(R.id.backup_include_mods);
        resourcePacks = view.findViewById(R.id.backup_include_resourcepacks);
        shaderPacks = view.findViewById(R.id.backup_include_shaderpacks);
        settings = view.findViewById(R.id.backup_include_settings);
        skins = view.findViewById(R.id.backup_include_skins);

        view.findViewById(R.id.backup_manager_create).setOnClickListener(v -> createLocalBackup());
        view.findViewById(R.id.backup_manager_import).setOnClickListener(v ->
                importLauncher.launch(new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"}));
        view.findViewById(R.id.backup_manager_refresh).setOnClickListener(v -> refreshBackups());
        refreshProfileSummary();
        refreshBackups();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (backupList != null) {
            refreshProfileSummary();
            refreshBackups();
        }
    }

    private File getBackupDirectory() {
        File folder = new File(requireContext().getFilesDir(), "novacraft/backups");
        if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Unable to create private backup folder.");
        return folder;
    }

    private File getSkinDirectory() {
        File folder = new File(requireContext().getFilesDir(), "novacraft/skins");
        if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Unable to create skin folder.");
        return folder;
    }

    private File getGameDirectory() {
        Tools.initStorageConstants(requireContext());
        LauncherProfiles.load();
        MinecraftProfile profile = LauncherProfiles.getCurrentProfile();
        return profile == null ? new File(Tools.DIR_GAME_NEW) : Tools.getGameDirPath(profile);
    }

    private void refreshProfileSummary() {
        try {
            File gameDirectory = getGameDirectory();
            String name = LauncherProfiles.getCurrentProfile().name;
            if (name == null || name.trim().isEmpty()) name = getString(R.string.novacraft_profile_default);
            profileText.setText(getString(R.string.novacraft_backup_profile, name));
            locationText.setText(getString(R.string.novacraft_backup_location, gameDirectory.getAbsolutePath()));
        } catch (Exception error) {
            profileText.setText(getString(R.string.novacraft_backup_profile, getString(R.string.novacraft_profile_unknown)));
            locationText.setText("");
        }
    }

    private Set<String> selectedCategories() {
        Set<String> selected = new HashSet<>();
        if (worlds.isChecked()) selected.add(BackupArchiveUtils.WORLDS);
        if (mods.isChecked()) selected.add(BackupArchiveUtils.MODS);
        if (resourcePacks.isChecked()) selected.add(BackupArchiveUtils.RESOURCE_PACKS);
        if (shaderPacks.isChecked()) selected.add(BackupArchiveUtils.SHADER_PACKS);
        if (settings.isChecked()) selected.add(BackupArchiveUtils.PROFILE_SETTINGS);
        if (skins.isChecked()) selected.add(BackupArchiveUtils.SKINS);
        return selected;
    }

    private void createLocalBackup() {
        Set<String> categories = selectedCategories();
        if (categories.isEmpty()) {
            Toast.makeText(requireContext(), R.string.novacraft_backup_no_categories, Toast.LENGTH_SHORT).show();
            return;
        }
        File gameDirectory;
        File skinDirectory;
        File output;
        String profileName;
        try {
            gameDirectory = getGameDirectory();
            skinDirectory = getSkinDirectory();
            profileName = LauncherProfiles.getCurrentProfile().name;
            if (profileName == null || profileName.trim().isEmpty()) profileName = "profile";
            output = new File(getBackupDirectory(), "NovaCraft-" + safeName(profileName) + "-" + timestamp() + ".zip");
        } catch (Exception error) {
            showStatus(getString(R.string.novacraft_backup_create_failed, error.getMessage()));
            return;
        }

        setBusy(true, R.string.novacraft_backup_status_working);
        final File gameDirCopy = gameDirectory;
        final File skinDirCopy = skinDirectory;
        final File outputCopy = output;
        final String profileCopy = profileName;
        new Thread(() -> {
            try {
                BackupArchiveUtils.createBackup(gameDirCopy, skinDirCopy, profileCopy, categories, outputCopy);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setBusy(false, 0);
                    showStatus(getString(R.string.novacraft_backup_created));
                    refreshBackups();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setBusy(false, 0);
                    showStatus(getString(R.string.novacraft_backup_create_failed, message(error)));
                });
            }
        }, "NovaCraft-Backup-Create").start();
    }

    private void onBackupImport(@Nullable Uri uri) {
        if (uri == null || getContext() == null) return;
        setBusy(true, R.string.novacraft_backup_status_working);
        final android.content.ContentResolver resolver = requireContext().getContentResolver();
        final File backupDirectory = getBackupDirectory();
        final String invalidArchiveMessage = getString(R.string.novacraft_backup_invalid);
        final String tooLargeMessage = getString(R.string.novacraft_backup_too_large);
        new Thread(() -> {
            File temporary = null;
            try {
                String name = displayName(uri);
                if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    throw new java.io.IOException(invalidArchiveMessage);
                }
                temporary = new File(backupDirectory, safeName(name) + ".importing");
                long total = 0;
                try (InputStream input = resolver.openInputStream(uri);
                     OutputStream output = new FileOutputStream(temporary)) {
                    if (input == null) throw new java.io.IOException("Unable to read selected archive.");
                    byte[] buffer = new byte[32768];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_IMPORT_BYTES) throw new java.io.IOException(tooLargeMessage);
                        output.write(buffer, 0, read);
                    }
                }
                BackupArchiveUtils.validateBackup(temporary);
                File destination = uniqueBackupFile(new File(backupDirectory, safeName(name)));
                if (!temporary.renameTo(destination)) throw new java.io.IOException("Unable to save imported backup.");
                File finalDestination = destination;
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setBusy(false, 0);
                    showStatus(getString(R.string.novacraft_backup_imported));
                    refreshBackups();
                });
            } catch (Exception error) {
                if (temporary != null) temporary.delete();
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setBusy(false, 0);
                    showStatus(getString(R.string.novacraft_backup_import_failed, message(error)));
                });
            }
        }, "NovaCraft-Backup-Import").start();
    }

    private void onBackupExport(@Nullable Uri uri) {
        if (uri == null || pendingExport == null || getContext() == null) return;
        File source = pendingExport;
        pendingExport = null;
        final android.content.ContentResolver resolver = requireContext().getContentResolver();
        setBusy(true, R.string.novacraft_backup_status_working);
        new Thread(() -> {
            try (InputStream input = new java.io.BufferedInputStream(new java.io.FileInputStream(source));
                 OutputStream output = resolver.openOutputStream(uri, "wt")) {
                if (output == null) throw new java.io.IOException("Unable to open destination file.");
                byte[] buffer = new byte[32768];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setBusy(false, 0);
                    showStatus(getString(R.string.novacraft_backup_export_started));
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setBusy(false, 0);
                    showStatus(getString(R.string.novacraft_backup_export_failed, message(error)));
                });
            }
        }, "NovaCraft-Backup-Export").start();
    }

    private void confirmRestore(File archive) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.novacraft_backup_restore_title)
                .setMessage(R.string.novacraft_backup_restore_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.novacraft_backup_restore, (dialog, which) -> restoreBackup(archive))
                .show();
    }

    private void restoreBackup(File archive) {
        final File gameDirectory;
        final File skinDirectory;
        try {
            gameDirectory = getGameDirectory();
            skinDirectory = getSkinDirectory();
        } catch (Exception error) {
            showStatus(getString(R.string.novacraft_backup_restore_failed, message(error)));
            return;
        }
        setBusy(true, R.string.novacraft_backup_status_working);
        new Thread(() -> {
            try {
                BackupArchiveUtils.RestoreResult result =
                        BackupArchiveUtils.restoreBackup(archive, gameDirectory, skinDirectory);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setBusy(false, 0);
                    showStatus(getString(R.string.novacraft_backup_restore_done,
                            result.restoredFiles, result.skippedEntries));
                    refreshBackups();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setBusy(false, 0);
                    showStatus(getString(R.string.novacraft_backup_restore_failed, message(error)));
                });
            }
        }, "NovaCraft-Backup-Restore").start();
    }

    private void refreshBackups() {
        if (!isAdded() || backupList == null) return;
        backupList.removeAllViews();
        File[] backups;
        try {
            backups = getBackupDirectory().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".zip"));
        } catch (Exception error) {
            backups = null;
        }
        if (backups == null || backups.length == 0) {
            emptyText.setVisibility(View.VISIBLE);
            return;
        }
        Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());
        emptyText.setVisibility(View.GONE);
        for (File backup : backups) addBackupCard(backup);
    }

    private void addBackupCard(File backup) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(12);
        card.setPadding(padding, padding, padding, padding);
        card.setBackgroundResource(R.drawable.background_overlay);

        TextView title = new TextView(requireContext());
        title.setText(backup.getName());
        title.setTextColor(getResources().getColor(R.color.primary_text));
        title.setTextSize(13);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        card.addView(title);

        TextView summary = new TextView(requireContext());
        summary.setText(getString(R.string.novacraft_backup_archive_info,
                BackupArchiveUtils.formatBytes(backup.length()),
                android.text.format.DateFormat.getMediumDateFormat(requireContext()).format(new java.util.Date(backup.lastModified()))));
        summary.setTextColor(getResources().getColor(R.color.secondary_text));
        summary.setTextSize(11);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
        summaryParams.topMargin = dp(5);
        card.addView(summary, summaryParams);

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button restore = new Button(requireContext());
        restore.setText(R.string.novacraft_backup_restore);
        restore.setOnClickListener(v -> confirmRestore(backup));
        Button export = new Button(requireContext());
        export.setText(R.string.novacraft_backup_export);
        export.setOnClickListener(v -> {
            pendingExport = backup;
            exportLauncher.launch(backup.getName());
        });
        Button delete = new Button(requireContext());
        delete.setText(R.string.novacraft_backup_delete);
        delete.setOnClickListener(v -> confirmDelete(backup));
        actions.addView(restore, new LinearLayout.LayoutParams(0, -2, 1f));
        actions.addView(export, new LinearLayout.LayoutParams(0, -2, 1f));
        actions.addView(delete, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.topMargin = dp(5);
        card.addView(actions, actionsParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(8);
        backupList.addView(card, cardParams);
    }

    private void confirmDelete(File backup) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.novacraft_backup_delete_title)
                .setMessage(backup.getName())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.novacraft_backup_delete, (dialog, which) -> {
                    if (backup.delete()) showStatus(getString(R.string.novacraft_backup_deleted));
                    refreshBackups();
                }).show();
    }

    private void setBusy(boolean busy, int messageId) {
        View view = getView();
        if (view == null) return;
        view.findViewById(R.id.backup_manager_create).setEnabled(!busy);
        view.findViewById(R.id.backup_manager_import).setEnabled(!busy);
        if (busy && messageId != 0) statusText.setText(messageId);
    }

    private void showStatus(String message) {
        if (statusText != null) statusText.setText(message);
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = requireContext().getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) return cursor.getString(column);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static File uniqueBackupFile(File file) {
        if (!file.exists()) return file;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : ".zip";
        int index = 2;
        File candidate;
        do {
            candidate = new File(file.getParentFile(), base + "-" + index + ext);
            index++;
        } while (candidate.exists());
        return candidate;
    }

    private static String safeName(String name) {
        String safe = name == null ? "backup.zip" : name.replaceAll("[^A-Za-z0-9._-]", "_").replace("..", "_");
        return safe.isEmpty() ? "backup.zip" : safe;
    }

    private static String timestamp() {
        return new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new java.util.Date());
    }

    private static String message(Throwable error) {
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
