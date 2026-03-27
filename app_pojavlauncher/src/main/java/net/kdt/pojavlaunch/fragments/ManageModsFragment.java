package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.InstalledModAdapter;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

public class ManageModsFragment extends Fragment {

    public static final String TAG = "ManageModsFragment";

    public ManageModsFragment() {
        super(R.layout.fragment_manage_mods);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ImageButton backButton  = view.findViewById(R.id.manage_mods_back);
        ImageButton addButton   = view.findViewById(R.id.manage_mods_add);
        TextView    title       = view.findViewById(R.id.manage_mods_title);
        RecyclerView recycler   = view.findViewById(R.id.manage_mods_recycler);
        View        emptyState  = view.findViewById(R.id.manage_mods_empty);

        // Back
        backButton.setOnClickListener(v -> Tools.removeCurrentFragment(requireActivity()));

        // Add → open mod store
        addButton.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, null));

        // Title: "ProfileName - Mods"
        String profileName = getCurrentProfileName();
        title.setText(profileName.isEmpty()
                ? getString(R.string.mcl_button_manage_mods)
                : profileName + " - Mods");

        // Build mod list
        File modsDir = getModsDir();
        InstalledModAdapter adapter = new InstalledModAdapter(modsDir, isEmpty -> {
            recycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        });

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String getCurrentProfileName() {
        try {
            String key = LauncherPreferences.DEFAULT_PREF
                    .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            if (key == null || key.isEmpty()) return "";
            LauncherProfiles.load();
            MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
            if (profile == null) return "";
            return profile.name != null ? profile.name : key;
        } catch (Exception e) {
            return "";
        }
    }

    private File getModsDir() {
        try {
            String key = LauncherPreferences.DEFAULT_PREF
                    .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            if (key != null && !key.isEmpty()) {
                LauncherProfiles.load();
                MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
                if (profile != null) {
                    File gameDir = Tools.getGameDirPath(profile);
                    return new File(gameDir, "mods");
                }
            }
        } catch (Exception ignored) {}
        return new File(Tools.DIR_GAME_NEW, "mods");
    }
}
