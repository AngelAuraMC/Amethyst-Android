package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.IOException;

/**
 * Searches and installs individual mods (not modpacks) into the current instance's mods folder.
 * Auto-detects MC version from the selected profile's lastVersionId.
 */
public class ModsSearchFragment extends Fragment implements ModItemAdapter.SearchResultCallback {

    public static final String TAG = "ModsSearchFragment";

    private View mOverlay;
    private float mOverlayTopCache;

    private final RecyclerView.OnScrollListener mOverlayPositionListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            mOverlay.setY(MathUtils.clamp(mOverlay.getY() - dy, -mOverlay.getHeight(), mOverlayTopCache));
        }
    };

    private EditText mSearchEditText;
    private ImageButton mFilterButton;
    private RecyclerView mRecyclerview;
    private ModItemAdapter mModItemAdapter;
    private ProgressBar mSearchProgressBar;
    private TextView mStatusTextView;
    private ColorStateList mDefaultTextColor;

    private ModpackApi mModpackApi;
    private final SearchFilters mSearchFilters;

    public ModsSearchFragment() {
        super(R.layout.fragment_mod_search);
        mSearchFilters = new SearchFilters();
        mSearchFilters.isModpack = false; // individual mods, not modpacks
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Wrap CommonApi so installation goes to mods folder instead of creating a new instance
        mModpackApi = new ModsInstallApi(context.getString(R.string.curseforge_api_key));
        // Auto-detect MC version from current profile
        mSearchFilters.mcVersion = detectMcVersion();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mModItemAdapter = new ModItemAdapter(getResources(), mModpackApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);
        mOverlayTopCache = getResources().getDimension(R.dimen.fragment_padding_medium);

        mOverlay           = view.findViewById(R.id.search_mod_overlay);
        mSearchEditText    = view.findViewById(R.id.search_mod_edittext);
        mSearchProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mRecyclerview      = view.findViewById(R.id.search_mod_list);
        mStatusTextView    = view.findViewById(R.id.search_mod_status_text);
        mFilterButton      = view.findViewById(R.id.search_mod_filter);

        mDefaultTextColor = mStatusTextView.getTextColors();

        mRecyclerview.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerview.setAdapter(mModItemAdapter);
        mRecyclerview.addOnScrollListener(mOverlayPositionListener);

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            searchMods(mSearchEditText.getText().toString());
            mSearchEditText.clearFocus();
            return false;
        });

        mOverlay.post(() -> {
            int overlayHeight = mOverlay.getHeight();
            mRecyclerview.setPadding(
                    mRecyclerview.getPaddingLeft(),
                    mRecyclerview.getPaddingTop() + overlayHeight,
                    mRecyclerview.getPaddingRight(),
                    mRecyclerview.getPaddingBottom());
        });

        mFilterButton.setOnClickListener(v -> displayFilterDialog());
        searchMods(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mModItemAdapter);
        mRecyclerview.removeOnScrollListener(mOverlayPositionListener);
    }

    @Override
    public void onSearchFinished() {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.GONE);
    }

    @Override
    public void onSearchError(int error) {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.VISIBLE);
        switch (error) {
            case ERROR_INTERNAL:
                mStatusTextView.setTextColor(Color.RED);
                mStatusTextView.setText(R.string.search_modpack_error);
                break;
            case ERROR_NO_RESULTS:
                mStatusTextView.setTextColor(mDefaultTextColor);
                mStatusTextView.setText(R.string.search_modpack_no_result);
                break;
        }
    }

    private void searchMods(String name) {
        mSearchProgressBar.setVisibility(View.VISIBLE);
        mSearchFilters.name = name == null ? "" : name;
        mModItemAdapter.performSearchQuery(mSearchFilters);
    }

    private void displayFilterDialog() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_mod_filters)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            TextView mSelectedVersion = dialog.findViewById(R.id.search_mod_selected_mc_version_textview);
            Button mSelectVersionButton = dialog.findViewById(R.id.search_mod_mc_version_button);
            Button mApplyButton = dialog.findViewById(R.id.search_mod_apply_filters);

            assert mSelectedVersion != null;
            assert mSelectVersionButton != null;
            assert mApplyButton != null;

            mSelectVersionButton.setOnClickListener(v ->
                    VersionSelectorDialog.open(v.getContext(), true,
                            (id, snapshot) -> mSelectedVersion.setText(id)));

            mSelectedVersion.setText(mSearchFilters.mcVersion);

            mApplyButton.setOnClickListener(v -> {
                mSearchFilters.mcVersion = mSelectedVersion.getText().toString();
                searchMods(mSearchEditText.getText().toString());
                dialogInterface.dismiss();
            });
        });

        dialog.show();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Parse MC version from lastVersionId like "fabric-loader-0.16.14-1.21.4" → "1.21.4" or "1.21.4" → "1.21.4" */
    private String detectMcVersion() {
        try {
            String key = LauncherPreferences.DEFAULT_PREF
                    .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            if (key == null || key.isEmpty()) return null;
            LauncherProfiles.load();
            MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
            if (profile == null || profile.lastVersionId == null) return null;

            String vid = profile.lastVersionId;
            // fabric-loader-X.Y.Z-MC  → last segment after last "-MC" block
            // forge-MC-loaderVer       → second segment
            // 1.21.4                   → as-is
            if (vid.startsWith("fabric-loader-") || vid.startsWith("quilt-loader-")) {
                // fabric-loader-0.16.14-1.21.4 — MC is everything after the loader version
                String[] parts = vid.split("-");
                if (parts.length >= 4) return parts[parts.length - 1];
            }
            if (vid.contains("-forge-") || vid.contains("-neoforge-")) {
                // 1.21.4-forge-xxx — MC is first part
                return vid.split("-")[0];
            }
            // Plain version id
            return vid;
        } catch (Exception e) {
            return null;
        }
    }

    // ── ModsInstallApi ───────────────────────────────────────────────────────

    /**
     * Wraps CommonApi and overrides handleInstallation to download the mod JAR
     * directly into the current instance's mods/ folder instead of installing a modpack.
     */
    private static class ModsInstallApi extends CommonApi {

        ModsInstallApi(String curseforgeApiKey) {
            super(curseforgeApiKey);
        }

        @Override
        public void handleInstallation(Context context, ModDetail modDetail, int selectedVersion) {
            if (modDetail.isModpack) {
                // Fall back to normal modpack install
                super.handleInstallation(context, modDetail, selectedVersion);
                return;
            }

            String url = modDetail.versionUrls[selectedVersion];
            if (url == null || url.isEmpty()) {
                Tools.showErrorRemote(context, R.string.modpack_install_download_failed,
                        new IOException("No download URL available"));
                return;
            }

            // Determine file name from URL
            String fileName = url.substring(url.lastIndexOf('/') + 1);
            if (!fileName.endsWith(".jar")) fileName += ".jar";
            // Strip query params if present
            if (fileName.contains("?")) fileName = fileName.substring(0, fileName.indexOf('?'));

            File modsDir = getModsDir();
            if (!modsDir.exists()) modsDir.mkdirs();
            File destFile = new File(modsDir, fileName);

            String finalFileName = fileName;
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
            PojavApplication.sExecutorService.execute(() -> {
                try {
                    DownloadUtils.downloadFile(url, destFile);
                    ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                    Tools.runOnUiThread(() ->
                        new AlertDialog.Builder(context)
                            .setTitle(modDetail.title)
                            .setMessage(context.getString(R.string.mod_install_success, finalFileName))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    );
                } catch (IOException e) {
                    ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                    Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
                }
            });
        }

        private static File getModsDir() {
            try {
                String key = LauncherPreferences.DEFAULT_PREF
                        .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
                if (key != null && !key.isEmpty()) {
                    LauncherProfiles.load();
                    MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
                    if (profile != null) {
                        return new File(Tools.getGameDirPath(profile), "mods");
                    }
                }
            } catch (Exception ignored) {}
            return new File(Tools.DIR_GAME_NEW, "mods");
        }
    }
}
