package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

public class SearchModFragment extends Fragment implements ModItemAdapter.SearchResultCallback {

    public static final String TAG = "SearchModFragment";
    private View mOverlay;
    private float mOverlayTopCache; // Padding cache reduce resource lookup

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

    private ModpackApi modpackApi;

    private final SearchFilters mSearchFilters;

    public SearchModFragment(){
        super(R.layout.fragment_mod_search);
        mSearchFilters = new SearchFilters();
        mSearchFilters.isModpack = true;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        modpackApi = new ModpackSearchApi(context.getString(R.string.curseforge_api_key), mSearchFilters);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // You can only access resources after attaching to current context
        mModItemAdapter = new ModItemAdapter(getResources(), modpackApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);
        mOverlayTopCache = getResources().getDimension(R.dimen.fragment_padding_medium);

        mOverlay = view.findViewById(R.id.search_mod_overlay);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mSearchProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mRecyclerview = view.findViewById(R.id.search_mod_list);
        mStatusTextView = view.findViewById(R.id.search_mod_status_text);
        mFilterButton = view.findViewById(R.id.search_mod_filter);

        mDefaultTextColor = mStatusTextView.getTextColors();

        mRecyclerview.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerview.setAdapter(mModItemAdapter);

        mRecyclerview.addOnScrollListener(mOverlayPositionListener);

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            searchMods(mSearchEditText.getText().toString());
            mSearchEditText.clearFocus();
            return false;
        });

        mOverlay.post(()->{
           int overlayHeight = mOverlay.getHeight();
           mRecyclerview.setPadding(mRecyclerview.getPaddingLeft(),
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

        // setup the view behavior
        dialog.setOnShowListener(dialogInterface -> {
            TextView mSelectedVersion = dialog.findViewById(R.id.search_mod_selected_mc_version_textview);
            Button mSelectVersionButton = dialog.findViewById(R.id.search_mod_mc_version_button);
            Button mApplyButton = dialog.findViewById(R.id.search_mod_apply_filters);
            Spinner mLoaderSpinner = dialog.findViewById(R.id.search_mod_loader_spinner);

            assert mSelectVersionButton != null;
            assert mSelectedVersion != null;
            assert mApplyButton != null;

            // Set up loader spinner
            if (mLoaderSpinner != null) {
                String[] loaderLabels = {"Any loader", "Fabric", "Forge", "Quilt", "NeoForge"};
                final String[] loaderValues = {"", "fabric", "forge", "quilt", "neoforge"};
                ArrayAdapter<String> loaderAdapter = new ArrayAdapter<>(
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

    // ── ModpackSearchApi ──────────────────────────────────────────────────────

    private static class ModpackSearchApi extends CommonApi {
        private final SearchFilters mFilters;
        private final ModrinthApi mModrinthApi = new ModrinthApi();

        ModpackSearchApi(String curseforgeApiKey, SearchFilters filters) {
            super(curseforgeApiKey);
            mFilters = filters;
        }

        /**
         * Override getModDetails so the version dropdown only shows versions
         * matching the selected MC version and loader filter.
         */
        @Override
        public ModDetail getModDetails(ModItem item) {
            if (item.apiSource == Constants.SOURCE_MODRINTH) {
                String filterVer = (mFilters.mcVersion != null && !mFilters.mcVersion.isEmpty())
                        ? mFilters.mcVersion : null;
                String filterLoader = (mFilters.modLoader != null && !mFilters.modLoader.isEmpty())
                        ? mFilters.modLoader : null;
                return mModrinthApi.getModDetails(item, filterVer, filterLoader);
            }
            // CurseForge: delegate normally (CF search already filters by version/loader)
            return super.getModDetails(item);
        }
    }
}