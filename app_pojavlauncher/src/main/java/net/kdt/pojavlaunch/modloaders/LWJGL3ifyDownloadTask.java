package net.kdt.pojavlaunch.modloaders;

import android.app.Activity;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

public class LWJGL3ifyDownloadTask implements Runnable, Tools.DownloaderFeedback, AsyncMinecraftDownloader.DoneListener {
    protected static final String TAG = "LWJGL3ifyDownloadTask";
    private final ModloaderDownloadListener mListener;
    private final LWJGL3ifyUtils.LWJGL3ifyMod mLWJGL3ifyMod;
    private final Activity mActivity;

    public LWJGL3ifyDownloadTask(ModloaderDownloadListener mListener, LWJGL3ifyUtils.LWJGL3ifyMod mLWJGL3ifyMod, Activity activity) {
        this.mListener = mListener;
        this.mLWJGL3ifyMod = mLWJGL3ifyMod;
        this.mActivity = activity;
    }

    @Override
    public void run() {
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.fabric_dl_progress, "BTA");
        try {
            runCatching();
            mListener.onDownloadFinished(null);
        }catch (Exception e) {
            mListener.onDownloadError(e);
        }finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }

    }

    private File tryDownloadModJar() throws IOException {
        try {
            File jarFile = new File(Tools.DIR_CACHE, "lwjgl3ify-jars/lwjgl3ify-"+ mLWJGL3ifyMod.versionName+".jar");
            if (!(jarFile.exists() && Objects.equals(getSha1(jarFile), mLWJGL3ifyMod.hash)))
                DownloadUtils.downloadFileMonitored(
                        mLWJGL3ifyMod.downloadUrl,
                        jarFile,
                        new byte[8192],
                        this
            );
            return jarFile;
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IOException("Unable to download LWJGL3ify from " + mLWJGL3ifyMod.downloadUrl, e);
        }
    }

    private void tryDownloadDeps(File modsDir) throws IOException {
        List<LWJGL3ifyUtils.LWJGL3ifyMod> deps = LWJGL3ifyUtils.collectDependencies(mLWJGL3ifyMod, new CommonApi(mActivity.getString(R.string.curseforge_api_key)));
        for (int i=0; i < deps.size(); ++i) {
            URI uri = null;
            try {
                uri = new URI(deps.get(i).downloadUrl);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            String path = uri.getPath();
            String fileName = new File(path).getName();
            try {
                DownloadUtils.downloadFileMonitored(
                        deps.get(i).downloadUrl,
                        new File(modsDir, fileName),
                        new byte[8192],
                        this
                );
            } catch (IOException e) {
                throw new IOException("Unable to download"+deps.get(i).versionName+" from " + deps.get(i).downloadUrl, e);
            }
        }
    }

    public String getSha1(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest algorithm = MessageDigest.getInstance("SHA-1");
        //noinspection IOStreamConstructor It will reccomend you use an API26 function like a dumb
        DigestInputStream hashingStream = new DigestInputStream(new FileInputStream(file), algorithm);
        byte[] buffer = new byte[8192];
        while (hashingStream.read(buffer) != -1) {} // just read to update the digest
        hashingStream.close();
        byte[] digest = algorithm.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void runCatching() throws IOException {
        File modJar = tryDownloadModJar();
        // This cannot be allowed to match the mod.jar ID otherwise conflicts occur and GLFW input breaks
        String LWJGL3ifyProfileID = LWJGL3ifyUtils.getProfileID(modJar);
        if (!modJar.exists()) throw new IOException("Failed to download LWJGL3ify "+ mLWJGL3ifyMod.versionName);
        MinecraftProfile profile = LWJGL3ifyUtils.createProfile(LWJGL3ifyProfileID, mLWJGL3ifyMod.versionName, mLWJGL3ifyMod.iconUrl);
        new MinecraftDownloader().start(mActivity, LWJGL3ifyUtils.installJson(modJar), LWJGL3ifyProfileID, this);
        LWJGL3ifyUtils.createInstance(profile, modJar, mLWJGL3ifyMod.versionName);
        tryDownloadDeps(new File(Tools.DIR_GAME_HOME, profile.gameDir+"/mods"));

        LauncherProfiles.load();
        LauncherProfiles.insertMinecraftProfile(profile);
        LauncherProfiles.write();
    }

    @Override
    public void updateProgress(int curr, int max) {
        int progress100 = (int)(((float)curr / (float)max)*100f);
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, progress100, R.string.of_dl_progress, mLWJGL3ifyMod.versionName);
    }

    @Override
    public void onDownloadDone() {

    }

    @Override
    public void onDownloadFailed(Throwable throwable) {

    }
}
