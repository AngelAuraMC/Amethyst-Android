package net.kdt.pojavlaunch.prefs.screens;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ApiHandler;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * In-app CurseForge Worlds browser. Uses the official CurseForge Core API,
 * checks map distribution permission, authenticates CDN downloads, verifies SHA-1 when supplied,
 * and stages validated ZIP worlds before moving them into the selected installation's saves folder.
 */
public class LauncherPreferenceCurseForgeMapsFragment extends LauncherPreferenceFragment {
    private static final int GAME_ID_MINECRAFT = 432;
    private static final int CLASS_ID_WORLDS = 17;
    private static final int PAGE_SIZE = 24;
    private static final int MAX_ENTRIES = 10000;
    private static final long MAX_DOWNLOAD_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_EXTRACTED_BYTES = 512L * 1024L * 1024L;

    private PreferenceCategory mResults;
    private Preference mMorePreference;
    private Preference mProfilePreference;
    private String mSearchText = "";
    private int mIndex = 0;
    private int mTotalCount = 0;
    private boolean mLoading = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_curseforge_maps);
        mResults = requirePreference("curseforge_maps_results", PreferenceCategory.class);
        mMorePreference = requirePreference("curseforge_maps_more");
        mProfilePreference = requirePreference("curseforge_maps_profile");

        requirePreference("curseforge_maps_search").setOnPreferenceClickListener(preference -> {
            showSearchDialog();
            return true;
        });
        mMorePreference.setOnPreferenceClickListener(preference -> {
            if (!mLoading && mIndex < mTotalCount) searchMaps(false);
            return true;
        });

        String version = getSelectedMinecraftVersion();
        mProfilePreference.setSummary(getString(R.string.novacraft_curseforge_maps_current_version,
                TextUtils.isEmpty(version) ? getString(R.string.novacraft_curseforge_maps_version_unknown) : version));

        if (!hasCurseForgeKey()) {
            addResultStatus(R.string.novacraft_curseforge_maps_key_missing);
            mMorePreference.setVisible(false);
            return;
        }
        searchMaps(true);
    }

    private String getCurseForgeKey() {
        try {
            return getString(R.string.curseforge_api_key).trim();
        } catch (Exception ignored) {
            return "DUMMY";
        }
    }

    private boolean hasCurseForgeKey() {
        String key = getCurseForgeKey();
        return !TextUtils.isEmpty(key) && !"DUMMY".equalsIgnoreCase(key);
    }

    private ApiHandler api() {
        return new ApiHandler("https://api.curseforge.com/v1", getCurseForgeKey());
    }

    private String getSelectedMinecraftVersion() {
        try {
            Tools.initStorageConstants(requireContext());
            LauncherProfiles.load();
            MinecraftProfile profile = LauncherProfiles.getCurrentProfile();
            String version = profile == null ? null : profile.lastVersionId;
            if (version == null || "latest-release".equals(version) || "latest-snapshot".equals(version)) return "";
            if (version.matches("^\\d+\\.\\d+(\\.\\d+)?$")) return version;
        } catch (Exception ignored) {
        }
        return "";
    }

    private File getSavesDirectory() {
        Tools.initStorageConstants(requireContext());
        LauncherProfiles.load();
        MinecraftProfile profile = LauncherProfiles.getCurrentProfile();
        File gameDirectory = profile == null ? new File(Tools.DIR_GAME_NEW) : Tools.getGameDirPath(profile);
        return new File(gameDirectory, "saves");
    }

    private void showSearchDialog() {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint(R.string.novacraft_curseforge_maps_search);
        input.setText(mSearchText);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.novacraft_curseforge_maps_search)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.search_go, (dialog, which) -> {
                    mSearchText = input.getText().toString().trim();
                    mIndex = 0;
                    searchMaps(true);
                })
                .show();
    }

    private void searchMaps(boolean reset) {
        if (!isAdded() || mLoading || !hasCurseForgeKey()) return;
        if (reset) {
            mIndex = 0;
            mTotalCount = 0;
            mResults.removeAll();
        }
        mLoading = true;
        mMorePreference.setVisible(false);
        if (reset) addResultStatus(R.string.novacraft_curseforge_maps_loading);
        Activity host = getActivity();
        if (host == null) {
            mLoading = false;
            return;
        }
        final int requestedIndex = mIndex;
        final String queryText = mSearchText;

        PojavApplication.sExecutorService.execute(() -> {
            ArrayList<MapProject> projects = new ArrayList<>();
            int total = 0;
            Exception failure = null;
            try {
                HashMap<String, Object> params = new HashMap<>();
                params.put("gameId", GAME_ID_MINECRAFT);
                params.put("classId", CLASS_ID_WORLDS);
                params.put("sortField", 2);
                params.put("sortOrder", "desc");
                params.put("index", requestedIndex);
                params.put("pageSize", PAGE_SIZE);
                if (!TextUtils.isEmpty(queryText)) params.put("searchFilter", queryText);
                String gameVersion = getSelectedMinecraftVersion();
                if (!TextUtils.isEmpty(gameVersion)) params.put("gameVersion", gameVersion);

                JsonObject response = api().get("mods/search", params, JsonObject.class);
                if (response == null || response.getAsJsonArray("data") == null) {
                    throw new IOException("CurseForge returned an empty response");
                }
                JsonArray data = response.getAsJsonArray("data");
                JsonObject pagination = response.getAsJsonObject("pagination");
                total = pagination == null ? requestedIndex + data.size()
                        : pagination.get("totalCount").getAsInt();

                for (JsonElement element : data) {
                    JsonObject obj = element.getAsJsonObject();
                    JsonElement allowed = obj.get("allowModDistribution");
                    if (allowed != null && !allowed.isJsonNull() && !allowed.getAsBoolean()) continue;

                    MapProject project = new MapProject();
                    project.id = obj.get("id").getAsLong();
                    project.name = safeString(obj, "name", "Unnamed map");
                    project.summary = safeString(obj, "summary", "");
                    project.slug = safeString(obj, "slug", "");
                    project.downloads = obj.has("downloadCount") && !obj.get("downloadCount").isJsonNull()
                            ? obj.get("downloadCount").getAsLong() : 0L;
                    projects.add(project);
                }
            } catch (Exception error) {
                failure = error;
            }

            final ArrayList<MapProject> result = projects;
            final int resultTotal = total;
            final Exception error = failure;
            host.runOnUiThread(() -> {
                if (!isAdded()) return;
                mLoading = false;
                if (reset) mResults.removeAll();
                if (error != null) {
                    if (mResults.getPreferenceCount() == 0) addResultStatus(R.string.novacraft_curseforge_maps_error);
                    mMorePreference.setVisible(false);
                    return;
                }
                if (result.isEmpty() && reset) addResultStatus(R.string.novacraft_curseforge_maps_empty);
                for (MapProject project : result) addMapPreference(project);
                mIndex = requestedIndex + PAGE_SIZE;
                mTotalCount = resultTotal;
                mMorePreference.setVisible(mIndex < mTotalCount && !result.isEmpty());
            });
        });
    }

    private void addMapPreference(MapProject project) {
        Preference item = new Preference(requireContext());
        item.setTitle(project.name);
        String summary = project.summary;
        if (project.downloads > 0) {
            String count = formatDownloads(project.downloads);
            summary = summary.isEmpty() ? getString(R.string.novacraft_curseforge_maps_download_count, count)
                    : summary + "\\n" + getString(R.string.novacraft_curseforge_maps_download_count, count);
        }
        item.setSummary(summary);
        item.setIcon(R.drawable.ic_folder);
        item.setOnPreferenceClickListener(preference -> {
            loadProjectFiles(project);
            return true;
        });
        mResults.addPreference(item);
    }

    private String formatDownloads(long count) {
        if (count >= 1000000000L) return String.format(Locale.US, "%.1fB", count / 1000000000.0);
        if (count >= 1000000L) return String.format(Locale.US, "%.1fM", count / 1000000.0);
        if (count >= 1000L) return String.format(Locale.US, "%.1fK", count / 1000.0);
        return String.valueOf(count);
    }

    private void loadProjectFiles(MapProject project) {
        if (!isAdded() || mLoading) return;
        mLoading = true;
        Toast.makeText(requireContext(), R.string.novacraft_curseforge_maps_files_loading,
                Toast.LENGTH_SHORT).show();
        Activity host = getActivity();
        if (host == null) {
            mLoading = false;
            return;
        }
        PojavApplication.sExecutorService.execute(() -> {
            ArrayList<MapFile> files = new ArrayList<>();
            Exception failure = null;
            try {
                HashMap<String, Object> params = new HashMap<>();
                params.put("index", 0);
                params.put("pageSize", 50);
                JsonObject response = api().get("mods/" + project.id + "/files", params, JsonObject.class);
                JsonArray data = response == null ? null : response.getAsJsonArray("data");
                if (data == null) throw new IOException("CurseForge returned no file list");

                String selectedVersion = getSelectedMinecraftVersion();
                for (JsonElement element : data) {
                    JsonObject obj = element.getAsJsonObject();
                    JsonElement allowed = obj.get("isAvailable");
                    if (allowed != null && !allowed.isJsonNull() && !allowed.getAsBoolean()) continue;

                    MapFile file = new MapFile();
                    file.id = obj.get("id").getAsLong();
                    file.displayName = safeString(obj, "displayName", safeString(obj, "fileName", "Map file"));
                    file.fileName = safeString(obj, "fileName", "");
                    file.downloadUrl = safeString(obj, "downloadUrl", "");
                    file.length = obj.has("fileLength") && !obj.get("fileLength").isJsonNull()
                            ? obj.get("fileLength").getAsLong() : 0L;
                    file.sha1 = readSha1(obj);
                    file.gameVersions = readGameVersions(obj.getAsJsonArray("gameVersions"));
                    file.compatible = !TextUtils.isEmpty(selectedVersion)
                            && file.gameVersions.contains(selectedVersion);
                    files.add(file);
                }
                files.sort(Comparator.comparing((MapFile f) -> f.compatible).reversed());
            } catch (Exception error) {
                failure = error;
            }

            final ArrayList<MapFile> result = files;
            final Exception error = failure;
            host.runOnUiThread(() -> {
                if (!isAdded()) return;
                mLoading = false;
                if (error != null) {
                    Toast.makeText(host, R.string.novacraft_curseforge_maps_files_error,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (result.isEmpty()) {
                    showOfficialProjectFallback(project,
                            getString(R.string.novacraft_curseforge_maps_no_files));
                    return;
                }
                showFileChoices(project, result);
            });
        });
    }

    private void showFileChoices(MapProject project, ArrayList<MapFile> files) {
        int count = Math.min(files.size(), 12);
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) {
            MapFile file = files.get(i);
            String versionLabel = file.gameVersions.isEmpty()
                    ? getString(R.string.novacraft_curseforge_maps_version_unknown)
                    : TextUtils.join(", ", file.gameVersions);
            labels[i] = getString(R.string.novacraft_curseforge_maps_file_label,
                    file.displayName, versionLabel, String.format(Locale.US, "%.1f", file.length / (1024.0 * 1024.0)));
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(project.name)
                .setItems(labels, (dialog, which) -> confirmFileInstall(project, files.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.novacraft_curseforge_maps_open_project,
                        (dialog, which) -> openOfficialProject(project))
                .show();
    }

    private void confirmFileInstall(MapProject project, MapFile file) {
        String version = file.gameVersions.isEmpty()
                ? getString(R.string.novacraft_curseforge_maps_version_unknown)
                : TextUtils.join(", ", file.gameVersions);
        String selected = getSelectedMinecraftVersion();
        String message = getString(R.string.novacraft_curseforge_maps_download_message,
                project.name, file.displayName, version);
        if (!TextUtils.isEmpty(selected) && !file.compatible) {
            message += "\\n\\n" + getString(R.string.novacraft_curseforge_maps_version_warning, selected);
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.novacraft_curseforge_maps_download_title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_download, (dialog, which) -> downloadAndInstall(project, file))
                .show();
    }

    private void downloadAndInstall(MapProject project, MapFile file) {
        if (!isAdded()) return;
        Activity host = getActivity();
        if (host == null) return;
        Toast.makeText(host, R.string.novacraft_curseforge_maps_download_started,
                Toast.LENGTH_LONG).show();

        PojavApplication.sExecutorService.execute(() -> {
            String installedName = null;
            String failure = null;
            File archive = new File(host.getCacheDir(), "novacraft-curseforge-" + UUID.randomUUID() + ".zip");
            try {
                String url = resolveDownloadUrl(project.id, file);
                if (TextUtils.isEmpty(url)) {
                    throw new IOException("DOWNLOAD_NOT_AVAILABLE");
                }
                downloadCurseForgeFile(url, archive, getCurseForgeKey());
                if (!TextUtils.isEmpty(file.sha1) && !verifySha1(archive, file.sha1)) {
                    throw new IOException("HASH_MISMATCH");
                }
                installedName = installWorldArchive(archive, project.name, getSavesDirectory());
            } catch (Exception error) {
                failure = error.getMessage() == null ? "Unknown error" : error.getMessage();
            } finally {
                archive.delete();
            }

            final String successName = installedName;
            final String failureReason = failure;
            host.runOnUiThread(() -> {
                if (!isAdded()) return;
                if (successName != null) {
                    Toast.makeText(host, getString(R.string.novacraft_curseforge_maps_install_success,
                            successName), Toast.LENGTH_LONG).show();
                } else if ("DOWNLOAD_NOT_AVAILABLE".equals(failureReason)) {
                    showOfficialProjectFallback(project,
                            getString(R.string.novacraft_curseforge_maps_no_distribution));
                } else if ("HASH_MISMATCH".equals(failureReason)) {
                    Toast.makeText(host, R.string.novacraft_curseforge_maps_hash_failed,
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(host, getString(R.string.novacraft_curseforge_maps_install_failed,
                            failureReason == null ? "Unknown error" : failureReason),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private String resolveDownloadUrl(long projectId, MapFile file) throws IOException {
        if (!TextUtils.isEmpty(file.downloadUrl)) return file.downloadUrl;
        JsonObject response = api().get("mods/" + projectId + "/files/" + file.id + "/download-url",
                JsonObject.class);
        if (response == null) return null;
        JsonElement data = response.get("data");
        return data == null || data.isJsonNull() ? null : data.getAsString();
    }

    private static void downloadCurseForgeFile(String downloadUrl, File destination, String apiKey)
            throws IOException {
        URL current = new URL(downloadUrl);
        byte[] buffer = new byte[32768];
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!"https".equalsIgnoreCase(current.getProtocol())
                    || current.getHost() == null
                    || !current.getHost().toLowerCase(Locale.ROOT).endsWith(".forgecdn.net")) {
                throw new IOException("CurseForge returned an untrusted download host");
            }
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", "NovaCraft Android Launcher");
            connection.setRequestProperty("x-api-key", apiKey);
            try {
                int status = connection.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_PERM
                        || status == HttpURLConnection.HTTP_MOVED_TEMP
                        || status == HttpURLConnection.HTTP_SEE_OTHER
                        || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (TextUtils.isEmpty(location)) throw new IOException("Missing download redirect");
                    current = new URL(current, location);
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IOException("CurseForge CDN returned HTTP " + status);
                }
                long contentLength = connection.getContentLengthLong();
                if (contentLength > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("DOWNLOAD_SIZE_LIMIT");
                }
                FileUtils.ensureParentDirectory(destination);
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
                    long total = 0L;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_DOWNLOAD_BYTES) throw new IOException("DOWNLOAD_SIZE_LIMIT");
                        output.write(buffer, 0, read);
                    }
                }
                if (!destination.isFile() || destination.length() == 0) {
                    throw new IOException("CurseForge returned an empty file");
                }
                return;
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many CurseForge download redirects");
    }

    private static boolean verifySha1(File file, String expected) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream input = new BufferedInputStream(new java.io.FileInputStream(file))) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder actual = new StringBuilder();
        for (byte value : digest.digest()) actual.append(String.format(Locale.US, "%02x", value & 0xff));
        return actual.toString().equalsIgnoreCase(expected);
    }

    private String installWorldArchive(File archive, String requestedName, File savesDirectory)
            throws Exception {
        FileUtils.ensureDirectory(savesDirectory);
        File staging = null;
        try (ZipFile zip = new ZipFile(archive)) {
            String levelDatPath = findLevelDat(zip);
            int lastSlash = levelDatPath.lastIndexOf('/');
            String rootPrefix = lastSlash < 0 ? "" : levelDatPath.substring(0, lastSlash + 1);
            staging = new File(savesDirectory, ".novacraft-cf-" + UUID.randomUUID());
            if (!staging.mkdir()) throw new IOException("Could not create temporary map folder");

            Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buffer = new byte[32768];
            long totalBytes = 0L;
            int count = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ENTRIES) throw new IOException("Map archive has too many entries");
                String normalized = normalizeZipPath(entry.getName());
                if (entry.isDirectory() || !normalized.startsWith(rootPrefix)) continue;
                String relative = normalized.substring(rootPrefix.length());
                if (relative.isEmpty()) continue;

                File output = new File(staging, relative);
                String stagingRoot = staging.getCanonicalPath() + File.separator;
                if (!output.getCanonicalPath().startsWith(stagingRoot)) {
                    throw new IOException("UNSAFE_MAP_ARCHIVE");
                }
                if (output.exists()) throw new IOException("Map archive has duplicate file paths");
                FileUtils.ensureParentDirectory(output);
                try (InputStream input = new BufferedInputStream(zip.getInputStream(entry));
                     OutputStream target = new BufferedOutputStream(new FileOutputStream(output))) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        totalBytes += read;
                        if (totalBytes > MAX_EXTRACTED_BYTES) throw new IOException("EXTRACT_SIZE_LIMIT");
                        target.write(buffer, 0, read);
                    }
                }
            }

            File levelDat = new File(staging, "level.dat");
            if (!levelDat.isFile() || levelDat.length() == 0) {
                throw new IOException("INVALID_MAP_ARCHIVE");
            }
            String baseName = sanitizeMapName(requestedName);
            if (baseName.isEmpty()) baseName = "CurseForge Map";
            String finalName = findAvailableWorldName(savesDirectory, baseName);
            File destination = new File(savesDirectory, finalName);
            if (!staging.renameTo(destination)) throw new IOException("Could not move map into saves folder");
            staging = null;
            return finalName;
        } finally {
            if (staging != null) deleteRecursively(staging);
        }
    }

    private String findLevelDat(ZipFile zip) throws IOException {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        String found = null;
        int count = 0;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            String path = normalizeZipPath(entry.getName());
            if ("level.dat".equals(path) || path.endsWith("/level.dat")) {
                found = path;
                if (++count > 1) throw new IOException("INVALID_MAP_ARCHIVE");
            }
        }
        if (found == null) throw new IOException("INVALID_MAP_ARCHIVE");
        return found;
    }

    private String normalizeZipPath(String raw) throws IOException {
        String path = raw.replace('\\\\', '/');
        if (path.startsWith("/") || path.matches("(?i)^[a-z]:.*")) {
            throw new IOException("UNSAFE_MAP_ARCHIVE");
        }
        StringBuilder normalized = new StringBuilder();
        for (String part : path.split("/")) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) throw new IOException("UNSAFE_MAP_ARCHIVE");
            if (normalized.length() > 0) normalized.append('/');
            normalized.append(part);
        }
        return normalized.toString();
    }

    private String sanitizeMapName(String name) {
        if (name == null) return "";
        StringBuilder result = new StringBuilder();
        String value = name.trim();
        for (int i = 0; i < value.length() && result.length() < 80; i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-' || c == '.') {
                result.append(c);
            }
        }
        String safe = result.toString().trim();
        while (safe.startsWith(".")) safe = safe.substring(1);
        return ".".equals(safe) || "..".equals(safe) ? "" : safe;
    }

    private String findAvailableWorldName(File saves, String requested) {
        String candidate = requested;
        int suffix = 2;
        while (new File(saves, candidate).exists()) {
            String tail = " (" + suffix++ + ")";
            int maxBase = Math.max(1, 80 - tail.length());
            String base = requested.length() > maxBase ? requested.substring(0, maxBase) : requested;
            candidate = base + tail;
        }
        return candidate;
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

    private String readSha1(JsonObject file) {
        JsonArray hashes = file.getAsJsonArray("hashes");
        if (hashes == null) return null;
        for (JsonElement element : hashes) {
            JsonObject hash = element.getAsJsonObject();
            if (hash.has("algo") && hash.get("algo").getAsInt() == 1
                    && hash.has("value") && !hash.get("value").isJsonNull()) {
                return hash.get("value").getAsString();
            }
        }
        return null;
    }

    private ArrayList<String> readGameVersions(JsonArray values) {
        ArrayList<String> versions = new ArrayList<>();
        if (values == null) return versions;
        for (JsonElement element : values) {
            String value = element.getAsString();
            if (value.matches("^\\d+\\.\\d+(\\.\\d+)?$") && !versions.contains(value)) {
                versions.add(value);
            }
        }
        return versions;
    }

    private String safeString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void addResultStatus(int textId) {
        Preference status = new Preference(requireContext());
        status.setTitle(textId);
        status.setSelectable(false);
        status.setPersistent(false);
        mResults.addPreference(status);
    }

    private void showOfficialProjectFallback(MapProject project, String reason) {
        new AlertDialog.Builder(requireContext())
                .setTitle(project.name)
                .setMessage(reason)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.novacraft_curseforge_maps_open_project,
                        (dialog, which) -> openOfficialProject(project))
                .show();
    }

    private void openOfficialProject(MapProject project) {
        try {
            String slug = !TextUtils.isEmpty(project.slug) ? project.slug : String.valueOf(project.id);
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.curseforge.com/minecraft/worlds/" + Uri.encode(slug))));
        } catch (Exception error) {
            Toast.makeText(requireContext(), R.string.novacraft_curseforge_maps_error,
                    Toast.LENGTH_LONG).show();
        }
    }

    private static class MapProject {
        long id;
        long downloads;
        String name;
        String summary;
        String slug;
    }

    private static class MapFile {
        long id;
        long length;
        String displayName;
        String fileName;
        String downloadUrl;
        String sha1;
        ArrayList<String> gameVersions = new ArrayList<>();
        boolean compatible;
    }
}
