package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;
import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class ProfileTypeSelectFragment extends Fragment {
    public static final String TAG = "ProfileTypeSelectFragment";
    public ProfileTypeSelectFragment() {
        super(R.layout.fragment_profile_type);
    }
    public ProfileTypeSelectFragment(int layout) {
        super(layout);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.vanilla_profile).setOnClickListener(v ->
                navigateTo(ProfileEditorFragment.class, ProfileEditorFragment.TAG, new Bundle(1)));

        view.findViewById(R.id.optifine_profile).setOnClickListener(v ->
                tryInstall(OptiFineInstallFragment.class, OptiFineInstallFragment.TAG));
        view.findViewById(R.id.modded_profile_fabric).setOnClickListener((v)->
                tryInstall(FabricInstallFragment.class, FabricInstallFragment.TAG));
        view.findViewById(R.id.modded_profile_forge).setOnClickListener((v)->
                tryInstall(ForgeInstallFragment.class, ForgeInstallFragment.TAG));
        view.findViewById(R.id.modded_profile_neoforge).setOnClickListener((v)->
                tryInstall(NeoForgeInstallFragment.class, NeoForgeInstallFragment.TAG));
        view.findViewById(R.id.modded_profile_modpack).setOnClickListener((v)->
                tryInstall(ModpackCreateFragment.class, ModpackCreateFragment.TAG));
        view.findViewById(R.id.modded_profile_quilt).setOnClickListener((v)->
                tryInstall(QuiltInstallFragment.class, QuiltInstallFragment.TAG));
        view.findViewById(R.id.modded_profile_bta).setOnClickListener((v)->
                tryInstall(BTAInstallFragment.class, BTAInstallFragment.TAG));
    }

    /** Navigate within right pane if inside MainMenuFragment, otherwise full-screen swap. */
    protected void navigateTo(Class<? extends Fragment> cls, String tag, android.os.Bundle args) {
        // Walk up to find MainMenuFragment (could be grandparent if nested)
        Fragment parent = getParentFragment();
        while (parent != null && !(parent instanceof MainMenuFragment)) {
            parent = parent.getParentFragment();
        }
        if (parent instanceof MainMenuFragment) {
            ((MainMenuFragment) parent).openChildPane(cls, tag, args);
        } else {
            Tools.swapFragment(requireActivity(), cls, tag, args);
        }
    }

    private void tryInstall(Class<? extends Fragment> fragmentClass, String tag){
        if(!hasOnlineProfile()){
            hasNoOnlineProfileDialog(requireActivity());
        } else {
            navigateTo(fragmentClass, tag, null);
        }
    }
}