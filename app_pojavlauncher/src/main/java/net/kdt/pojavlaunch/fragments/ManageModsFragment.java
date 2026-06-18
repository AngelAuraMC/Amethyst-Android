package net.kdt.pojavlaunch.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.InstalledModAdapter;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

public class ManageModsFragment extends Fragment {

    public static final String TAG = "ManageModsFragment";

    private static final String PREF_FILE      = "mod_filters";
    private static final String KEY_MC_VERSION = "mc_version_";
    private static final String KEY_LOADER     = "loader_";

    private ImageButton mFilterButton;
    private InstalledModAdapter mAdapter;

    public ManageModsFragment() {
        super(R.layout.fragment_manage_mods);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ImageButton backButton = view.findViewById(R.id.manage_mods_back);
        mFilterButton          = view.findViewById(R.id.manage_mods_filter);
        ImageButton addButton  = view.findViewById(R.id.manage_mods_add);
        TextView    title      = view.findViewById(R.id.manage_mods_title);
        RecyclerView recycler  = view.findViewById(R.id.manage_mods_recycler);
        View        emptyState = view.findViewById(R.id.manage_mods_empty);

        backButton.setOnClickListener(v -> requireActivity().onBackPressed());
        mFilterButton.setOnClickListener(v -> showFilterDialog());
        addButton.setOnClickListener(v -> openModSearch());

        String profileName = getCurrentProfileName();
        title.setText(profileName.isEmpty()
                ? getString(R.string.mcl_button_manage_mods)
                : profileName + " - Mods");

        refreshFilterButtonTint();

        // Build adapter, inject saved filter, then auto-check for updates
        String profileKey = getCurrentProfileKey();
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREF_FILE, android.content.Context.MODE_PRIVATE);
        String savedVersion = prefs.getString(KEY_MC_VERSION + profileKey, "");
        String savedLoader  = prefs.getString(KEY_LOADER      + profileKey, "");

        mAdapter = new InstalledModAdapter(getModsDir(), isEmpty -> {
            recycler.setVisibility(isEmpty ? View.GONE  : View.VISIBLE);
            emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        });
        mAdapter.setFilter(savedVersion, savedLoader);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(mAdapter);

        // Auto-check for updates as soon as the screen opens
        // If no filter is set, silently skip (nothing to compare against)
        if (!savedVersion.isEmpty() || !savedLoader.isEmpty()) {
            mAdapter.checkForUpdates(() -> {
                // no-op — individual items already refresh themselves
            });
        }
    }

    // ── Filter dialog ────────────────────────────────────────────────────────

    private void showFilterDialog() {
        String profileKey = getCurrentProfileKey();
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREF_FILE, android.content.Context.MODE_PRIVATE);

        String savedVersion = prefs.getString(KEY_MC_VERSION + profileKey, "");
        String savedLoader  = prefs.getString(KEY_LOADER      + profileKey, "");

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_mod_filters)
                .create();

        dialog.setOnShowListener(di -> {
            TextView versionText   = dialog.findViewById(R.id.search_mod_selected_mc_version_textview);
            Button   selectVersion = dialog.findViewById(R.id.search_mod_mc_version_button);
            Button   applyButton   = dialog.findViewById(R.id.search_mod_apply_filters);
            Spinner  loaderSpinner = dialog.findViewById(R.id.search_mod_loader_spinner);

            if (versionText == null || selectVersion == null || applyButton == null) return;

            versionText.setText(savedVersion);

            final String[] loaderValues = { "", "fabric", "forge", "quilt", "neoforge" };
            if (loaderSpinner != null) {
                String[] loaderLabels = {
                        getString(R.string.search_mod_any_loader),
                        "Fabric", "Forge", "Quilt", "NeoForge"
                };
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        requireContext(), android.R.layout.simple_spinner_item, loaderLabels);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                loaderSpinner.setAdapter(adapter);
                for (int i = 0; i < loaderValues.length; i++) {
                    if (loaderValues[i].equals(savedLoader)) {
                        loaderSpinner.setSelection(i);
                        break;
                    }
                }
            }

            selectVersion.setOnClickListener(v ->
                    VersionSelectorDialog.open(v.getContext(), true,
                            (id, snapshot) -> versionText.setText(id)));

            applyButton.setOnClickListener(v -> {
                String newVersion = versionText.getText().toString().trim();
                String newLoader  = (loaderSpinner != null)
                        ? loaderValues[loaderSpinner.getSelectedItemPosition()]
                        : "";

                prefs.edit()
                        .putString(KEY_MC_VERSION + profileKey, newVersion)
                        .putString(KEY_LOADER      + profileKey, newLoader)
                        .apply();

                // Update the adapter's filter and re-run update check
                if (mAdapter != null) {
                    mAdapter.setFilter(newVersion, newLoader);
                    if (!newVersion.isEmpty() || !newLoader.isEmpty()) {
                        mAdapter.checkForUpdates(() -> {});
                    }
                }

                refreshFilterButtonTint();
                di.dismiss();
            });
        });

        dialog.show();
    }

    private void openModSearch() {
        String profileKey = getCurrentProfileKey();
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREF_FILE, android.content.Context.MODE_PRIVATE);

        String version = prefs.getString(KEY_MC_VERSION + profileKey, "");
        String loader  = prefs.getString(KEY_LOADER      + profileKey, "");

        Bundle args = new Bundle();
        if (!version.isEmpty()) args.putString(ModsSearchFragment.ARG_PRESET_MC_VERSION, version);
        if (!loader.isEmpty())  args.putString(ModsSearchFragment.ARG_PRESET_LOADER,     loader);

        ModsSearchFragment fragment = new ModsSearchFragment();
        fragment.setArguments(args);
        navigateToFragment(fragment, ModsSearchFragment.TAG);
    }

    private void refreshFilterButtonTint() {
        if (mFilterButton == null || !isAdded()) return;
        String profileKey = getCurrentProfileKey();
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREF_FILE, android.content.Context.MODE_PRIVATE);
        boolean active = !prefs.getString(KEY_MC_VERSION + profileKey, "").isEmpty()
                      || !prefs.getString(KEY_LOADER      + profileKey, "").isEmpty();
        mFilterButton.setAlpha(active ? 1.0f : 0.4f);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @NonNull
    private String getCurrentProfileKey() {
        String key = LauncherPreferences.DEFAULT_PREF
                .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        return key != null ? key : "default";
    }

    private String getCurrentProfileName() {
        try {
            String key = getCurrentProfileKey();
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
            String key = getCurrentProfileKey();
            LauncherProfiles.load();
            MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
            if (profile != null) {
                File gameDir = Tools.getGameDirPath(profile);
                return new File(gameDir, "mods");
            }
        } catch (Exception ignored) {}
        return new File(Tools.DIR_GAME_NEW, "mods");
    }

    private void navigateToFragment(Fragment fragment, String tag) {
        Fragment parent = getParentFragment();
        if (parent != null) {
            parent.getChildFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.right_pane_container, fragment, tag)
                    .addToBackStack(tag)
                    .commit();
        } else {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.container_fragment, fragment, tag)
                    .addToBackStack(tag)
                    .commit();
        }
    }
}