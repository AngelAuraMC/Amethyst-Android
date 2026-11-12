package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.net.Uri;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/** Downloads one verified mod JAR into the active Minecraft profile's mods directory. */
public final class SingleModInstaller {
    private SingleModInstaller() {}

    public static File install(ModDetail detail, int selectedVersion) throws IOException {
        if (detail == null || detail.isModpack) throw new IOException("This is not a single-mod download.");
        if (selectedVersion < 0 || selectedVersion >= detail.versionUrls.length) {
            throw new IOException("The selected mod version is no longer available.");
        }

        String url = detail.versionUrls[selectedVersion];
        if (url == null || url.trim().isEmpty() || !"https".equalsIgnoreCase(Uri.parse(url).getScheme())) {
            throw new IOException("The mod provider did not supply a safe direct HTTPS download.");
        }

        File gameDirectory;
        try {
            gameDirectory = Tools.getGameDirPath(LauncherProfiles.getCurrentProfile());
        } catch (RuntimeException error) {
            throw new IOException("Unable to resolve the active Minecraft profile folder.", error);
        }
        File modsDirectory = new File(gameDirectory, "mods");
        FileUtils.ensureDirectory(modsDirectory);

        String fileName = Uri.parse(url).getLastPathSegment();
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = safe(detail.id) + "-" + safe(detail.versionIds[selectedVersion]) + ".jar";
        }
        fileName = safe(fileName);
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) fileName += ".jar";

        File destination = new File(modsDirectory, fileName);
        if (destination.exists()) {
            String stem = fileName.substring(0, fileName.length() - 4);
            String projectPart = safe(detail.id);
            String versionPart = safe(detail.versionIds[selectedVersion]);
            destination = new File(modsDirectory, stem + "-" + projectPart + "-" + versionPart + ".jar");
            int suffix = 2;
            while (destination.exists()) {
                destination = new File(modsDirectory, stem + "-" + projectPart + "-" + versionPart + "-" + suffix + ".jar");
                suffix++;
            }
        }

        ModDownloader downloader = new ModDownloader(modsDirectory, true);
        downloader.submitDownload(1, destination.getName(), detail.versionHashes[selectedVersion], url);
        downloader.awaitFinish((current, maximum) -> { });

        if (!destination.isFile() || destination.length() == 0) {
            throw new IOException("The download finished without creating a valid mod file.");
        }
        return destination;
    }

    private static String safe(String value) {
        if (value == null) return "mod";
        String safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
        safe = safe.replace("..", "_");
        return safe.isEmpty() ? "mod" : safe;
    }
}
