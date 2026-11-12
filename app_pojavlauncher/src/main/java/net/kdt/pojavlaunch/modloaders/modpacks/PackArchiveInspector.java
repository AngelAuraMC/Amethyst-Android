package net.kdt.pojavlaunch.modloaders.modpacks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Lightweight, non-extracting validation and metadata inspection for Minecraft packs. */
public final class PackArchiveInspector {
    public static final int RESOURCE_PACK = 0;
    public static final int SHADER_PACK = 1;
    private static final int MAX_METADATA_BYTES = 512 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 20000;

    private PackArchiveInspector() {}

    public static final class Info {
        public String title;
        public String detail;
        public String format;
        public String validationMessage;
        public boolean valid;

        Info(String title) {
            this.title = title;
            this.detail = "";
            this.format = "";
        }
    }

    public static Info inspect(File file, int type) {
        Info info = new Info(file == null ? "Unknown pack" : file.getName());
        if (file == null || !file.exists()) {
            info.validationMessage = "File does not exist";
            return info;
        }
        if (file.isDirectory()) {
            return inspectDirectory(file, type, info);
        }
        if (!file.isFile()) {
            info.validationMessage = "Not a regular file";
            return info;
        }
        String lower = file.getName().toLowerCase(Locale.ROOT);
        boolean disabled = lower.endsWith(".zip.disabled");
        if (!(lower.endsWith(".zip") || disabled)) {
            info.validationMessage = "Unsupported file type";
            return info;
        }
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int count = 0;
            String packMetaPath = null;
            String shaderPath = null;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ARCHIVE_ENTRIES) {
                    info.validationMessage = "Archive contains too many entries";
                    return info;
                }
                String path = entry.getName().replace('\\', '/');
                if (type == RESOURCE_PACK && !entry.isDirectory() &&
                        (path.equals("pack.mcmeta") || path.matches("^[^/]+/pack\\.mcmeta$"))) {
                    packMetaPath = path;
                }
                if (type == SHADER_PACK) {
                    String normalized = path.toLowerCase(Locale.ROOT);
                    if (normalized.startsWith("shaders/") || normalized.matches("^[^/]+/shaders/.*")) {
                        shaderPath = path;
                    } else if (normalized.equals("shaders") || normalized.equals("shaders.properties") ||
                            normalized.matches("^[^/]+/shaders(?:/.*)?$") ||
                            normalized.matches("^[^/]+/shaders\\.properties$")) {
                        shaderPath = path;
                    }
                }
            }
            if (type == RESOURCE_PACK) {
                if (packMetaPath == null) {
                    info.validationMessage = "pack.mcmeta was not found at the archive root or one folder below";
                    return info;
                }
                ZipEntry metadata = zip.getEntry(packMetaPath);
                if (metadata != null && metadata.getSize() > MAX_METADATA_BYTES) {
                    info.validationMessage = "pack.mcmeta is too large";
                    return info;
                }
                if (metadata != null) {
                    try (InputStream input = zip.getInputStream(metadata)) {
                        applyResourceMetadata(info, readLimited(input));
                    }
                }
                ZipEntry icon = zip.getEntry(packMetaPath.equals("pack.mcmeta") ? "pack.png" :
                        packMetaPath.substring(0, packMetaPath.length() - "pack.mcmeta".length()) + "pack.png");
                info.detail = append(info.detail, icon == null ? "No pack icon" : "Pack icon included");
                info.valid = true;
                info.validationMessage = "Resource pack archive validated";
            } else {
                if (shaderPath == null) {
                    info.validationMessage = "Shader archive does not contain a shaders folder";
                    return info;
                }
                info.detail = "Shader folder: " + firstSegment(shaderPath);
                info.valid = true;
                info.validationMessage = "Shader pack archive structure detected";
            }
        } catch (Exception error) {
            info.valid = false;
            info.validationMessage = "Cannot read archive: " + error.getClass().getSimpleName();
        }
        return info;
    }

    public static List<PackEntry> scan(File folder, int type) {
        ArrayList<PackEntry> result = new ArrayList<>();
        if (folder == null) return result;
        File disabledFolder = new File(folder, ".novacraft-disabled");
        scanChildren(folder, disabledFolder, type, false, result);
        if (disabledFolder.isDirectory()) scanChildren(disabledFolder, disabledFolder, type, true, result);
        java.util.Collections.sort(result, (a, b) -> a.info.title.compareToIgnoreCase(b.info.title));
        return result;
    }

    private static void scanChildren(File folder, File disabledFolder, int type, boolean disabled,
                                     List<PackEntry> result) {
        File[] children = folder.listFiles();
        if (children == null) return;
        for (File file : children) {
            if (file.equals(disabledFolder) || !file.exists()) continue;
            boolean fileDisabled = disabled;
            if (file.isFile()) {
                if (disabled) continue;
                String lower = file.getName().toLowerCase(Locale.ROOT);
                if (lower.endsWith(".zip.disabled")) fileDisabled = true;
                else if (!lower.endsWith(".zip")) continue;
            } else if (!file.isDirectory()) {
                continue;
            }
            Info info = inspect(file, type);
            if (info.valid) result.add(new PackEntry(file, info, !fileDisabled, file.isDirectory()));
        }
    }

    private static Info inspectDirectory(File directory, int type, Info info) {
        try {
            if (type == RESOURCE_PACK) {
                File metadata = new File(directory, "pack.mcmeta");
                if (!metadata.isFile()) {
                    info.validationMessage = "pack.mcmeta not found";
                    return info;
                }
                if (metadata.length() > MAX_METADATA_BYTES) {
                    info.validationMessage = "pack.mcmeta is too large";
                    return info;
                }
                try (InputStream input = new FileInputStream(metadata)) {
                    applyResourceMetadata(info, readLimited(input));
                }
                info.detail = append(info.detail, new File(directory, "pack.png").isFile()
                        ? "Pack icon included" : "No pack icon");
            } else {
                File shaders = new File(directory, "shaders");
                File properties = new File(directory, "shaders.properties");
                if (!shaders.isDirectory() && !properties.isFile()) {
                    info.validationMessage = "Shader folder was not found";
                    return info;
                }
                info.detail = shaders.isDirectory() ? "Shader folder detected" : "Shader properties detected";
            }
            info.valid = true;
            info.validationMessage = "Pack folder validated";
        } catch (Exception error) {
            info.valid = false;
            info.validationMessage = "Cannot inspect pack folder";
        }
        return info;
    }

    private static void applyResourceMetadata(Info info, String json) {
        JsonElement parsed = new JsonParser().parse(json);
        if (parsed == null || !parsed.isJsonObject()) throw new IllegalArgumentException("Invalid pack metadata");
        JsonObject root = parsed.getAsJsonObject();
        JsonObject pack = root.has("pack") && root.get("pack").isJsonObject()
                ? root.getAsJsonObject("pack") : null;
        if (pack == null) throw new IllegalArgumentException("Missing pack metadata object");
        if (pack.has("pack_format") && !pack.get("pack_format").isJsonNull()) {
            info.format = "Pack format " + pack.get("pack_format").getAsString();
        }
        if (pack.has("description") && !pack.get("description").isJsonNull()) {
            info.detail = append(info.detail, plainText(pack.get("description")));
        }
        info.valid = true;
        info.validationMessage = "pack.mcmeta validated";
    }

    private static String plainText(JsonElement element) {
        if (element == null || element.isJsonNull()) return "";
        if (element.isJsonPrimitive()) return element.getAsString();
        if (element.isJsonArray()) {
            StringBuilder result = new StringBuilder();
            for (JsonElement part : element.getAsJsonArray()) {
                String text = plainText(part);
                if (!text.isEmpty()) {
                    if (result.length() > 0) result.append(' ');
                    result.append(text);
                }
            }
            return result.toString();
        }
        if (element.isJsonObject() && element.getAsJsonObject().has("text")) {
            return plainText(element.getAsJsonObject().get("text"));
        }
        return "";
    }

    private static String readLimited(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_METADATA_BYTES) throw new IllegalArgumentException("Metadata is too large");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String firstSegment(String path) {
        int index = path.indexOf('/');
        return index < 0 ? path : path.substring(0, index);
    }

    private static String append(String first, String second) {
        if (second == null || second.trim().isEmpty()) return first == null ? "" : first;
        if (first == null || first.trim().isEmpty()) return second;
        return first + " • " + second;
    }

    public static final class PackEntry {
        public final File file;
        public final Info info;
        public final boolean available;
        public final boolean directory;

        PackEntry(File file, Info info, boolean available, boolean directory) {
            this.file = file;
            this.info = info;
            this.available = available;
            this.directory = directory;
        }
    }
}
