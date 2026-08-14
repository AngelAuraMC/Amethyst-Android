package net.kdt.pojavlaunch.modloaders.modpacks.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.zip.ZipFile;

public class ModpackInstaller {

    public static ModLoader installModpack(ModDetail modDetail, int selectedVersion, InstallFunction installFunction) throws IOException {
        String versionUrl = modDetail.versionUrls[selectedVersion];
        String versionHash = modDetail.versionHashes[selectedVersion];
        String modpackName = (modDetail.title.toLowerCase(Locale.ROOT) + " " + modDetail.versionNames[selectedVersion])
                .trim().replaceAll("[\\\\/:*?\"<>| \\t\\n]", "_" );
        if (versionHash != null) {
            modpackName += "_" + versionHash;
        }
        if (modpackName.length() > 255){
            modpackName = modpackName.substring(0,255);
        }

        // Build a new minecraft instance, folder first

        // Get the modpack file
        File modpackFile = new File(Tools.DIR_CACHE, modpackName + ".cf"); // Cache File
        ModLoader modLoaderInfo;
        try {
            byte[] downloadBuffer = new byte[8192];
            DownloadUtils.ensureSha1(modpackFile, versionHash, (Callable<Void>) () -> {
                DownloadUtils.downloadFileMonitored(versionUrl, modpackFile, downloadBuffer,
                        new DownloaderProgressWrapper(R.string.modpack_download_downloading_metadata,
                                ProgressLayout.INSTALL_MODPACK));
                return null;
            });

            // Install the modpack
            modLoaderInfo = installFunction.installModpack(modpackFile, new File(Tools.DIR_GAME_HOME, "custom_instances/"+modpackName));

        } finally {
            //noinspection ResultOfMethodCallIgnored It's cache, who cares
            modpackFile.delete();
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
        if(modLoaderInfo == null) {
            return null;
        }

        // Create the instance
        MinecraftProfile profile = new MinecraftProfile();
        profile.gameDir = "./custom_instances/" + modpackName;
        profile.name = modDetail.title;
        profile.lastVersionId = modLoaderInfo.getVersionId();
        profile.icon = ModIconCache.getBase64Image(modDetail.getIconCacheTag());


        LauncherProfiles.mainProfileJson.profiles.put(modpackName, profile);
        LauncherProfiles.write();

        return modLoaderInfo;
    }

    public static ModLoader importModpack(File modpackFile, int apiSource, InstallFunction installFunction) throws IOException, NoSuchAlgorithmException {
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 1, R.string.import_modpack_start);
        // modpackFile is deleted in LauncherActivity, no need to delete here.
        if (modpackFile == null) throw new IOException("Can't open modpack file, try again?");
        String manifestFileName;
        switch (apiSource) {
            case Constants.SOURCE_CURSEFORGE:
                manifestFileName = "manifest.json";
                break;
            case Constants.SOURCE_MODRINTH:
                manifestFileName = "modrinth.index.json";
                break;
            default:
                throw new UnsupportedOperationException("Unknown API source: " + apiSource);
        }
        // Read Manifest JSON
        JsonObject manifestFile = JsonParser.parseString(Tools.read(ZipUtils.getEntryStream(
                    new ZipFile(modpackFile), manifestFileName))).getAsJsonObject();

        // Parse the JSON to prepare for instance creation
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 1, R.string.import_modpack_json);
        String modpackName = "";
        String modpackVersion = "";
        String modpackMcVersion = "";

        switch (apiSource) {
            case Constants.SOURCE_CURSEFORGE:
                try {
                    modpackName = manifestFile.get("name").getAsString();
                    modpackVersion = manifestFile.get("version").getAsString();
                    modpackMcVersion = manifestFile.get("minecraft").getAsJsonObject().get("version").getAsString();
                } catch (RuntimeException ignored) {}
                break;
            case Constants.SOURCE_MODRINTH:
                try {
                    modpackName = manifestFile.get("name").getAsString();
                    modpackVersion = manifestFile.get("versionId").getAsString();
                    modpackMcVersion = manifestFile.get("dependencies").getAsJsonObject().get("minecraft").getAsString();
                } catch (RuntimeException ignored) {}
                break;
            default:
                throw new UnsupportedOperationException("Unknown API source: " + apiSource);
        }
        if(modpackName.isBlank() || modpackVersion.isBlank() || modpackMcVersion.isBlank()) throw new IOException("Corrupt Modpack manifest file.");

        // Hash the ZIP File, can't use getSha1 cause progress bar
        MessageDigest algorithm = MessageDigest.getInstance("SHA-1");
        //noinspection IOStreamConstructor It will reccomend you use an API26 function like a dumb
        DigestInputStream hashingStream = new DigestInputStream(new FileInputStream(modpackFile), algorithm);
        long fileSize = modpackFile.length();
        long readSize = 0;
        byte[] buffer = new byte[262144];
        while (true) {
            int n = hashingStream.read(buffer);
            if (n == -1) break;
            readSize += n;
            String readMB = fileSize > 0 ? String.format(Locale.US, "%.2f", readSize / (1024.0 * 1024.0)) : "unknown";
            String totalMB = fileSize > 0 ? String.format(Locale.US, "%.2f",fileSize / (1024.0 * 1024.0)) : "unknown";
            int progress = fileSize > 0 ? (int) ((readSize * 100L) / fileSize) : 0;
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, progress, R.string.import_modpack_hash, readMB, totalMB);
        }
        hashingStream.close();
        byte[] digest = algorithm.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        String hash = sb.toString();
        String profileFolderName = String.join(" ", modpackName, modpackVersion, "for", modpackMcVersion, hash);
        profileFolderName = profileFolderName.trim().replaceAll("[\\\\/:*?\"<>| \\t\\n]", "_");

        // Install the actual pack into custom_instances
        ModLoader modLoaderInfo = installFunction.installModpack(modpackFile, new File(Tools.DIR_GAME_HOME, "custom_instances/"+profileFolderName));
        ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);

        // Create the instance (We don't have a picture guys)
        MinecraftProfile profile = MinecraftProfile.getDefaultProfile();
        profile.gameDir = "./custom_instances/" + profileFolderName;
        profile.name = modpackName;
        if (!modpackMcVersion.isBlank()) profile.lastVersionId = modpackMcVersion;
        if (modLoaderInfo != null && modLoaderInfo.getVersionId() != null)
            profile.lastVersionId = modLoaderInfo.getVersionId();
        LauncherProfiles.mainProfileJson.profiles.put(profileFolderName, profile);
        LauncherProfiles.write();

        return modLoaderInfo;
}

interface InstallFunction {
        ModLoader installModpack(File modpackFile, File instanceDestination) throws IOException;
    }
}
