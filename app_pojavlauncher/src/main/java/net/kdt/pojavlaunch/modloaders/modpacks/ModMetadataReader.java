package net.kdt.pojavlaunch.modloaders.modpacks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads supported loader metadata without executing code from mod archives. */
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
                parseFabric(info, parseJson(zip.getInputStream(fabric)));
                return info;
            }

            ZipEntry quilt = zip.getEntry("quilt.mod.json");
            if (quilt != null) {
                parseQuilt(info, parseJson(zip.getInputStream(quilt)));
                return info;
            }

            boolean neoForge = zip.getEntry("META-INF/neoforge.mods.toml") != null;
            ZipEntry forge = zip.getEntry(neoForge ? "META-INF/neoforge.mods.toml" : "META-INF/mods.toml");
            if (forge != null) {
                parseForge(info, readLimited(zip.getInputStream(forge)), neoForge);
            }
        } catch (Exception ignored) {
            info.loader = "Unreadable JAR";
        }
        return info;
    }

    private static void parseFabric(Info info, JsonObject root) {
        info.loader = "Fabric";
        info.modId = string(root, "id");
        info.version = string(root, "version");
        setDisplayName(info, string(root, "name"));
        JsonObject depends = object(root, "depends");
        JsonObject recommends = object(root, "recommends");
        JsonObject suggests = object(root, "suggests");
        JsonObject breaks = object(root, "breaks");
        JsonObject conflicts = object(root, "conflicts");
        readDependencyMap(depends, info.requiredDependencies);
        readDependencyMap(recommends, info.optionalDependencies);
        readDependencyMap(suggests, info.optionalDependencies);
        readDependencyMap(breaks, info.breaks);
        readDependencyMap(conflicts, info.conflicts);
        info.minecraftRequirement = valueFor(depends, "minecraft");
        removePlatformDependencies(info);
    }

    private static void parseQuilt(Info info, JsonObject root) {
        JsonObject loader = object(root, "quilt_loader");
        info.loader = "Quilt";
        info.modId = string(loader, "id");
        info.version = string(loader, "version");
        setDisplayName(info, string(object(loader, "metadata"), "name"));
        JsonElement dependencies = loader == null ? null : loader.get("depends");
        if (dependencies != null && dependencies.isJsonArray()) {
            readQuiltDependencies(info, dependencies.getAsJsonArray(), true);
        }
        JsonElement breaks = loader == null ? null : loader.get("breaks");
        if (breaks != null && breaks.isJsonArray()) readQuiltDependencies(info, breaks.getAsJsonArray(), false);
        removePlatformDependencies(info);
    }

    private static void readQuiltDependencies(Info info, JsonArray dependencies, boolean dependencyList) {
        for (JsonElement element : dependencies) {
            if (!element.isJsonObject()) continue;
            JsonObject dependency = element.getAsJsonObject();
            String id = string(dependency, "id");
            if (id == null || id.isEmpty()) continue;
            String requirement = elementString(dependency.get("versions"));
            if ("minecraft".equalsIgnoreCase(id)) {
                if (requirement != null) info.minecraftRequirement = requirement;
                continue;
            }
            boolean optional = dependency.has("optional") && dependency.get("optional").getAsBoolean();
            if (!dependencyList) info.breaks.add(id);
            else if (optional) info.optionalDependencies.add(id);
            else info.requiredDependencies.add(id);
        }
    }

    private static void parseForge(Info info, String text, boolean neoForge) {
        info.loader = neoForge ? "NeoForge" : "Forge";
        Matcher modSection = Pattern.compile("(?ms)^\\s*\\[\\[mods\\]\\]\\s*(.*?)(?=^\\s*\\[\\[|\\z)").matcher(text);
        if (modSection.find()) {
            String section = modSection.group(1);
            info.modId = tomlValue(section, "modId");
            info.version = tomlValue(section, "version");
            setDisplayName(info, tomlValue(section, "displayName"));
        }

        Pattern dependencySection = Pattern.compile("(?ms)^\\s*\\[\\[dependencies\\.[^\\]]+\\]\\]\\s*(.*?)(?=^\\s*\\[\\[|\\z)");
        Matcher dependencyMatcher = dependencySection.matcher(text);
        while (dependencyMatcher.find()) {
            String section = dependencyMatcher.group(1);
            String id = tomlValue(section, "modId");
            if (id == null || id.isEmpty()) continue;
            String requirement = tomlValue(section, "versionRange");
            boolean mandatory = !"false".equalsIgnoreCase(tomlValue(section, "mandatory"));
            if ("minecraft".equalsIgnoreCase(id)) {
                if (requirement != null) info.minecraftRequirement = requirement;
            } else if (isPlatformDependency(id)) {
                // The selected loader itself is validated separately.
            } else if (mandatory) {
                info.requiredDependencies.add(id);
            } else {
                info.optionalDependencies.add(id);
            }
        }
    }

    private static String tomlValue(String section, String key) {
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*[\"']([^\"']*)[\"']").matcher(section);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static void readDependencyMap(JsonObject source, List<String> destination) {
        if (source == null) return;
        for (String key : source.keySet()) {
            if (!isPlatformDependency(key)) destination.add(key);
        }
    }

    private static String valueFor(JsonObject source, String key) {
        return source == null ? null : elementString(source.get(key));
    }

    private static void removePlatformDependencies(Info info) {
        removePlatformDependenciesFrom(info.requiredDependencies);
        removePlatformDependenciesFrom(info.optionalDependencies);
        removePlatformDependenciesFrom(info.breaks);
        removePlatformDependenciesFrom(info.conflicts);
    }

    private static void removePlatformDependenciesFrom(List<String> dependencies) {
        for (int index = dependencies.size() - 1; index >= 0; index--) {
            if (isPlatformDependency(dependencies.get(index))) dependencies.remove(index);
        }
    }

    public static boolean isPlatformDependency(String id) {
        if (id == null) return true;
        String value = id.toLowerCase(Locale.ROOT);
        return value.equals("minecraft") || value.equals("java") ||
                value.equals("fabricloader") || value.equals("fabric-loader") ||
                value.equals("quilt_loader") || value.equals("quilt-loader") ||
                value.equals("forge") || value.equals("neoforge") ||
                value.equals("neoforged") || value.equals("javafml");
    }

    private static void setDisplayName(Info info, String name) {
        if (name != null && !name.trim().isEmpty()) info.displayName = name;
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
        public String version;
        public String loader;
        public String minecraftRequirement;
        public final List<String> requiredDependencies = new ArrayList<>();
        public final List<String> optionalDependencies = new ArrayList<>();
        public final List<String> conflicts = new ArrayList<>();
        public final List<String> breaks = new ArrayList<>();

        public boolean hasLoaderMismatch(String activeLoader) {
            if (loader == null || "Unknown".equals(loader) || "Unreadable JAR".equals(loader)) return false;
            if (activeLoader == null || activeLoader.trim().isEmpty()) return true;
            return !loader.toLowerCase(Locale.ROOT).contains(activeLoader.toLowerCase(Locale.ROOT));
        }

        public String describe() {
            StringBuilder text = new StringBuilder(loader == null ? "Loader unknown" : loader);
            if (modId != null && !modId.isEmpty()) text.append(" • ").append(modId);
            if (version != null && !version.isEmpty()) text.append(" • ").append(version);
            if (minecraftRequirement != null && !minecraftRequirement.isEmpty()) text.append(" • MC ").append(minecraftRequirement);
            return text.toString();
        }
    }
}
