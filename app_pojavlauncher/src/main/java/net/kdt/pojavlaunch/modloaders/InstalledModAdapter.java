package net.kdt.pojavlaunch.modloaders;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ApiHandler;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class InstalledModAdapter extends RecyclerView.Adapter<InstalledModAdapter.ModViewHolder> {

    private static final String TAG = "ModAdapter";
    private static final String MODRINTH_API = "https://api.modrinth.com/v2";

    public interface EmptyStateListener {
        void onEmptyStateChanged(boolean isEmpty);
    }

    private final List<ModEntry> mMods = new ArrayList<>();
    private final EmptyStateListener mEmptyListener;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // Icon cache — keyed by absolute file path. Avoids re-extracting from the jar
    // (and blanking the ImageView) every time the row is rebound, e.g. during an
    // update check which calls notifyItemChanged/notifyDataSetChanged.
    // A cached null Bitmap means "checked, no icon found" — falls back to the
    // default placeholder without retrying the zip read every time.
    private final java.util.Map<String, Bitmap> mIconCache = new java.util.HashMap<>();
    private final java.util.Set<String> mIconCheckedNoResult = new java.util.HashSet<>();

    // Per-instance filter — set by ManageModsFragment before triggering update check
    private String mFilterMcVersion = "";
    private String mFilterLoader    = "";

    public InstalledModAdapter(File modsDir, EmptyStateListener listener) {
        mEmptyListener = listener;
        if (modsDir != null && modsDir.isDirectory()) {
            File[] files = modsDir.listFiles(f -> f.isFile() &&
                    (f.getName().endsWith(".jar") || f.getName().endsWith(".jar.disabled")));
            if (files != null) {
                Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File f : files) mMods.add(new ModEntry(f));
            }
        }
        notifyEmptyState();
    }

    /** Called by ManageModsFragment to inject the saved per-instance filter. */
    public void setFilter(String mcVersion, String loader) {
        mFilterMcVersion = mcVersion != null ? mcVersion : "";
        mFilterLoader    = loader    != null ? loader    : "";
    }

    // ── Update checking ───────────────────────────────────────────────────

    /**
     * Kicks off a background update check for every mod in the list.
     * For each mod: SHA1 hash the jar → ask Modrinth /version_file/{hash} for the
     * project id → ask /project/{id}/version filtered by mcVersion+loader → compare
     * the latest version file name to the current filename. If different, set the
     * update URL on the entry and show the update button.
     *
     * @param onComplete called on main thread when all checks are done, with the
     *                   count of mods that have updates available.
     */
    public void checkForUpdates(Runnable onComplete) {
        // Clear any previous update state. Only notify rows that actually had
        // an update flag set — avoids a blanket notifyDataSetChanged() which
        // would rebind every visible row and (without the icon cache) cause
        // icons to flash. The icon cache makes this safe either way now, but
        // this is still cheaper.
        for (int i = 0; i < mMods.size(); i++) {
            ModEntry e = mMods.get(i);
            if (e.updateUrl != null || e.updateFileName != null) {
                e.updateUrl      = null;
                e.updateFileName = null;
                notifyItemChanged(i);
            }
        }

        if (mFilterMcVersion.isEmpty() && mFilterLoader.isEmpty()) {
            // No filter — can't meaningfully check; caller should warn the user
            mMainHandler.post(onComplete);
            return;
        }

        final int total = mMods.size();
        if (total == 0) {
            mMainHandler.post(onComplete);
            return;
        }

        final int[] done = {0};

        for (int i = 0; i < total; i++) {
            final int index = i;
            final ModEntry entry = mMods.get(i);

            PojavApplication.sExecutorService.execute(() -> {
                try {
                    checkUpdateForEntry(entry);
                } catch (Exception e) {
                    Log.w(TAG, "Update check failed for " + entry.displayName() + ": " + e.getMessage());
                } finally {
                    mMainHandler.post(() -> {
                        // Refresh only this item
                        if (index < mMods.size()) notifyItemChanged(index);
                        done[0]++;
                        if (done[0] >= total) onComplete.run();
                    });
                }
            });
        }
    }

    private void checkUpdateForEntry(ModEntry entry) throws Exception {
        // 1. SHA1 hash the jar file
        String sha1 = sha1Hex(entry.file);
        if (sha1 == null) return;

        ApiHandler api = new ApiHandler(MODRINTH_API);

        // 2. Look up which project+version this file belongs to
        //    GET /version_file/{hash}?algorithm=sha1
        java.util.HashMap<String, Object> hashParams = new java.util.HashMap<>();
        hashParams.put("algorithm", "sha1");
        JsonObject fileVersion = api.get("version_file/" + sha1, hashParams, JsonObject.class);
        if (fileVersion == null) return; // Not on Modrinth

        String projectId = fileVersion.has("project_id")
                ? fileVersion.get("project_id").getAsString() : null;
        if (projectId == null) return;

        // 3. Get all versions of the project filtered by our mc version + loader
        java.util.HashMap<String, Object> params = new java.util.HashMap<>();
        if (!mFilterMcVersion.isEmpty()) params.put("game_versions", "[\"" + mFilterMcVersion + "\"]");
        if (!mFilterLoader.isEmpty())    params.put("loaders",        "[\"" + mFilterLoader    + "\"]");

        JsonArray versions = api.get("project/" + projectId + "/version", params, JsonArray.class);
        if (versions == null || versions.size() == 0) return;

        // Modrinth returns newest first — index 0 is the latest
        JsonObject latest = versions.get(0).getAsJsonObject();

        // 4. Get the latest version's primary file name
        JsonArray files = latest.getAsJsonArray("files");
        if (files == null || files.size() == 0) return;

        // Find the primary file (primary=true) or fall back to first
        JsonObject primaryFile = null;
        for (int i = 0; i < files.size(); i++) {
            JsonObject f = files.get(i).getAsJsonObject();
            if (f.has("primary") && f.get("primary").getAsBoolean()) {
                primaryFile = f;
                break;
            }
        }
        if (primaryFile == null) primaryFile = files.get(0).getAsJsonObject();

        String latestUrl      = primaryFile.get("url").getAsString();
        String latestFileName = latestUrl.substring(latestUrl.lastIndexOf('/') + 1);
        if (latestFileName.contains("?")) latestFileName = latestFileName.substring(0, latestFileName.indexOf('?'));

        // 5. Compare — if the file name differs, an update is available
        String currentName = entry.file.getName();
        if (currentName.endsWith(".disabled")) currentName = currentName.replace(".disabled", "");

        if (!currentName.equalsIgnoreCase(latestFileName)) {
            entry.updateUrl      = latestUrl;
            entry.updateFileName = latestFileName;
        }
    }

    /** Downloads the update, replaces the existing jar, refreshes the entry. */
    private void applyUpdate(Context context, ModEntry entry, int position) {
        if (entry.updateUrl == null) return;

        String updateUrl  = entry.updateUrl;
        String updateName = entry.updateFileName;

        // Determine target file (keep disabled state)
        boolean wasDisabled = entry.file.getName().endsWith(".disabled");
        String  targetName  = wasDisabled ? updateName + ".disabled" : updateName;
        File    targetFile  = new File(entry.file.getParent(), targetName);

        Toast.makeText(context,
                context.getString(R.string.mod_updating, entry.displayName()),
                Toast.LENGTH_SHORT).show();

        PojavApplication.sExecutorService.execute(() -> {
            try {
                // Download to a temp file first so we never leave a half-written jar
                File tmpFile = new File(entry.file.getParent(), targetName + ".tmp");
                DownloadUtils.downloadFile(updateUrl, tmpFile);

                // Delete old file, rename temp to final
                entry.file.delete();
                tmpFile.renameTo(targetFile);

                mMainHandler.post(() -> {
                    entry.file        = targetFile;
                    entry.enabled     = !wasDisabled;
                    entry.updateUrl   = null;
                    entry.updateFileName = null;
                    if (position < mMods.size()) notifyItemChanged(position);
                    Toast.makeText(context,
                            context.getString(R.string.mod_update_done, entry.displayName()),
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Update download failed: " + e.getMessage());
                mMainHandler.post(() ->
                        Toast.makeText(context,
                                context.getString(R.string.mod_update_failed, entry.displayName()),
                                Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ── Adapter ───────────────────────────────────────────────────────────

    @NonNull
    @Override
    public ModViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_installed_mod, parent, false);
        return new ModViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ModViewHolder holder, int position) {
        holder.bind(mMods.get(position));
    }

    @Override
    public void onViewRecycled(@NonNull ModViewHolder holder) {
        holder.icon.setTag(null);
        holder.icon.setImageResource(R.drawable.ic_add_modded);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() { return mMods.size(); }

    private void notifyEmptyState() {
        if (mEmptyListener != null) mEmptyListener.onEmptyStateChanged(mMods.isEmpty());
    }

    // ── ViewHolder ────────────────────────────────────────────────────────

    class ModViewHolder extends RecyclerView.ViewHolder {
        final ImageView   icon;
        final TextView    name, version;
        final SwitchCompat toggle;
        final android.widget.Button update;
        final ImageButton delete;

        ModViewHolder(@NonNull View itemView) {
            super(itemView);
            icon   = itemView.findViewById(R.id.installed_mod_icon);
            name   = itemView.findViewById(R.id.installed_mod_name);
            version= itemView.findViewById(R.id.installed_mod_version);
            toggle = itemView.findViewById(R.id.installed_mod_toggle);
            update = itemView.findViewById(R.id.installed_mod_update);
            delete = itemView.findViewById(R.id.installed_mod_delete);
        }

        void bind(ModEntry entry) {
            name.setText(entry.displayName());
            version.setText(entry.file.getName());

            final String path = entry.file.getAbsolutePath();
            icon.setTag(path);

            // Cache hit — apply immediately, no flash, no re-read from disk
            Bitmap cached = mIconCache.get(path);
            if (cached != null) {
                icon.setImageBitmap(cached);
            } else if (mIconCheckedNoResult.contains(path)) {
                // Already determined this jar has no icon — use the fallback
                // and don't bother re-extracting on every rebind.
                icon.setImageResource(R.drawable.ic_add_modded);
            } else {
                // First time seeing this mod — show placeholder while we extract
                icon.setImageResource(R.drawable.ic_add_modded);

                final String expectedTag = path;
                final WeakReference<ImageView> iconRef = new WeakReference<>(icon);
                final File jarFile = entry.file;

                PojavApplication.sExecutorService.execute(() -> {
                    Bitmap bmp = extractModIcon(jarFile);
                    mMainHandler.post(() -> {
                        if (bmp != null) {
                            mIconCache.put(path, bmp);
                        } else {
                            // Forge mods, very old mods, or jars with no embedded
                            // icon at all — remember that so we stop retrying.
                            mIconCheckedNoResult.add(path);
                        }
                        ImageView iv = iconRef.get();
                        if (iv != null && expectedTag.equals(iv.getTag())) {
                            if (bmp != null) iv.setImageBitmap(bmp);
                            // else: already showing the placeholder, nothing to do
                        }
                    });
                });
            }

            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(entry.enabled);
            toggle.setOnCheckedChangeListener((btn, checked) -> entry.setEnabled(checked));

            // Update button — visible only when an update is available
            if (entry.updateUrl != null) {
                update.setVisibility(View.VISIBLE);
                update.setOnClickListener(v -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) applyUpdate(v.getContext(), entry, pos);
                });
            } else {
                update.setVisibility(View.GONE);
                update.setOnClickListener(null);
            }

            delete.setOnClickListener(v -> {
                Context ctx = v.getContext();
                new AlertDialog.Builder(ctx)
                        .setTitle(ctx.getString(R.string.manage_mods_delete_confirm, entry.displayName()))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (d, i) -> {
                            entry.file.delete();
                            int p = getBindingAdapterPosition();
                            if (p != RecyclerView.NO_POSITION) {
                                mMods.remove(p);
                                notifyItemRemoved(p);
                                notifyEmptyState();
                            }
                        })
                        .show();
            });
        }
    }

    // ── ModEntry ──────────────────────────────────────────────────────────

    static class ModEntry {
        File   file;
        boolean enabled;
        @Nullable String updateUrl;
        @Nullable String updateFileName;

        ModEntry(File f) {
            this.file    = f;
            this.enabled = !f.getName().endsWith(".disabled");
        }

        String displayName() {
            String n = file.getName();
            if (n.endsWith(".jar.disabled")) n = n.substring(0, n.length() - 13);
            else if (n.endsWith(".jar"))     n = n.substring(0, n.length() - 4);
            return n;
        }

        void setEnabled(boolean enable) {
            if (enable == this.enabled) return;
            File target = enable
                    ? new File(file.getParent(), file.getName().replace(".jar.disabled", ".jar"))
                    : new File(file.getParent(), file.getName() + ".disabled");
            if (file.renameTo(target)) {
                file = target;
                this.enabled = enable;
            }
        }
    }

    // ── SHA1 ──────────────────────────────────────────────────────────────

    @Nullable
    private static String sha1Hex(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[8192];
            try (FileInputStream fis = new FileInputStream(file)) {
                int read;
                while ((read = fis.read(buf)) != -1) md.update(buf, 0, read);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "SHA1 failed for " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    // ── Icon extraction ───────────────────────────────────────────────────

    @Nullable
    private static Bitmap extractModIcon(File jarFile) {
        try (ZipFile zip = new ZipFile(jarFile)) {
            String iconPath = resolveIconPath(zip);
            if (iconPath != null) {
                Bitmap bmp = loadEntryAsBitmap(zip, iconPath);
                if (bmp != null) return bmp;
            }
            for (String fallback : new String[]{"pack.png", "icon.png", "logo.png"}) {
                Bitmap bmp = loadEntryAsBitmap(zip, fallback);
                if (bmp != null) return bmp;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to open JAR: " + jarFile.getName() + " — " + e.getMessage());
        }
        return null;
    }

    @Nullable
    private static String resolveIconPath(ZipFile zip) {
        String content = readEntry(zip, "fabric.mod.json");
        if (content != null) {
            try {
                JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                if (obj.has("icon")) {
                    JsonElement iconEl = obj.get("icon");
                    if (iconEl.isJsonPrimitive()) return iconEl.getAsString();
                    if (iconEl.isJsonObject()) {
                        JsonObject sizeMap = iconEl.getAsJsonObject();
                        String best = null; int bestSize = 0;
                        for (String key : sizeMap.keySet()) {
                            try {
                                int sz = Integer.parseInt(key);
                                if (sz > bestSize) { bestSize = sz; best = sizeMap.get(key).getAsString(); }
                            } catch (NumberFormatException ignored) {
                                best = sizeMap.get(key).getAsString();
                            }
                        }
                        if (best != null) return best;
                    }
                }
            } catch (Exception ignored) {}
        }
        content = readEntry(zip, "quilt.mod.json");
        if (content != null) {
            try {
                JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                JsonObject ql = root.has("quilt_loader") ? root.getAsJsonObject("quilt_loader") : null;
                if (ql != null && ql.has("metadata")) {
                    JsonObject meta = ql.getAsJsonObject("metadata");
                    if (meta.has("icon") && meta.get("icon").isJsonPrimitive())
                        return meta.get("icon").getAsString();
                }
            } catch (Exception ignored) {}
        }
        content = readEntry(zip, "mcmod.info");
        if (content != null) {
            try {
                JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
                if (arr.size() > 0 && arr.get(0).isJsonObject()) {
                    JsonObject mod = arr.get(0).getAsJsonObject();
                    if (mod.has("logoFile")) { String logo = mod.get("logoFile").getAsString(); if (!logo.isEmpty()) return logo; }
                }
            } catch (Exception ignored) {}
        }
        for (String toml : new String[]{"META-INF/neoforge.mods.toml", "META-INF/mods.toml"}) {
            content = readEntry(zip, toml);
            if (content != null) {
                String logo = tomlStringField(content, "logoFile");
                if (logo != null && !logo.isEmpty()) return logo;
            }
        }
        return null;
    }

    @Nullable
    private static Bitmap loadEntryAsBitmap(ZipFile zip, String entryPath) {
        ZipEntry entry = zip.getEntry(entryPath);
        if (entry == null) {
            String lower = entryPath.toLowerCase();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.getName().toLowerCase().equals(lower)) { entry = e; break; }
            }
        }
        if (entry == null) return null;
        try (InputStream is = zip.getInputStream(entry)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int read;
            while ((read = is.read(buf)) != -1) baos.write(buf, 0, read);
            byte[] bytes = baos.toByteArray();
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) { return null; }
    }

    @Nullable
    private static String readEntry(ZipFile zip, String entryPath) {
        ZipEntry entry = zip.getEntry(entryPath);
        if (entry == null) return null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(zip.getInputStream(entry), "UTF-8"))) {
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    @Nullable
    private static String tomlStringField(String toml, String field) {
        for (String line : toml.split("\n")) {
            line = line.trim();
            if (line.startsWith(field + " ") || line.startsWith(field + "=")) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String val = line.substring(eq + 1).trim();
                if (val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length() - 1);
                if (!val.isEmpty()) return val;
            }
        }
        return null;
    }
}