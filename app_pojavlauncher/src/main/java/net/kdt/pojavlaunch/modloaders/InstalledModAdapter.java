package net.kdt.pojavlaunch.modloaders;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
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
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Adapter for the installed mods list.
 * Enable/disable: rename .jar <-> .jar.disabled
 * Icons: reads the real icon path from mod metadata (fabric.mod.json, mods.toml, etc.)
 * Delete: removes file with confirmation
 */
public class InstalledModAdapter extends RecyclerView.Adapter<InstalledModAdapter.ModViewHolder> {

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
            icon.setImageResource(R.drawable.ic_add_modded); // placeholder

            // Async icon load
            int pos = getBindingAdapterPosition();
            PojavApplication.sExecutorService.execute(() -> {
                Bitmap bmp = extractModIcon(entry.file);
                mMainHandler.post(() -> {
                    if (getBindingAdapterPosition() == pos && bmp != null)
                        icon.setImageBitmap(bmp);
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

    /**
     * Reads the real icon path from mod metadata, then extracts it from the JAR.
     * Supports: Fabric, Quilt, Forge legacy, Forge/NeoForge (TOML).
     * Falls back to common filenames if metadata not found.
     */
    @Nullable
    private static Bitmap extractModIcon(File jarFile) {
        try (ZipFile zip = new ZipFile(jarFile)) {
            String iconPath = resolveIconPath(zip);
            if (iconPath != null) {
                Bitmap bmp = loadEntryAsBitmap(zip, iconPath);
                if (bmp != null) return bmp;
            }
            // Fallback — some old mods use these
            for (String fallback : new String[]{"pack.png", "icon.png", "logo.png"}) {
                Bitmap bmp = loadEntryAsBitmap(zip, fallback);
                if (bmp != null) return bmp;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Reads mod metadata files in priority order and returns the icon path. */
    @Nullable
    private static String resolveIconPath(ZipFile zip) {
        // 1. Fabric — fabric.mod.json → "icon"
        String path = readEntry(zip, "fabric.mod.json");
        if (path != null) {
            String icon = jsonStringField(path, "icon");
            if (icon != null) return icon;
        }

        // 2. Quilt — quilt.mod.json → quilt_loader.metadata.icon
        path = readEntry(zip, "quilt.mod.json");
        if (path != null) {
            try {
                JsonObject root = JsonParser.parseString(path).getAsJsonObject();
                if (root.has("quilt_loader")) {
                    JsonObject ql = root.getAsJsonObject("quilt_loader");
                    if (ql.has("metadata")) {
                        JsonObject meta = ql.getAsJsonObject("metadata");
                        String icon = jsonStringField(meta.toString(), "icon");
                        if (icon != null) return icon;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 3. Forge legacy — mcmod.info → logoFile (JSON array)
        path = readEntry(zip, "mcmod.info");
        if (path != null) {
            try {
                JsonArray arr = JsonParser.parseString(path).getAsJsonArray();
                if (arr.size() > 0) {
                    JsonElement el = arr.get(0);
                    if (el.isJsonObject()) {
                        String logo = jsonStringField(el.getAsJsonObject().toString(), "logoFile");
                        if (logo != null && !logo.isEmpty()) return logo;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4. Forge/NeoForge — META-INF/mods.toml or neoforge.mods.toml → logoFile
        for (String toml : new String[]{"META-INF/neoforge.mods.toml", "META-INF/mods.toml"}) {
            path = readEntry(zip, toml);
            if (path != null) {
                String logo = tomlStringField(path, "logoFile");
                if (logo != null && !logo.isEmpty()) return logo;
            }
        }

        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    @Nullable
    private static Bitmap loadEntryAsBitmap(ZipFile zip, String entryPath) {
        try {
            ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null) return null;
            try (InputStream is = zip.getInputStream(entry)) {
                return BitmapFactory.decodeStream(is);
            }
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String readEntry(ZipFile zip, String entryPath) {
        try {
            ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null) return null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(zip.getInputStream(entry)))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append('\n');
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Extract a string field from a JSON string using Gson. */
    @Nullable
    private static String jsonStringField(String json, String field) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has(field) && obj.get(field).isJsonPrimitive())
                return obj.get(field).getAsString();
        } catch (Exception ignored) {}
        return null;
    }

    /** Extract a string field from a TOML string (simple key = "value" parsing). */
    @Nullable
    private static String tomlStringField(String toml, String field) {
        for (String line : toml.split("\n")) {
            line = line.trim();
            if (line.startsWith(field)) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String val = line.substring(eq + 1).trim();
                // strip surrounding quotes
                if (val.startsWith("\"") && val.endsWith("\""))
                    val = val.substring(1, val.length() - 1);
                if (!val.isEmpty()) return val;
            }
        }
        return null;
    }
}
