package net.kdt.pojavlaunch.modloaders.modpacks;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ModManagerAdapter extends RecyclerView.Adapter<ModManagerAdapter.ModViewHolder> {
    public interface Listener {
        void onToggle(ModEntry entry);
        void onRemove(ModEntry entry);
    }

    private final Listener listener;
    private final List<ModEntry> entries = new ArrayList<>();

    public ModManagerAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setEntries(List<ModEntry> newEntries) {
        entries.clear();
        if (newEntries != null) entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ModViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_novacraft_mod, parent, false);
        return new ModViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ModViewHolder holder, int position) {
        ModEntry entry = entries.get(position);
        String title = entry.info == null ? null : entry.info.displayName;
        holder.name.setText(title == null || title.trim().isEmpty() ? entry.file.getName() : title);
        holder.details.setText(entry.info == null ? entry.file.getName() : entry.info.describe());
        if (entry.info != null && entry.info.hasLoaderMismatch(entry.activeLoader)) {
            holder.status.setText(holder.itemView.getContext().getString(
                    R.string.novacraft_mod_loader_mismatch_tag, entry.info.loader, entry.activeLoader));
        } else {
            holder.status.setText(entry.enabled ? R.string.novacraft_mod_enabled : R.string.novacraft_mod_disabled);
        }
        holder.toggle.setText(entry.enabled ? R.string.novacraft_mod_disable : R.string.novacraft_mod_enable);
        holder.toggle.setOnClickListener(v -> listener.onToggle(entry));
        holder.remove.setOnClickListener(v -> listener.onRemove(entry));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public static final class ModEntry {
        public final File file;
        public final ModMetadataReader.Info info;
        public final boolean enabled;
        public final String activeLoader;

        public ModEntry(File file, ModMetadataReader.Info info, boolean enabled, String activeLoader) {
            this.file = file;
            this.info = info;
            this.enabled = enabled;
            this.activeLoader = activeLoader;
        }
    }

    static final class ModViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView details;
        final TextView status;
        final Button toggle;
        final Button remove;

        ModViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.mod_item_name);
            details = itemView.findViewById(R.id.mod_item_details);
            status = itemView.findViewById(R.id.mod_item_status);
            toggle = itemView.findViewById(R.id.mod_item_toggle);
            remove = itemView.findViewById(R.id.mod_item_remove);
        }
    }
}
