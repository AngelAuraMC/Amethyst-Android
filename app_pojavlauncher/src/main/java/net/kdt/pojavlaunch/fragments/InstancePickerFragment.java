package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.profiles.ProfileAdapter;
import net.kdt.pojavlaunch.profiles.ProfileAdapterExtra;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shown in the right pane (landscape) when the user taps the instance spinner.
 * Uses the same item_version_profile_layout + ProfileAdapter.setView() as the
 * spinner popup, so icon sizing is identical. Adds a "New instance" row at top.
 */
public class InstancePickerFragment extends Fragment {

    public static final String TAG = "InstancePickerFragment";

    public InstancePickerFragment() {
        super(R.layout.fragment_instance_picker);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Back button — delegates to activity so the right pane pops correctly
        view.findViewById(R.id.instance_picker_back)
                .setOnClickListener(v -> requireActivity().onBackPressed());

        RecyclerView recycler = view.findViewById(R.id.instance_picker_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        LauncherProfiles.load();
        Map<String, MinecraftProfile> profiles = LauncherProfiles.mainProfileJson.profiles;
        List<String> keys = new ArrayList<>(profiles.keySet());
        String selected = LauncherPreferences.DEFAULT_PREF
                .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");

        ProfileAdapter profileAdapter = new ProfileAdapter(new ProfileAdapterExtra[0]);

        recycler.setAdapter(new InstanceAdapter(keys, profiles, profileAdapter, selected,
                profileKey -> {
                    Fragment parentFrag = getParentFragment();
                    if (parentFrag instanceof MainMenuFragment) {
                        ((MainMenuFragment) parentFrag).selectInstance(profileKey);
                    }
                },
                () -> {
                    // "New instance" tapped — open profile type selector in right pane
                    Fragment parentFrag = getParentFragment();
                    if (parentFrag instanceof MainMenuFragment) {
                        ((MainMenuFragment) parentFrag).openChildPane(
                                ProfileTypeSelectFragment.class,
                                ProfileTypeSelectFragment.TAG, null);
                    } else {
                        Tools.swapFragment(requireActivity(),
                                ProfileTypeSelectFragment.class,
                                ProfileTypeSelectFragment.TAG, null);
                    }
                }));
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    interface OnInstanceSelected { void onSelected(String profileKey); }
    interface OnCreateNew        { void onCreate(); }

    static class InstanceAdapter extends RecyclerView.Adapter<InstanceAdapter.VH> {

        private static final int VIEW_TYPE_CREATE   = 0;
        private static final int VIEW_TYPE_INSTANCE = 1;

        private final List<String> mKeys;
        private final Map<String, MinecraftProfile> mProfiles;
        private final ProfileAdapter mProfileAdapter; // used for rendering via setView
        private String mSelectedKey;
        private final OnInstanceSelected mOnSelect;
        private final OnCreateNew mOnCreate;

        InstanceAdapter(List<String> keys, Map<String, MinecraftProfile> profiles,
                        ProfileAdapter profileAdapter, String selectedKey,
                        OnInstanceSelected onSelect, OnCreateNew onCreate) {
            mKeys           = keys;
            mProfiles       = profiles;
            mProfileAdapter = profileAdapter;
            mSelectedKey    = selectedKey;
            mOnSelect       = onSelect;
            mOnCreate       = onCreate;
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? VIEW_TYPE_CREATE : VIEW_TYPE_INSTANCE;
        }

        @Override
        public int getItemCount() {
            return mKeys.size() + 1; // +1 for "New instance"
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Use the exact same layout the spinner popup uses
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_version_profile_layout, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            if (getItemViewType(position) == VIEW_TYPE_CREATE) {
                // Render the "New instance" extra via ProfileAdapter
                ProfileAdapterExtra extra = new ProfileAdapterExtra(
                        0,
                        R.string.create_profile,
                        h.itemView.getContext().getDrawable(R.drawable.ic_add));
                mProfileAdapter.setViewExtra(h.itemView, extra);
                h.itemView.setOnClickListener(v -> mOnCreate.onCreate());
                return;
            }

            String key = mKeys.get(position - 1); // offset by 1 for create row
            MinecraftProfile p = mProfiles.get(key);

            // Let ProfileAdapter.setView() handle icon + label — identical to spinner popup
            mProfileAdapter.setView(h.itemView, key, key.equals(mSelectedKey));

            h.itemView.setOnClickListener(v -> {
                String prev = mSelectedKey;
                mSelectedKey = key;
                notifyItemChanged(mKeys.indexOf(prev) + 1);
                notifyItemChanged(position);
                mOnSelect.onSelected(key);
            });
        }

        static class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View v) { super(v); }
        }
    }
}