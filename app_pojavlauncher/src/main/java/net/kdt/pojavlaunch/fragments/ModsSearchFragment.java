package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
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
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Searches and installs individual mods into the current instance's mods folder.
 * - Version filter: when an MC version is selected, only versions matching it are shown.
 * - Dependency dialog: shown before download, matching the ModBundle UI.
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
        mSearchFilters.isModpack = false;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mModpackApi = new ModsInstallApi(context.getString(R.string.curseforge_api_key), mSearchFilters);
        ((ModsInstallApi) mModpackApi).mActivityContext = context;
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
        mSearchEditText.setHint(R.string.hint_search_mod);
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
                mStatusTextView.setText(R.string.search_mod_error);
                break;
            case ERROR_NO_RESULTS:
                mStatusTextView.setTextColor(mDefaultTextColor);
                mStatusTextView.setText(R.string.search_mod_no_result);
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
            android.widget.Spinner mLoaderSpinner = dialog.findViewById(R.id.search_mod_loader_spinner);

            assert mSelectedVersion != null;
            assert mSelectVersionButton != null;
            assert mApplyButton != null;

            // Set up loader spinner
            if (mLoaderSpinner != null) {
                String[] loaderLabels = {"Any loader", "Fabric", "Forge", "Quilt", "NeoForge"};
                final String[] loaderValues = {"", "fabric", "forge", "quilt", "neoforge"};
                android.widget.ArrayAdapter<String> loaderAdapter = new android.widget.ArrayAdapter<>(
                        requireContext(), android.R.layout.simple_spinner_item, loaderLabels);
                loaderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                mLoaderSpinner.setAdapter(loaderAdapter);

                // Restore current selection
                String currentLoader = mSearchFilters.modLoader != null ? mSearchFilters.modLoader : "";
                for (int i = 0; i < loaderValues.length; i++) {
                    if (loaderValues[i].equals(currentLoader)) {
                        mLoaderSpinner.setSelection(i);
                        break;
                    }
                }

                mSelectVersionButton.setOnClickListener(v ->
                        VersionSelectorDialog.open(v.getContext(), true,
                                (id, snapshot) -> mSelectedVersion.setText(id)));

                mSelectedVersion.setText(mSearchFilters.mcVersion);

                mApplyButton.setOnClickListener(v -> {
                    mSearchFilters.mcVersion = mSelectedVersion.getText().toString();
                    int pos = mLoaderSpinner.getSelectedItemPosition();
                    mSearchFilters.modLoader = loaderValues[pos];
                    searchMods(mSearchEditText.getText().toString());
                    dialogInterface.dismiss();
                });
            } else {
                // Fallback if spinner view not found
                mSelectVersionButton.setOnClickListener(v ->
                        VersionSelectorDialog.open(v.getContext(), true,
                                (id, snapshot) -> mSelectedVersion.setText(id)));

                mSelectedVersion.setText(mSearchFilters.mcVersion);

                mApplyButton.setOnClickListener(v -> {
                    mSearchFilters.mcVersion = mSelectedVersion.getText().toString();
                    searchMods(mSearchEditText.getText().toString());
                    dialogInterface.dismiss();
                });
            }
        });

        dialog.show();
    }

    // ── ModsInstallApi ────────────────────────────────────────────────────────

    private static class ModsInstallApi extends CommonApi {

        private final SearchFilters mFilters;
        private final ModrinthApi mModrinthApi = new ModrinthApi();
        private final Handler mMainHandler = new Handler(Looper.getMainLooper());
        private Context mActivityContext;
        private final net.kdt.pojavlaunch.modloaders.modpacks.api.CurseforgeApi mCurseforgeApi;

        ModsInstallApi(String curseforgeApiKey, SearchFilters filters) {
            super(curseforgeApiKey);
            mFilters = filters;
            mCurseforgeApi = new net.kdt.pojavlaunch.modloaders.modpacks.api.CurseforgeApi(curseforgeApiKey);
        }

        /**
         * Override getModDetails to filter versions by the selected MC version.
         * Only versions matching the filter are shown in the version dropdown.
         */
        @Override
        public ModDetail getModDetails(ModItem item) {
            if (item.apiSource == net.kdt.pojavlaunch.modloaders.modpacks.models.Constants.SOURCE_MODRINTH) {
                String filterVer = (mFilters.mcVersion != null && !mFilters.mcVersion.isEmpty())
                        ? mFilters.mcVersion : null;
                String filterLoader = (mFilters.modLoader != null && !mFilters.modLoader.isEmpty())
                        ? mFilters.modLoader : null;
                return mModrinthApi.getModDetails(item, filterVer, filterLoader);
            }
            if (item.apiSource == net.kdt.pojavlaunch.modloaders.modpacks.models.Constants.SOURCE_CURSEFORGE) {
                String filterVer = (mFilters.mcVersion != null && !mFilters.mcVersion.isEmpty())
                        ? mFilters.mcVersion : null;
                return mCurseforgeApi.getModDetails(item, filterVer);
            }
            return super.getModDetails(item);
        }

        @Override
        public void handleInstallation(Context context, ModDetail modDetail, int selectedVersion) {
            if (modDetail.isModpack) {
                super.handleInstallation(context, modDetail, selectedVersion);
                return;
            }

            String url = modDetail.versionUrls[selectedVersion];

            // Check if this is a CF-restricted mod using the flag set during search
            boolean isCfRestricted = modDetail.apiSource == Constants.SOURCE_CURSEFORGE
                    && (modDetail.isRestricted || url == null || url.isEmpty());

            if (isCfRestricted) {
                String cfUrl = (modDetail.websiteUrl != null && !modDetail.websiteUrl.isEmpty())
                        ? modDetail.websiteUrl
                        : "https://www.curseforge.com/minecraft/mc-mods/" + modDetail.id;
                Context dialogCtx = mActivityContext != null ? mActivityContext : context;
                mMainHandler.post(() ->
                    new AlertDialog.Builder(dialogCtx)
                        .setTitle(modDetail.title)
                        .setMessage("This mod restricts third-party downloads.\n\nDownload it manually from CurseForge and place it in your mods folder:\n\n" + cfUrl)
                        .setPositiveButton("Open CurseForge", (d, w) ->
                            Tools.openURL((android.app.Activity) dialogCtx, cfUrl))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                );
                return;
            }

            if (url == null || url.isEmpty()) {
                Tools.showErrorRemote(context, R.string.modpack_install_download_failed,
                        new IOException("No download URL available for this mod"));
                return;
            }

            // Extract filename
            String rawName = url.substring(url.lastIndexOf('/') + 1);
            if (rawName.contains("?")) rawName = rawName.substring(0, rawName.indexOf('?'));
            final String fileName = rawName.endsWith(".jar") ? rawName : rawName + ".jar";

            // Check if this version has dependencies
            String[] depIds   = (modDetail.versionDependencyIds   != null) ? modDetail.versionDependencyIds[selectedVersion]   : null;
            String[] depTypes = (modDetail.versionDependencyTypes != null) ? modDetail.versionDependencyTypes[selectedVersion] : null;

            if (depIds == null || depIds.length == 0) {
                // No deps — download directly
                downloadMod(context, url, fileName, new String[0], new String[0]);
                return;
            }

            // Fetch project names for all deps, then show dialog
            String[] labels = new String[depIds.length];
            final boolean[] checkedDefaults = new boolean[depIds.length];
            AtomicInteger remaining = new AtomicInteger(depIds.length);

            for (int i = 0; i < depIds.length; i++) {
                final int idx = i;
                final String type = (depTypes != null && idx < depTypes.length) ? depTypes[idx] : "required";
                final String prefix = "required".equals(type) ? "Required: " : "Optional: ";
                checkedDefaults[idx] = "required".equals(type);

                final String projectId = depIds[idx];
                PojavApplication.sExecutorService.execute(() -> {
                    // Fetch project name from Modrinth
                    String name = fetchProjectName(projectId);
                    labels[idx] = prefix + (name != null ? name : projectId);
                    if (remaining.decrementAndGet() == 0) {
                        mMainHandler.post(() -> showDepsDialog(context, url, fileName,
                                depIds, depTypes, labels, checkedDefaults));
                    }
                });
            }
        }

        private void showDepsDialog(Context context, String url, String fileName,
                                    String[] depIds, String[] depTypes,
                                    String[] labels, boolean[] checkedDefaults) {
            // context here is getApplicationContext() from ModItemAdapter — no window token.
            // Use the stored Activity reference instead.
            Context dialogCtx = mActivityContext != null ? mActivityContext : context;
            boolean[] selected = checkedDefaults.clone();

            new AlertDialog.Builder(dialogCtx)
                    .setTitle(R.string.mod_deps_title)
                    .setMultiChoiceItems(labels, selected,
                            (dialog, which, isChecked) -> selected[which] = isChecked)
                    .setPositiveButton(R.string.mod_deps_install_selected, (d, w) -> {
                        List<String> selectedIds = new ArrayList<>();
                        for (int i = 0; i < depIds.length; i++) {
                            if (selected[i]) selectedIds.add(depIds[i]);
                        }
                        downloadMod(context, url, fileName,
                                selectedIds.toArray(new String[0]), depTypes);
                    })
                    .setNeutralButton(R.string.mod_deps_install_without,
                            (d, w) -> downloadMod(context, url, fileName, new String[0], new String[0]))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void downloadMod(Context context, String url, String fileName,
                                  String[] depIds, String[] depTypes) {
            File modsDir = getModsDir();
            if (!modsDir.exists()) modsDir.mkdirs();

            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
            PojavApplication.sExecutorService.execute(() -> {
                try {
                    // Download main mod
                    DownloadUtils.downloadFile(url, new File(modsDir, fileName));

                    // Download selected dependencies
                    for (String depId : depIds) {
                        if (depId == null || depId.isEmpty()) continue;
                        downloadDependency(depId, modsDir);
                    }

                    ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                    Tools.runOnUiThread(() ->
                            Toast.makeText(context,
                                    context.getString(R.string.mod_install_success, fileName),
                                    Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                    Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
                }
            });
        }

        private void downloadDependency(String projectId, File modsDir) {
            // Fetch latest version for the current MC version/loader filter
            try {
                String filterVer = (mFilters.mcVersion != null && !mFilters.mcVersion.isEmpty())
                        ? mFilters.mcVersion : "";
                String filterLoader = (mFilters.modLoader != null && !mFilters.modLoader.isEmpty())
                        ? mFilters.modLoader : null;
                ModItem depItem = new ModItem(
                        net.kdt.pojavlaunch.modloaders.modpacks.models.Constants.SOURCE_MODRINTH,
                        false, projectId, projectId, "", "");
                ModDetail depDetail = mModrinthApi.getModDetails(depItem, filterVer.isEmpty() ? null : filterVer, filterLoader);
                if (depDetail == null || depDetail.versionUrls == null || depDetail.versionUrls.length == 0) return;

                String depUrl = depDetail.versionUrls[0];
                String depName = depUrl.substring(depUrl.lastIndexOf('/') + 1);
                if (depName.contains("?")) depName = depName.substring(0, depName.indexOf('?'));
                if (!depName.endsWith(".jar")) depName += ".jar";

                DownloadUtils.downloadFile(depUrl, new File(modsDir, depName));
            } catch (Exception e) {
                Log.w(TAG, "Failed to download dependency " + projectId + ": " + e.getMessage());
            }
        }

        private String fetchProjectName(String projectId) {
            try {
                net.kdt.pojavlaunch.modloaders.modpacks.api.ApiHandler handler =
                        new net.kdt.pojavlaunch.modloaders.modpacks.api.ApiHandler("https://api.modrinth.com/v2");
                com.google.gson.JsonObject obj = handler.get("project/" + projectId,
                        com.google.gson.JsonObject.class);
                if (obj != null && obj.has("title")) return obj.get("title").getAsString();
            } catch (Exception ignored) {}
            return null;
        }

        private static File getModsDir() {
            try {
                String key = LauncherPreferences.DEFAULT_PREF
                        .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
                if (key != null && !key.isEmpty()) {
                    LauncherProfiles.load();
                    MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
                    if (profile != null) return new File(Tools.getGameDirPath(profile), "mods");
                }
            } catch (Exception ignored) {}
            return new File(Tools.DIR_GAME_NEW, "mods");
        }
    }
}