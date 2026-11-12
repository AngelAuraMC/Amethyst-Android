package net.kdt.pojavlaunch.modloaders.modpacks;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;

import java.util.ArrayList;
import java.util.List;

public class PackManagerAdapter extends RecyclerView.Adapter<PackManagerAdapter.PackViewHolder> {
    public interface Listener {
        void onToggle(PackArchiveInspector.PackEntry entry);
        void onRemove(PackArchiveInspector.PackEntry entry);
    }

    private final Listener listener;
    private final List<PackArchiveInspector.PackEntry> entries = new ArrayList<>();

    public PackManagerAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setEntries(List<PackArchiveInspector.PackEntry> source) {
        entries.clear();
        if (source != null) entries.addAll(source);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_novacraft_pack, parent, false);
        return new PackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PackViewHolder holder, int position) {
        PackArchiveInspector.PackEntry entry = entries.get(position);
        holder.title.setText(entry.info.title);
        holder.detail.setText(entry.info.detail == null || entry.info.detail.isEmpty()
                ? entry.file.getName() : entry.info.detail);
        holder.format.setText(entry.info.format == null || entry.info.format.isEmpty()
                ? (entry.directory ? "Folder pack" : "ZIP archive") : entry.info.format);
        holder.status.setText(entry.available ? R.string.novacraft_pack_available : R.string.novacraft_pack_hidden);
        holder.toggle.setText(entry.available ? R.string.novacraft_pack_hide : R.string.novacraft_pack_show);
        holder.toggle.setOnClickListener(v -> listener.onToggle(entry));
        holder.remove.setOnClickListener(v -> listener.onRemove(entry));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static final class PackViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView detail;
        final TextView format;
        final TextView status;
        final Button toggle;
        final Button remove;

        PackViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.pack_item_title);
            detail = itemView.findViewById(R.id.pack_item_detail);
            format = itemView.findViewById(R.id.pack_item_format);
            status = itemView.findViewById(R.id.pack_item_status);
            toggle = itemView.findViewById(R.id.pack_item_toggle);
            remove = itemView.findViewById(R.id.pack_item_remove);
        }
    }
}
