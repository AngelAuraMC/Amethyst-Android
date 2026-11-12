package net.kdt.pojavlaunch.backups;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Safe local NovaCraft backup archive format.
 *
 * Login token/account files are intentionally excluded. Archives contain selected
 * game data categories plus a small manifest, and restores never overwrite files.
 */
public final class BackupArchiveUtils {
    public static final String WORLDS = "worlds";
    public static final String MODS = "mods";
    public static final String RESOURCE_PACKS = "resourcepacks";
    public static final String SHADER_PACKS = "shaderpacks";
    public static final String PROFILE_SETTINGS = "profile-settings";
    public static final String SKINS = "skins";

    private static final String MANIFEST = "novacraft-manifest.txt";
    private static final String MAGIC = "NOVACRAFT_BACKUP_V1";
    private static final long MAX_ARCHIVE_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final long MAX_RESTORE_BYTES = 4L * 1024L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 50000;
    private static final byte[] BUFFER = new byte[32768];

    private BackupArchiveUtils() {}

    public static void createBackup(File gameDirectory, File skinDirectory, String profileName,
                                    Set<String> selectedCategories, File outputFile) throws IOException {
        if (gameDirectory == null || skinDirectory == null || outputFile == null) {
            throw new IOException("Missing backup path.");
        }
        if (selectedCategories == null || selectedCategories.isEmpty()) {
            throw new IOException("Select at least one backup category.");
        }
        File absoluteOutput = outputFile.getAbsoluteFile();
        File parent = absoluteOutput.getParentFile();
        if (parent == null) throw new IOException("Invalid backup destination.");
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create backup directory.");
        }

        File temporary = new File(parent, absoluteOutput.getName() + ".partial");
        if (temporary.exists() && !temporary.delete()) throw new IOException("Unable to clear partial backup.");

