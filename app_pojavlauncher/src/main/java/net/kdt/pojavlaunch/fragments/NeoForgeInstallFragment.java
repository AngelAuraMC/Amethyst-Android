package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.widget.ExpandableListAdapter;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.JavaGUILauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.ForgeDownloadTask;
import net.kdt.pojavlaunch.modloaders.ForgeUtils;
import net.kdt.pojavlaunch.modloaders.ModloaderListenerProxy;
import net.kdt.pojavlaunch.modloaders.NeoForgeDownloadTask;
import net.kdt.pojavlaunch.modloaders.NeoForgeVersionListAdapter;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class NeoForgeInstallFragment extends ModVersionListFragment<List<String>> {
    public static final String TAG = "NeoForgeInstallFragment";
    public NeoForgeInstallFragment() {
        super(TAG);
    }

    private static final String NEOFORGE_METADATA_URL = "https://meta.prismlauncher.org/v1/net.neoforged/index.json";


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public int getTitleText() {
        return R.string.neoforge_dl_select_version;
    }

    @Override
    public int getNoDataMsg() {
        return R.string.neoforge_dl_no_installer;
    }

    @Override
    public List<String> loadVersionList() {
        String test = null;
        try {
            test = DownloadUtils.downloadStringCached(NEOFORGE_METADATA_URL, "neoforge_versions", input -> input);
        } catch (Exception e) {
            Tools.showErrorRemote(e);
        }
        return Collections.singletonList(test);
        // Moved the parsing logic to the adapter because there is no way to get this info easily, we use prism's index
        // since neoforge doesn't actually give this information easily anywhere.
        // To clarify, neoforge does not provide maven APIs to get supported Minecraft versions for each loader version

    }

    @Override
    public ExpandableListAdapter createAdapter(List<String> versionList, LayoutInflater layoutInflater) {
        return new NeoForgeVersionListAdapter(versionList, layoutInflater);
    }

    @Override
    public Runnable createDownloadTask(Object selectedVersion, ModloaderListenerProxy listenerProxy) {
        return new NeoForgeDownloadTask(listenerProxy, (String) selectedVersion);
    }

    @Override
    public void onDownloadFinished(Context context, File downloadedFile) {
        Intent modInstallerStartIntent = new Intent(context, JavaGUILauncherActivity.class);
        modInstallerStartIntent.putExtra("javaArgs", "-jar "+downloadedFile.getAbsolutePath()+" --install-client");
        context.startActivity(modInstallerStartIntent);
    }
}
