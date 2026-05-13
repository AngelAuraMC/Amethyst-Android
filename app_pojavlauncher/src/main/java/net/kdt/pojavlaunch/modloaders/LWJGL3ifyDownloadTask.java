package net.kdt.pojavlaunch.modloaders;

import android.app.Activity;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipFile;

public class LWJGL3ifyDownloadTask implements Runnable, Tools.DownloaderFeedback, AsyncMinecraftDownloader.DoneListener {
    private static final String TAG = "LWJGL3ifyDownloadTask";
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

    private String tryDownloadIcon() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (Base64OutputStream base64OutputStream = new Base64OutputStream(byteArrayOutputStream, Base64.DEFAULT)){
            // Instead of appending and wasting memory with a StringBuilder, just write the prefix
            // to the stream before the base64 icon data.
            byteArrayOutputStream.write("data:image/png;base64,".getBytes(StandardCharsets.US_ASCII));
            DownloadUtils.download(mLWJGL3ifyMod.iconUrl, base64OutputStream);
            return new String(byteArrayOutputStream.toByteArray(), StandardCharsets.US_ASCII);
        }catch (IOException e) {
            Log.w(TAG, "Failed to download base64 icon", e);
        }finally {
            try {
                byteArrayOutputStream.close();
            } catch (IOException e) {
                Log.wtf(TAG, "Failed to close a byte array stream??", e);
            }
        }
        return null;
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

    private static JMinecraftVersionList.Version installJson(String profileID, File modJar) throws IOException {
        try {
            JMinecraftVersionList.Version version = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(
                            ZipUtils.getEntryStream(
                                    new ZipFile(modJar),
                                    "me/eigenraven/lwjgl3ify/relauncher/version.json"
                            )
                    ),
                    JMinecraftVersionList.Version.class);
            version.id = profileID;
            Tools.write(Tools.DIR_HOME_VERSION + "/" + profileID + "/" + profileID + ".json", Tools.GLOBAL_GSON.toJson(version));
            return version;
        } catch (IOException e) {
            throw new IOException("Failed to install "+profileID+" json file.");
        }
    }

    private MinecraftProfile createProfile(String profileID) {
        MinecraftProfile LWJGL3ifyProfile = new MinecraftProfile();
        LWJGL3ifyProfile.lastVersionId = profileID;
        LWJGL3ifyProfile.name = "LWJGL3ify - "+ mLWJGL3ifyMod.versionName;
        // Allows for smooth-ish upgrades. LWJGL3ify thankfully follows semver.
        LWJGL3ifyProfile.gameDir = String.format("./custom_instances/LWJGL3ify_%s", mLWJGL3ifyMod.versionName);
        LWJGL3ifyProfile.icon = tryDownloadIcon();
        return LWJGL3ifyProfile;
    }

    private void createInstance(MinecraftProfile profile, File modJar) throws IOException {
        File modsDir = new File(Tools.DIR_GAME_HOME, profile.gameDir+"/mods");
        if (modsDir.isFile()) {
            if (!modsDir.delete()) {
                throw new IOException("Failed to delete file where directory should be: " + modsDir.getAbsolutePath());
            }
        }
        try {
            FileUtils.ensureDirectory(modsDir);
        } catch (IOException e) {
            throw new IOException("Failed to create folder " + modsDir.getAbsolutePath());
        }
        // Copy downloaded cached mod jar
        try (FileInputStream fis = new FileInputStream(modJar);
             FileOutputStream fos = new FileOutputStream(new File(modsDir, "lwjgl3ify-"+ mLWJGL3ifyMod.versionName+".jar"))) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        tryDownloadDeps(modsDir);
    }

    public void runCatching() throws IOException {
        // This cannot be allowed to match the mod.jar ID otherwise conflicts occur and GLFW input breaks
        String LWJGL3ifyProfileID = "1.7.10-LWJGL3ify-"+ mLWJGL3ifyMod.versionName;
        File modJar = tryDownloadModJar();
        if (!modJar.exists()) throw new IOException("Failed to download LWJGL3ify "+ mLWJGL3ifyMod.versionName);
        MinecraftProfile profile = createProfile(LWJGL3ifyProfileID);
        new MinecraftDownloader().start(mActivity, installJson(LWJGL3ifyProfileID, modJar), LWJGL3ifyProfileID, this);
        createInstance(profile, modJar);


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
