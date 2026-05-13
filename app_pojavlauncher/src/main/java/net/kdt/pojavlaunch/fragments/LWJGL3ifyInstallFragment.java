package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ExpandableListAdapter;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.modloaders.LWJGL3ifyDownloadTask;
import net.kdt.pojavlaunch.modloaders.LWJGL3ifyUtils;
import net.kdt.pojavlaunch.modloaders.LWJGL3ifyVersionListAdapter;
import net.kdt.pojavlaunch.modloaders.ModloaderListenerProxy;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;

import java.io.File;
import java.io.IOException;

public class LWJGL3ifyInstallFragment extends ModVersionListFragment<LWJGL3ifyUtils.LWJGL3ifyVersionList>{
    public static final String TAG = "LWJGL3ifyInstallFragment";
    private ModpackApi modpackApi;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        modpackApi = new CommonApi(context.getString(R.string.curseforge_api_key));
    }
    public LWJGL3ifyInstallFragment() {
        super(TAG);
    }

    /**
     * @return 
     */
    @Override
    public int getTitleText() {
        return R.string.select_lwjgl3ify_version;
    }

    /**
     * @return 
     */
    @Override
    public int getNoDataMsg() {
        return R.string.modloader_dl_failed_to_load_list;
    }

    /**
     * @return 
     * @throws IOException
     */
    @Override
    public LWJGL3ifyUtils.LWJGL3ifyVersionList loadVersionList() throws IOException {
        return LWJGL3ifyUtils.getLWJGL3ifyVersionList(modpackApi);
    }

    /**
     * @param versionList 
     * @param layoutInflater
     * @return
     */
    @Override
    public ExpandableListAdapter createAdapter(LWJGL3ifyUtils.LWJGL3ifyVersionList versionList, LayoutInflater layoutInflater) {
        return new LWJGL3ifyVersionListAdapter(versionList, layoutInflater);
    }

    /**
     * @param selectedVersion 
     * @param listenerProxy
     * @return
     */
    @Override
    public Runnable createDownloadTask(Object selectedVersion, ModloaderListenerProxy listenerProxy) {
        return new LWJGL3ifyDownloadTask(listenerProxy, (LWJGL3ifyUtils.LWJGL3ifyMod) selectedVersion, requireActivity());
    }

    /**
     * @param context 
     * @param downloadedFile
     */
    @Override
    public void onDownloadFinished(Context context, File downloadedFile) {
        // Nothing to do.
    }
}
