package net.kdt.pojavlaunch.modloaders;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Adapter for the installed mods list (fragment_manage_mods).
 * Enable/disable works by renaming .jar <-> .jar.disabled
 * Delete removes the file permanently after confirmation.
 */
public class InstalledModAdapter extends RecyclerView.Adapter<InstalledModAdapter.ModViewHolder> {

    public interface EmptyStateListener {
        void onEmptyStateChanged(boolean isEmpty);
    }

    private final List<ModEntry> mMods = new ArrayList<>();
    private final EmptyStateListener mEmptyListener;

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
        ModEntry entry = mMods.get(position);
        holder.bind(entry, position);
    }

    @Override
    public int getItemCount() {
        return mMods.size();
    }

    private void notifyEmptyState() {
        if (mEmptyListener != null) mEmptyListener.onEmptyStateChanged(mMods.isEmpty());
    }

    // ── ViewHolder ─────────────────────────────────────────────────────────

    class ModViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name, version, description;
        final SwitchCompat toggle;
        final ImageButton delete;

        ModViewHolder(@NonNull View itemView) {
            super(itemView);
            icon        = itemView.findViewById(R.id.installed_mod_icon);
            name        = itemView.findViewById(R.id.installed_mod_name);
            version     = itemView.findViewById(R.id.installed_mod_description); // reuse description row for version info
            description = itemView.findViewById(R.id.installed_mod_version);
            toggle      = itemView.findViewById(R.id.installed_mod_toggle);
            delete      = itemView.findViewById(R.id.installed_mod_delete);
        }

        void bind(ModEntry entry, int position) {
            name.setText(entry.displayName());
            version.setText(entry.file.getName());
            description.setText("");

            // Toggle state — suppress listener while setting
            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(entry.enabled);
            toggle.setOnCheckedChangeListener((btn, isChecked) -> {
                entry.setEnabled(isChecked);
            });

            // Delete with confirmation
            delete.setOnClickListener(v -> {
                Context ctx = v.getContext();
                new AlertDialog.Builder(ctx)
                        .setTitle(ctx.getString(R.string.manage_mods_delete_confirm, entry.displayName()))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (d, i) -> {
                            entry.file.delete();
                            int pos = getAdapterPosition();
                            if (pos != RecyclerView.NO_ID) {
                                mMods.remove(pos);
                                notifyItemRemoved(pos);
                                notifyEmptyState();
                            }
                        })
                        .show();
            });
        }
    }

    // ── ModEntry ───────────────────────────────────────────────────────────

    static class ModEntry {
        File file;
        boolean enabled;

        ModEntry(File f) {
            this.file = f;
            this.enabled = !f.getName().endsWith(".disabled");
        }

        /** Human-readable name: strip .jar / .jar.disabled suffix */
        String displayName() {
            String n = file.getName();
            if (n.endsWith(".jar.disabled")) n = n.substring(0, n.length() - 13);
            else if (n.endsWith(".jar"))      n = n.substring(0, n.length() - 4);
            return n;
        }

        /** Rename file to toggle enabled state */
        void setEnabled(boolean enable) {
            if (enable == this.enabled) return;
            File target;
            if (enable) {
                // Remove .disabled suffix
                String newName = file.getName().replace(".jar.disabled", ".jar");
                target = new File(file.getParent(), newName);
            } else {
                target = new File(file.getParent(), file.getName() + ".disabled");
            }
            if (file.renameTo(target)) {
                file = target;
                this.enabled = enable;
            }
        }
    }
}
