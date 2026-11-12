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
import net.kdt.pojavlaunch.modloaders.modpacks.PackArchiveInspector;
import net.kdt.pojavlaunch.modloaders.modpacks.PackManagerAdapter;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PackManagerFragment extends Fragment implements PackManagerAdapter.Listener {
    public static final String TAG = "NovaCraftPackManager";
    private static final long MAX_IMPORT_BYTES = 250L * 1024L * 1024L;
    private static final String DISABLED_DIRECTORY = ".novacraft-disabled";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String[]> importLauncher;
    private RecyclerView packList;
    private TextView profileText;
    private TextView folderText;
    private TextView summaryText;
    private TextView helpText;
    private PackManagerAdapter adapter;
    private int selectedType = PackArchiveInspector.RESOURCE_PACK;
    private File activeFolder;
    private long scanToken;

    public PackManagerFragment() {
        super(R.layout.fragment_pack_manager);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onImportPicked);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        profileText = view.findViewById(R.id.pack_manager_profile);
        folderText = view.findViewById(R.id.pack_manager_path);
        summaryText = view.findViewById(R.id.pack_manager_summary);
        helpText = view.findViewById(R.id.pack_manager_help);
        packList = view.findViewById(R.id.pack_manager_list);
        adapter = new PackManagerAdapter(this);
        packList.setLayoutManager(new LinearLayoutManager(requireContext()));
        packList.setAdapter(adapter);

        Button resourceButton = view.findViewById(R.id.pack_manager_resource_tab);
        Button shaderButton = view.findViewById(R.id.pack_manager_shader_tab);
        Button importButton = view.findViewById(R.id.pack_manager_import);
        Button refreshButton = view.findViewById(R.id.pack_manager_refresh);

        resourceButton.setOnClickListener(v -> selectType(PackArchiveInspector.RESOURCE_PACK));
        shaderButton.setOnClickListener(v -> selectType(PackArchiveInspector.SHADER_PACK));
        importButton.setOnClickListener(v -> importLauncher.launch(new String[] {
                "application/zip", "application/x-zip-compressed", "application/octet-stream"
        }));
        refreshButton.setOnClickListener(v -> refreshPacks());
        selectType(PackArchiveInspector.RESOURCE_PACK);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) refreshPacks();
    }

    private void selectType(int type) {
        selectedType = type;
        if (getView() != null) {
            Button resource = getView().findViewById(R.id.pack_manager_resource_tab);
            Button shaders = getView().findViewById(R.id.pack_manager_shader_tab);
            resource.setSelected(type == PackArchiveInspector.RESOURCE_PACK);
            shaders.setSelected(type == PackArchiveInspector.SHADER_PACK);
            helpText.setText(type == PackArchiveInspector.RESOURCE_PACK
                    ? R.string.novacraft_resourcepacks_help : R.string.novacraft_shaderpacks_help);
        }
        refreshPacks();
    }

    private File getGameDirectory() {
        Tools.initStorageConstants(requireContext());
        LauncherProfiles.load();
        MinecraftProfile selected = LauncherProfiles.getCurrentProfile();
        return selected == null ? new File(Tools.DIR_GAME_NEW) : Tools.getGameDirPath(selected);
    }

    private File getPackFolder() {
        return new File(getGameDirectory(),
                selectedType == PackArchiveInspector.RESOURCE_PACK ? "resourcepacks" : "shaderpacks");
    }

    private void refreshPacks() {
        if (!isAdded() || adapter == null || getContext() == null) return;
        final long token = ++scanToken;
        final int type = selectedType;
        File folder;
        try {
            folder = getPackFolder();
            FileUtils.ensureDirectory(folder);
        } catch (Exception error) {
            Toast.makeText(requireContext(), R.string.novacraft_pack_folder_error, Toast.LENGTH_LONG).show();
            return;
        }
        activeFolder = folder;
        String profileName = getString(R.string.unnamed);
        try {
            MinecraftProfile selected = LauncherProfiles.getCurrentProfile();
            if (selected != null && selected.name != null && !selected.name.trim().isEmpty()) profileName = selected.name;
        } catch (Exception ignored) {}
        profileText.setText(getString(R.string.novacraft_pack_profile, profileName));
        folderText.setText(folder.getAbsolutePath());

        new Thread(() -> {
            List<PackArchiveInspector.PackEntry> entries = PackArchiveInspector.scan(folder, type);
            int available = 0;
            int hidden = 0;
            for (PackArchiveInspector.PackEntry entry : entries) {
                if (entry.available) available++;
                else hidden++;
            }
            final int availableCount = available;
            final int hiddenCount = hidden;
            mainHandler.post(() -> {
                if (token != scanToken || getView() == null) return;
                adapter.setEntries(entries);
                summaryText.setText(getString(R.string.novacraft_pack_summary, availableCount, hiddenCount));
            });
        }, "NovaCraft-Pack-Scanner").start();
    }

    private void onImportPicked(@Nullable Uri uri) {
        if (uri == null || getContext() == null || activeFolder == null) return;
        final int type = selectedType;
        final File destinationFolder = activeFolder;
        final ContentResolver resolver = requireContext().getContentResolver();
        new Thread(() -> {
            File temporary = null;
            try {
                String displayName = getDisplayName(resolver, uri);
                if (displayName == null || !displayName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    throw new java.io.IOException("Choose a .zip pack archive.");
                }
                String safeName = displayName.replaceAll("[^A-Za-z0-9._-]", "_").replace("..", "_");
                if (safeName.trim().isEmpty()) throw new java.io.IOException("Invalid pack filename.");
                File destination = uniqueDestination(destinationFolder, safeName);
                temporary = new File(destinationFolder, destination.getName() + ".importing.zip");
                long total = 0;
                try (InputStream input = resolver.openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(temporary)) {
                    if (input == null) throw new java.io.IOException("Unable to open selected archive.");
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_IMPORT_BYTES) throw new java.io.IOException("Pack archive exceeds the 250 MB import limit.");
                        output.write(buffer, 0, read);
                    }
                }
                PackArchiveInspector.Info validation = PackArchiveInspector.inspect(temporary, type);
                if (!validation.valid) throw new java.io.IOException(validation.validationMessage == null
                        ? "This archive is not a valid pack for the selected category." : validation.validationMessage);
                if (!temporary.renameTo(destination)) throw new java.io.IOException("Unable to move pack into the active profile folder.");
                mainHandler.post(() -> {
                    if (getContext() != null) {
                        Toast.makeText(requireContext(), R.string.novacraft_pack_import_success, Toast.LENGTH_LONG).show();
                        refreshPacks();
                    }
                });
            } catch (Exception error) {
                if (temporary != null) temporary.delete();
                String message = error.getMessage() == null ? error.toString() : error.getMessage();
                mainHandler.post(() -> {
                    if (getContext() != null) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                });
            }
        }, "NovaCraft-Pack-Import").start();
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

    private File uniqueDestination(File folder, String name) {
        File desired = new File(folder, name);
        if (!desired.exists()) return desired;
        String lower = name.toLowerCase(Locale.ROOT);
        boolean zip = lower.endsWith(".zip") || lower.endsWith(".zip.disabled");
        String extension = lower.endsWith(".zip.disabled") ? ".zip.disabled" : (zip ? ".zip" : "");
        String stem = extension.isEmpty() ? name : name.substring(0, name.length() - extension.length());
        int suffix = 2;
        File candidate;
        do {
            candidate = new File(folder, stem + "-" + suffix + extension);
            suffix++;
        } while (candidate.exists());
        return candidate;
    }

    @Override
    public void onToggle(PackArchiveInspector.PackEntry entry) {
        try {
            File source = entry.file.getCanonicalFile();
            File base = activeFolder.getCanonicalFile();
            File disabledFolder = new File(activeFolder, DISABLED_DIRECTORY).getCanonicalFile();
            File sourceParent = source.getParentFile();
            if (!base.equals(sourceParent) && !disabledFolder.equals(sourceParent)) {
                throw new SecurityException("Pack is outside the active profile folder.");
            }

            File destination;
            if (entry.directory) {
                if (entry.available) {
                    if (!disabledFolder.exists() && !disabledFolder.mkdirs()) {
                        throw new java.io.IOException("Unable to create disabled-pack storage.");
                    }
                    destination = uniqueDestination(disabledFolder, source.getName());
                } else {
                    destination = uniqueDestination(base, source.getName());
                }
            } else {
                String name = source.getName();
                boolean isDisabled = name.toLowerCase(Locale.ROOT).endsWith(".zip.disabled");
                String nextName = isDisabled ? name.substring(0, name.length() - ".disabled".length()) : name + ".disabled";
                destination = uniqueDestination(sourceParent, nextName);
            }
            if (!source.renameTo(destination)) throw new java.io.IOException("Android could not change this pack's availability.");
            refreshPacks();
        } catch (Exception error) {
            Toast.makeText(requireContext(), getString(R.string.novacraft_pack_file_action_failed, error.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRemove(PackArchiveInspector.PackEntry entry) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.novacraft_pack_remove_title)
                .setMessage(getString(R.string.novacraft_pack_remove_message, entry.info.title))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_remove, (dialog, which) -> {
                    try {
                        File source = entry.file.getCanonicalFile();
                        File base = activeFolder.getCanonicalFile();
                        File disabledFolder = new File(activeFolder, DISABLED_DIRECTORY).getCanonicalFile();
                        File parent = source.getParentFile();
                        if (!base.equals(parent) && !disabledFolder.equals(parent)) {
                            throw new SecurityException("Pack is outside the active profile folder.");
                        }
                        if (!deleteRecursively(source)) throw new java.io.IOException("Unable to remove the selected pack.");
                        refreshPacks();
                    } catch (Exception error) {
                        Toast.makeText(requireContext(), getString(R.string.novacraft_pack_file_action_failed, error.getMessage()),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) if (!deleteRecursively(child)) return false;
            }
        }
        return file.delete();
    }
}
