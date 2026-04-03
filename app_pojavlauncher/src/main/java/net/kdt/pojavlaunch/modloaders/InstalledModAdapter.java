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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class InstalledModAdapter extends RecyclerView.Adapter<InstalledModAdapter.ModViewHolder> {

    private static final String TAG = "ModAdapter";

    public interface EmptyStateListener {
        void onEmptyStateChanged(boolean isEmpty);
    }

    private final List<ModEntry> mMods = new ArrayList<>();
    private final EmptyStateListener mEmptyListener;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

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
        // Clear the tag so any in-flight load doesn't update this recycled view
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
        final ImageView icon;
        final TextView name, version;
        final SwitchCompat toggle;
        final ImageButton delete;

        ModViewHolder(@NonNull View itemView) {
            super(itemView);
            icon    = itemView.findViewById(R.id.installed_mod_icon);
            name    = itemView.findViewById(R.id.installed_mod_name);
            version = itemView.findViewById(R.id.installed_mod_version);
            toggle  = itemView.findViewById(R.id.installed_mod_toggle);
            delete  = itemView.findViewById(R.id.installed_mod_delete);
        }

        void bind(ModEntry entry) {
            name.setText(entry.displayName());
            version.setText(entry.file.getName());

            // Tag the ImageView with the file path so we can verify it hasn't been recycled
            icon.setTag(entry.file.getAbsolutePath());
            icon.setImageResource(R.drawable.ic_add_modded);

            final String expectedTag = entry.file.getAbsolutePath();
            final WeakReference<ImageView> iconRef = new WeakReference<>(icon);
            final File jarFile = entry.file;

            PojavApplication.sExecutorService.execute(() -> {
                Bitmap bmp = extractModIcon(jarFile);
                if (bmp == null) return;
                mMainHandler.post(() -> {
                    ImageView iv = iconRef.get();
                    // Only update if the view still belongs to the same mod
                    if (iv != null && expectedTag.equals(iv.getTag())) {
                        iv.setImageBitmap(bmp);
                    }
                });
            });

            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(entry.enabled);
            toggle.setOnCheckedChangeListener((btn, checked) -> entry.setEnabled(checked));

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
        File file;
        boolean enabled;

        ModEntry(File f) {
            this.file = f;
            this.enabled = !f.getName().endsWith(".disabled");
        }

        String displayName() {
            String n = file.getName();
            if (n.endsWith(".jar.disabled")) n = n.substring(0, n.length() - 13);
            else if (n.endsWith(".jar"))      n = n.substring(0, n.length() - 4);
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

    // ── Icon extraction ───────────────────────────────────────────────────

    @Nullable
    private static Bitmap extractModIcon(File jarFile) {
        try (ZipFile zip = new ZipFile(jarFile)) {
            String iconPath = resolveIconPath(zip);
            Log.d(TAG, jarFile.getName() + " → icon path: " + iconPath);

            if (iconPath != null) {
                Bitmap bmp = loadEntryAsBitmap(zip, iconPath);
                if (bmp != null) return bmp;
                Log.w(TAG, "Icon path resolved but bitmap failed: " + iconPath);
            }

            // Fallback scan — some old mods don't declare icon in metadata
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
        // 1. Fabric — fabric.mod.json → "icon" (string OR {"64":"path"} object)
        String content = readEntry(zip, "fabric.mod.json");
        if (content != null) {
            try {
                JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                if (obj.has("icon")) {
                    JsonElement iconEl = obj.get("icon");
                    if (iconEl.isJsonPrimitive()) {
                        return iconEl.getAsString();
                    } else if (iconEl.isJsonObject()) {
                        // Size map e.g. {"64": "path64.png", "128": "path128.png"}
                        // Pick the largest available
                        JsonObject sizeMap = iconEl.getAsJsonObject();
                        String best = null;
                        int bestSize = 0;
                        for (String key : sizeMap.keySet()) {
                            try {
                                int sz = Integer.parseInt(key);
                                if (sz > bestSize) {
                                    bestSize = sz;
                                    best = sizeMap.get(key).getAsString();
                                }
                            } catch (NumberFormatException ignored) {
                                best = sizeMap.get(key).getAsString();
                            }
                        }
                        if (best != null) return best;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "fabric.mod.json parse error: " + e.getMessage());
            }
        }

        // 2. Quilt — quilt.mod.json → quilt_loader.metadata.icon
        content = readEntry(zip, "quilt.mod.json");
        if (content != null) {
            try {
                JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                JsonObject ql = root.has("quilt_loader") ?
                        root.getAsJsonObject("quilt_loader") : null;
                if (ql != null && ql.has("metadata")) {
                    JsonObject meta = ql.getAsJsonObject("metadata");
                    if (meta.has("icon") && meta.get("icon").isJsonPrimitive())
                        return meta.get("icon").getAsString();
                }
            } catch (Exception e) {
                Log.w(TAG, "quilt.mod.json parse error: " + e.getMessage());
            }
        }

        // 3. Forge legacy — mcmod.info → logoFile
        content = readEntry(zip, "mcmod.info");
        if (content != null) {
            try {
                // mcmod.info is a JSON array
                JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
                if (arr.size() > 0 && arr.get(0).isJsonObject()) {
                    JsonObject mod = arr.get(0).getAsJsonObject();
                    if (mod.has("logoFile")) {
                        String logo = mod.get("logoFile").getAsString();
                        if (!logo.isEmpty()) return logo;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "mcmod.info parse error: " + e.getMessage());
            }
        }

        // 4. Forge/NeoForge — TOML — logoFile = "path"
        for (String toml : new String[]{"META-INF/neoforge.mods.toml", "META-INF/mods.toml"}) {
            content = readEntry(zip, toml);
            if (content != null) {
                String logo = tomlStringField(content, "logoFile");
                if (logo != null && !logo.isEmpty()) return logo;
            }
        }

        return null;
    }

    // ── Low-level helpers ─────────────────────────────────────────────────

    /**
     * Load a ZipEntry as a Bitmap.
     * IMPORTANT: BitmapFactory.decodeStream needs mark/reset support.
     * ZipInputStream doesn't support it, so we buffer all bytes first.
     */
    @Nullable
    private static Bitmap loadEntryAsBitmap(ZipFile zip, String entryPath) {
        // ZipFile.getEntry is case-sensitive — try exact then case-insensitive scan
        ZipEntry entry = zip.getEntry(entryPath);
        if (entry == null) {
            // Case-insensitive fallback
            String lower = entryPath.toLowerCase();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.getName().toLowerCase().equals(lower)) {
                    entry = e;
                    break;
                }
            }
        }
        if (entry == null) return null;

        try (InputStream is = zip.getInputStream(entry)) {
            // Buffer into byte array — BitmapFactory needs mark/reset
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) baos.write(buf, 0, read);
            byte[] bytes = baos.toByteArray();
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            Log.w(TAG, "loadEntryAsBitmap failed for " + entryPath + ": " + e.getMessage());
            return null;
        }
    }

    @Nullable
    private static String readEntry(ZipFile zip, String entryPath) {
        ZipEntry entry = zip.getEntry(entryPath);
        if (entry == null) return null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(zip.getInputStream(entry), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String tomlStringField(String toml, String field) {
        for (String line : toml.split("\n")) {
            line = line.trim();
            if (line.startsWith(field + " ") || line.startsWith(field + "=")) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String val = line.substring(eq + 1).trim();
                if (val.startsWith("\"") && val.endsWith("\""))
                    val = val.substring(1, val.length() - 1);
                if (!val.isEmpty()) return val;
            }
        }
        return null;
    }
}