        long entries = 0;
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))) {
            zip.setLevel(6);
            StringBuilder manifest = new StringBuilder(MAGIC).append("\n")
                    .append("created=").append(System.currentTimeMillis()).append("\n")
                    .append("profile=").append(safeManifestValue(profileName)).append("\n")
                    .append("categories=");
            boolean first = true;
            for (String category : selectedCategories) {
                if (!isKnownCategory(category)) continue;
                if (!first) manifest.append(",");
                manifest.append(category);
                first = false;
            }
            manifest.append("\naccount_tokens=excluded\n");
            putBytes(zip, MANIFEST, manifest.toString().getBytes("UTF-8"));

            if (selectedCategories.contains(WORLDS)) {
                File saves = new File(gameDirectory, "saves");
                File[] worlds = saves.listFiles();
                if (worlds != null) {
                    Arrays.sort(worlds, (a,b) -> a.getName().compareToIgnoreCase(b.getName()));
                    for (File world : worlds) {
                        if (world.isDirectory() && new File(world, "level.dat").isFile()) {
                            entries += addTree(zip, world, world, "worlds/" + world.getName() + "/");
                        }
                    }
                }
            }
            if (selectedCategories.contains(MODS)) {
                entries += addDirectory(zip, new File(gameDirectory, "mods"), "mods/");
            }
            if (selectedCategories.contains(RESOURCE_PACKS)) {
                entries += addDirectory(zip, new File(gameDirectory, "resourcepacks"), "resourcepacks/");
            }
            if (selectedCategories.contains(SHADER_PACKS)) {
                entries += addDirectory(zip, new File(gameDirectory, "shaderpacks"), "shaderpacks/");
            }
            if (selectedCategories.contains(PROFILE_SETTINGS)) {
                entries += addProfileSettings(zip, gameDirectory);
            }
            if (selectedCategories.contains(SKINS)) {
                entries += addDirectory(zip, skinDirectory, "skins/");
            }
        } catch (Exception error) {
            temporary.delete();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Unable to create archive.", error);
        }

        if (temporary.length() <= 0 || temporary.length() > MAX_ARCHIVE_BYTES) {
            temporary.delete();
            throw new IOException("Backup archive is empty or exceeds the 2 GB safety limit.");
        }
        if (entries == 0) {
            temporary.delete();
            throw new IOException("The selected categories contain no files to back up.");
        }
        // Never silently replace an earlier backup if two snapshots share a timestamp.
        File finalOutput = absoluteOutput;
        if (finalOutput.exists()) {
            String name = absoluteOutput.getName();
            int dot = name.lastIndexOf('.');
            String baseName = dot > 0 ? name.substring(0, dot) : name;
            String extension = dot > 0 ? name.substring(dot) : "";
            int index = 2;
            do {
                finalOutput = new File(parent, baseName + "_" + index + extension);
                index++;
            } while (finalOutput.exists());
        }
        if (!temporary.renameTo(finalOutput)) {
            temporary.delete();
            throw new IOException("Unable to finish backup archive.");
        }
    }

    private static long addProfileSettings(ZipOutputStream zip, File gameDirectory) throws IOException {
        long count = 0;
        String[] files = {"options.txt", "servers.dat", "optionsof.txt", "optionsshaders.txt", "optionschl.txt"};
        for (String name : files) {
            File file = new File(gameDirectory, name);
            if (file.isFile()) {
                addTree(zip, gameDirectory, file, "profile-settings/");
                count++;
            }
        }
        String[] directories = {"config", "defaultconfigs"};
        for (String name : directories) {
            File directory = new File(gameDirectory, name);
            if (directory.isDirectory()) count += addTree(zip, directory, directory, "profile-settings/" + name + "/");
        }
        return count;
    }

    private static long addDirectory(ZipOutputStream zip, File directory, String prefix) throws IOException {
        if (directory == null || !directory.isDirectory()) return 0;
        return addTree(zip, directory, directory, prefix);
    }

    private static long addTree(ZipOutputStream zip, File root, File current, String prefix) throws IOException {
        if (root == null || current == null || !current.exists()) return 0;
        String rootCanonical = root.getCanonicalPath();
        String currentCanonical = current.getCanonicalPath();
        if (!currentCanonical.equals(rootCanonical)
                && !currentCanonical.startsWith(rootCanonical + File.separator)) return 0;
        if (!currentCanonical.equals(current.getAbsolutePath())) return 0; // Do not follow symbolic links.
        if (current.isDirectory()) {
            File[] children = current.listFiles();
            if (children == null) return 0;
            Arrays.sort(children, (a,b) -> a.getName().compareToIgnoreCase(b.getName()));
            long count = 0;
            for (File child : children) {
                count += addTree(zip, root, child, prefix);
            }
            return count;
        }

        String relative = root.toURI().relativize(current.toURI()).getPath();
        if (relative == null || relative.isEmpty()) relative = current.getName();
        String entryName = prefix + relative;
        validateEntryName(entryName);
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(current.lastModified());
        zip.putNextEntry(entry);
        try (InputStream input = new BufferedInputStream(new FileInputStream(current))) {
            int read;
            while ((read = input.read(BUFFER)) != -1) zip.write(BUFFER, 0, read);
        }
        zip.closeEntry();
        return 1;
    }

    private static void putBytes(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    public static void validateBackup(File archive) throws IOException {
        if (archive == null || !archive.isFile() || archive.length() <= 0 || archive.length() > MAX_ARCHIVE_BYTES) {
            throw new IOException("Archive is missing or too large.");
        }
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry manifest = zip.getEntry(MANIFEST);
            if (manifest == null || manifest.isDirectory()) throw new IOException("Missing NovaCraft backup manifest.");
            String manifestText = readSmallText(zip.getInputStream(manifest));
            if (!manifestText.startsWith(MAGIC + "\n")) throw new IOException("Unsupported backup archive version.");

            int count = 0;
            boolean hasContent = false;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ENTRIES) throw new IOException("Archive contains too many entries.");
                String name = entry.getName();
                if (MANIFEST.equals(name)) continue;
                validateEntryName(name);
                String category = firstSegment(name);
                if (!isKnownCategory(category)) throw new IOException("Unknown archive category.");
                if (!entry.isDirectory()) {
                    if (entry.getSize() < 0 || entry.getSize() > MAX_RESTORE_BYTES) {
                        throw new IOException("Invalid archive entry size.");
                    }
                    hasContent = true;
                }
            }
            if (!hasContent) throw new IOException("Backup archive contains no payload files.");
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid backup archive.", error);
        }
    }

    private static String readSmallText(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > 16384) throw new IOException("Manifest is too large.");
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), "UTF-8");
        }
    }


    /**
     * Fully reads every payload entry before a backup is trusted for restore.
     * This catches corrupt ZIP entries and CRC failures, enforces the extraction
     * ceiling, and returns a SHA-256 fingerprint of the ZIP bytes.
     */
    public static VerificationResult verifyBackup(File archive) throws IOException {
        validateBackup(archive);
        int payloadEntries = 0;
        long totalBytes = 0;
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buffer = new byte[32768];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (MANIFEST.equals(entry.getName())) continue;
                if (++payloadEntries > MAX_ENTRIES) throw new IOException("Archive contains too many entries.");
                CRC32 crc = new CRC32();
                long entryBytes = 0;
                try (InputStream input = new BufferedInputStream(zip.getInputStream(entry))) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        entryBytes += read;
                        totalBytes += read;
                        if (totalBytes > MAX_RESTORE_BYTES) throw new IOException("Archive exceeds the 4 GB extraction safety limit.");
                        crc.update(buffer, 0, read);
                    }
                }
                if (entry.getSize() != entryBytes) throw new IOException("Archive entry size does not match its contents.");
                if (entry.getCrc() >= 0 && entry.getCrc() != crc.getValue()) {
                    throw new IOException("Archive entry failed its CRC integrity check.");
                }
            }
        }
        return new VerificationResult(payloadEntries, totalBytes, sha256(archive));
    }

    private static String sha256(File file) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is unavailable on this device.", error);
        }
        byte[] buffer = new byte[32768];
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(String.format(Locale.US, "%02x", value & 0xff));
        return result.toString();
    }

    public static RestoreResult restoreBackup(File archive, File gameDirectory, File skinDirectory) throws IOException {
        validateBackup(archive);
        if (gameDirectory == null || skinDirectory == null) throw new IOException("Missing restore destination.");
        if (!gameDirectory.exists() && !gameDirectory.mkdirs()) throw new IOException("Unable to create game directory.");
        if (!skinDirectory.exists() && !skinDirectory.mkdirs()) throw new IOException("Unable to create skin library folder.");

        int restored = 0;
        int skipped = 0;
        long totalBytes = 0;
        String suffix = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        Map<String, File> categoryRoots = new HashMap<>();
        Map<String, File> worldRoots = new HashMap<>();
        Map<String, File> skinRoots = new HashMap<>();

        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (MANIFEST.equals(name)) continue;
                if (entry.isDirectory()) {
                    try {
                        File directory = resolveRestoreTarget(name, gameDirectory, skinDirectory,
                                categoryRoots, worldRoots, skinRoots, suffix, true);
                        if (directory != null && !directory.exists() && !directory.mkdirs()) skipped++;
                    } catch (IOException error) {
                        skipped++;
                    }
                    continue;
                }

                try {
                    File target = resolveRestoreTarget(name, gameDirectory, skinDirectory,
                            categoryRoots, worldRoots, skinRoots, suffix, false);
                    if (target == null) {
                        skipped++;
                        continue;
                    }
                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        skipped++;
                        continue;
                    }
                    if (target.exists()) target = uniqueSibling(target, suffix);
                    long fileBytes = 0;
                    try (InputStream input = new BufferedInputStream(zip.getInputStream(entry));
                         FileOutputStream output = new FileOutputStream(target)) {
                        int read;
                        while ((read = input.read(BUFFER)) != -1) {
                            fileBytes += read;
                            totalBytes += read;
                            if (totalBytes > MAX_RESTORE_BYTES) throw new IOException("Restore exceeds the 4 GB extraction safety limit.");
                            output.write(BUFFER, 0, read);
                        }
                    }
                    restored++;
                } catch (IOException | RuntimeException error) {
                    skipped++;
                    if (totalBytes > MAX_RESTORE_BYTES) throw new IOException("Restore exceeds the 4 GB extraction safety limit.", error);
                }
            }
        }
        return new RestoreResult(restored, skipped);
    }

    private static File resolveRestoreTarget(String entryName, File gameDirectory, File skinDirectory,
                                             Map<String, File> categoryRoots,
                                             Map<String, File> worldRoots,
                                             Map<String, File> skinRoots,
                                             String suffix, boolean directoryEntry) throws IOException {
        validateEntryName(entryName);
        String category = firstSegment(entryName);
        String relative = entryName.substring(category.length() + 1);
        if (relative.isEmpty()) return null;

        if (WORLDS.equals(category)) {
            return resolveUniqueRootEntry(relative, new File(gameDirectory, "saves"),
                    worldRoots, "worlds", suffix, directoryEntry);
        }
        if (MODS.equals(category) || RESOURCE_PACKS.equals(category) || SHADER_PACKS.equals(category)) {
            String folderName = MODS.equals(category) ? "mods" :
                    RESOURCE_PACKS.equals(category) ? "resourcepacks" : "shaderpacks";
            return resolveUniqueRootEntry(relative, new File(gameDirectory, folderName),
                    categoryRoots, category, suffix, directoryEntry);
        }
        if (SKINS.equals(category)) {
            return resolveUniqueRootEntry(relative, skinDirectory, skinRoots, category, suffix, directoryEntry);
        }
        if (PROFILE_SETTINGS.equals(category)) {
            File target = new File(gameDirectory, relative);
            if (!isInside(gameDirectory, target)) throw new IOException("Invalid profile settings path.");
            if (directoryEntry) return target;
            if (target.exists()) target = uniqueSibling(target, suffix);
            return target;
        }
        return null;
    }

    private static File resolveUniqueRootEntry(String relative, File destinationRoot,
                                               Map<String, File> rootMap, String mapPrefix,
                                               String suffix, boolean directoryEntry) throws IOException {
        String[] parts = relative.split("/");
        if (parts.length == 0 || parts[0].isEmpty()) return null;
        String key = mapPrefix + ":" + parts[0];
        File chosenRoot = rootMap.get(key);
        if (chosenRoot == null) {
            if (!destinationRoot.exists() && !destinationRoot.mkdirs()) throw new IOException("Unable to create restore directory.");
            chosenRoot = new File(destinationRoot, parts[0]);
            if (chosenRoot.exists()) chosenRoot = uniqueSibling(chosenRoot, suffix);
            if (!isInside(destinationRoot, chosenRoot)) throw new IOException("Invalid restore path.");
            rootMap.put(key, chosenRoot);
        }
        if (parts.length == 1) return chosenRoot;
        StringBuilder remainder = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (i > 1) remainder.append('/');
            remainder.append(parts[i]);
        }
        File target = new File(chosenRoot, remainder.toString());
        if (!isInside(destinationRoot, target)) throw new IOException("Invalid restore path.");
        return target;
    }

    private static boolean isInside(File root, File candidate) throws IOException {
        String rootPath = root.getCanonicalPath();
        String candidatePath = candidate.getCanonicalPath();
        return candidatePath.equals(rootPath) || candidatePath.startsWith(rootPath + File.separator);
    }

    private static File uniqueSibling(File source, String suffix) {
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        File parent = source.getParentFile();
        File candidate = new File(parent, base + "_restored_" + suffix + ext);
        int index = 2;
        while (candidate.exists()) {
            candidate = new File(parent, base + "_restored_" + suffix + "_" + index + ext);
            index++;
        }
        return candidate;
    }

    private static String firstSegment(String name) {
        int slash = name.indexOf('/');
        return slash < 0 ? name : name.substring(0, slash);
    }

    private static void validateEntryName(String name) throws IOException {
        if (name == null || name.isEmpty() || name.startsWith("/") || name.indexOf('\\') >= 0) {
            throw new IOException("Invalid archive entry path.");
        }
        String[] segments = name.split("/");
        for (String segment : segments) {
            if ("..".equals(segment) || ".".equals(segment) || segment.isEmpty()) {
                // A final empty segment is only valid for a directory entry.
                if (segment.isEmpty() && name.endsWith("/") && segment.equals(segments[segments.length - 1])) continue;
                throw new IOException("Unsafe archive entry path.");
            }
        }
    }

    private static boolean isKnownCategory(String category) {
        return WORLDS.equals(category) || MODS.equals(category)
                || RESOURCE_PACKS.equals(category) || SHADER_PACKS.equals(category)
                || PROFILE_SETTINGS.equals(category) || SKINS.equals(category);
    }

    private static String safeManifestValue(String value) {
        if (value == null) return "unknown";
        return value.replace("\n", " ").replace("\r", " ").replace("=", " ");
    }

    public static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        if (bytes >= 1024L * 1024L) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.0f KB", Math.max(1.0, bytes / 1024.0));
    }


    public static class VerificationResult {
        public final int payloadEntries;
        public final long uncompressedBytes;
        public final String sha256;
        VerificationResult(int payloadEntries, long uncompressedBytes, String sha256) {
            this.payloadEntries=payloadEntries;
            this.uncompressedBytes=uncompressedBytes;
            this.sha256=sha256;
        }
    }

    public static class RestoreResult {
        public final int restoredFiles;
        public final int skippedEntries;
        RestoreResult(int restoredFiles, int skippedEntries) {
            this.restoredFiles = restoredFiles;
            this.skippedEntries = skippedEntries;
        }
    }
}
