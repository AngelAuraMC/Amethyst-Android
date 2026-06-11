package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.app.Activity;
import android.net.Uri;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    public static ModLoader importModpack(Activity activity, Uri zipUri, InstallFunction installFunction) throws IOException, NoSuchAlgorithmException {
        String modrinthPackInfoFileName = "modrinth.index.json";
        String curseforgePackInfoFileName = "manifest.json";
        InputStream inputStream = null;
        inputStream = activity.getContentResolver().openInputStream(zipUri);
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        ZipEntry zipEntry;
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            boolean isModrinth = zipEntry.getName().equals(modrinthPackInfoFileName);
            boolean isCurseforge = zipEntry.getName().equals(curseforgePackInfoFileName);
            if (!(isModrinth || isCurseforge)) continue;
            // Read Manifest JSON
            BufferedReader reader = new BufferedReader(new InputStreamReader(zipInputStream));
            String str;
            StringBuilder jsonString = new StringBuilder();
            while ((str = reader.readLine()) != null) {
                jsonString.append(str).append("\n");
            }
            zipInputStream.close();

            // Hash the ZIP File
            inputStream = activity.getContentResolver().openInputStream(zipUri);
            MessageDigest algorithm = MessageDigest.getInstance("SHA-1");
            DigestInputStream hashingStream = new DigestInputStream(inputStream, algorithm);

            byte[] buffer = new byte[8192];
            while (hashingStream.read(buffer) != -1) {} // just read to update the digest
            hashingStream.close();
            byte[] digest = algorithm.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            String hash = sb.toString();

            // Parse the JSON to prepare for instance creation
            JsonObject packInfoJson = JsonParser.parseString(jsonString.toString()).getAsJsonObject();
            String modpackName;
            if(isModrinth){
                // Added a for because there is an awkward __ that I can't be bothered to fix
                // FO only deduplication be like:
                modpackName = (packInfoJson.get("name").getAsString().toLowerCase(Locale.ROOT) +
                        packInfoJson.get("versionId") + "for" +
                        packInfoJson.get("dependencies").getAsJsonObject().get("minecraft"));
            } else {
                modpackName = (packInfoJson.get("name").getAsString().toLowerCase(Locale.ROOT) +
                        packInfoJson.get("version") + "for" +
                        packInfoJson.get("minecraft").getAsJsonObject().get("version"));
            }
            modpackName = modpackName.trim().replaceAll("[\\\\/:*?\"<>| \\t\\n]", "_");
            modpackName = modpackName + hash;

            // Copy ZIP file to cache
            if(modpackName == null) throw new IOException("Corrupt Modpack manifest file.");
            File modpackFile = null;
            modpackFile = new File(Tools.DIR_CACHE, modpackName + ".cf");
            inputStream = activity.getContentResolver().openInputStream(zipUri);
            FileOutputStream output = new FileOutputStream(modpackFile);
            byte[] b = new byte[4 * 1024];
            int read;
            while ((read = inputStream.read(b)) != -1) {
                output.write(b, 0, read);
            }
            output.flush();

            // Install the actual pack into custom_instances
            ModLoader modLoaderInfo = installFunction.installModpack(modpackFile, new File(Tools.DIR_GAME_HOME, "custom_instances/"+modpackName));
            // We have to do this because installModpack doesn't clean up after itself
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            modpackFile.delete();
            if(modLoaderInfo == null) {
                return null;
            }

            // Create the instance (We don't have a picture guys)
            MinecraftProfile profile = new MinecraftProfile();
            profile.gameDir = "./custom_instances/" + modpackName;
            profile.name = packInfoJson.get("name").getAsString();
            profile.lastVersionId = modLoaderInfo.getVersionId();
            LauncherProfiles.mainProfileJson.profiles.put(modpackName, profile);
            LauncherProfiles.write();

            return modLoaderInfo;
        }
        throw new IOException("Can't find manifest file in modpack provided");
}

interface InstallFunction {
        ModLoader installModpack(File modpackFile, File instanceDestination) throws IOException;
    }
}
