package net.kdt.pojavlaunch.modloaders.modpacks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads only well-known metadata files from a mod JAR. Unknown metadata is reported as unknown, not guessed. */
public final class ModMetadataReader {
    private static final int MAX_METADATA_BYTES = 512 * 1024;

    private ModMetadataReader() {}

    public static Info read(String fallbackName, java.io.File jarFile) {
        Info info = new Info();
        info.displayName = fallbackName;
        info.loader = "Unknown";
        try (ZipFile zip = new ZipFile(jarFile)) {
            ZipEntry fabric = zip.getEntry("fabric.mod.json");
            if (fabric != null) {
                JsonObject root = parseJson(zip.getInputStream(fabric));
                info.loader = "Fabric";
                info.modId = string(root, "id");
                String name = string(root, "name");
                if (name != null && !name.isEmpty()) info.displayName = name;
                JsonObject depends = object(root, "depends");
                info.minecraftRequirement = elementString(depends == null ? null : depends.get("minecraft"));
                return info;
            }

            ZipEntry quilt = zip.getEntry("quilt.mod.json");
            if (quilt != null) {
                JsonObject root = parseJson(zip.getInputStream(quilt));
                JsonObject loader = object(root, "quilt_loader");
                info.loader = "Quilt";
                info.modId = string(loader, "id");
                JsonObject metadata = object(loader, "metadata");
                String name = string(metadata, "name");
                if (name != null && !name.isEmpty()) info.displayName = name;
                info.minecraftRequirement = findQuiltMinecraftRequirement(loader);
                return info;
            }

            boolean neoForge = zip.getEntry("META-INF/neoforge.mods.toml") != null;
            ZipEntry forge = zip.getEntry(neoForge ? "META-INF/neoforge.mods.toml" : "META-INF/mods.toml");
            if (forge != null) {
                String text = readLimited(zip.getInputStream(forge));
                info.loader = neoForge ? "NeoForge" : "Forge";
                Matcher idMatcher = Pattern.compile("(?m)^\\s*modId\\s*=\\s*[\"']([^\"']+)[\"']").matcher(text);
                if (idMatcher.find()) info.modId = idMatcher.group(1);
                Matcher mcMatcher = Pattern.compile("modId\\s*=\\s*[\"']minecraft[\"'][\\s\\S]{0,600}?versionRange\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(text);
                if (mcMatcher.find()) info.minecraftRequirement = mcMatcher.group(1);
            }
        } catch (Exception ignored) {
            info.loader = "Unreadable JAR";
        }
        return info;
    }

    private static String findQuiltMinecraftRequirement(JsonObject loader) {
        try {
            JsonElement dependencies = loader.get("depends");
            if (dependencies == null || !dependencies.isJsonArray()) return null;
            for (JsonElement element : dependencies.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject dependency = element.getAsJsonObject();
                if ("minecraft".equalsIgnoreCase(string(dependency, "id"))) {
                    return elementString(dependency.get("versions"));
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static JsonObject parseJson(InputStream input) throws IOException {
        try (InputStream stream = input) {
            String json = readLimited(stream);
            JsonElement parsed = new JsonParser().parse(json);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        }
    }

    private static String readLimited(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_METADATA_BYTES) throw new IOException("Mod metadata is unexpectedly large");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) return null;
        return parent.getAsJsonObject(key);
    }

    private static String string(JsonObject parent, String key) {
        return parent == null ? null : elementString(parent.get(key));
    }

    private static String elementString(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        try {
            if (element.isJsonPrimitive()) return element.getAsString();
            return element.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static final class Info {
        public String displayName;
        public String modId;
        public String loader;
        public String minecraftRequirement;

        public boolean hasLoaderMismatch(String activeLoader) {
            if (activeLoader == null || loader == null || "Unknown".equals(loader) || "Unreadable JAR".equals(loader)) return false;
            return !loader.toLowerCase(Locale.ROOT).contains(activeLoader.toLowerCase(Locale.ROOT));
        }

        public String describe() {
            StringBuilder text = new StringBuilder(loader == null ? "Loader unknown" : loader);
            if (modId != null && !modId.isEmpty()) text.append(" • ").append(modId);
            if (minecraftRequirement != null && !minecraftRequirement.isEmpty()) text.append(" • Minecraft ").append(minecraftRequirement);
            return text.toString();
        }
    }
}
