package net.kdt.pojavlaunch.modloaders;

import androidx.annotation.NonNull;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.fragments.NeoForgeInstallFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public class NeoForgeDownloadTask implements Runnable, Tools.DownloaderFeedback {
    private final String mDownloadUrl;
    private final String mLoaderVersion;

    private final ModloaderDownloadListener mListener;

    public NeoForgeDownloadTask(ModloaderDownloadListener listener, @NonNull String loaderVersion) {
        this.mListener = listener;
        this.mDownloadUrl = String.format(NEOFORGE_INSTALLER_URL, loaderVersion);
        this.mLoaderVersion = loaderVersion;
    }

    private static final String NEOFORGE_INSTALLER_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/%1$s/neoforge-%1$s-installer.jar";

    @Override
    public void run() {
        if(determineDownloadUrl()) {
            downloadNeoForge();
        }
        ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
    }

    @Override
    public void updateProgress(int curr, int max) {
        int progress100 = (int)(((float)curr / (float)max)*100f);
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, progress100, R.string.forge_dl_progress, mLoaderVersion);
    }

    private void downloadNeoForge() {
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.forge_dl_progress, mLoaderVersion);
        try {
            File destinationFile = new File(Tools.DIR_CACHE, "neoforge-installer.jar");
            byte[] buffer = new byte[8192];
            DownloadUtils.downloadFileMonitored(mDownloadUrl, destinationFile, buffer, this);
            mListener.onDownloadFinished(destinationFile);
        }catch (FileNotFoundException e) {
            mListener.onDataNotAvailable();
        } catch (IOException e) {
            mListener.onDownloadError(e);
        }
    }

    public boolean determineDownloadUrl() {
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.neoforge_dl_searching);
        try {
            if(!findVersion()) {
                mListener.onDataNotAvailable();
                return false;
            }
        }catch (IOException e) {
            mListener.onDownloadError(e);
            return false;
        }
        return true;
    }

    public boolean findVersion() throws IOException {
        List<String> neoforgeVersions = NeoForgeInstallFragment.downloadNeoForgeVersions();
        if(neoforgeVersions == null) return false;
        for(String versionName : neoforgeVersions) {
            if(!versionName.startsWith(mLoaderVersion)) continue;
            return true;
        }
        return false;
    }

}
