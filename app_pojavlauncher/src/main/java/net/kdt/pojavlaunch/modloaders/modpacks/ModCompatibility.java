package net.kdt.pojavlaunch.modloaders.modpacks;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

/** Small helpers for matching mod metadata to the currently selected launcher profile. */
public final class ModCompatibility {
    private static final Pattern MC_VERSION = Pattern.compile("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?");

    private ModCompatibility() {}

    public static String detectLoader(MinecraftProfile profile) {
        if (profile == null || profile.lastVersionId == null) return null;
        String id = profile.lastVersionId.toLowerCase(Locale.ROOT);
        if (id.contains("neoforge")) return "neoforge";
        if (id.contains("fabric")) return "fabric";
        if (id.contains("quilt")) return "quilt";
        if (id.contains("forge")) return "forge";
        return null;
    }

    public static String detectMinecraftVersion(String versionId) {
        if (versionId == null) return null;
        Matcher matcher = MC_VERSION.matcher(versionId);
        String first = null;
        String last = null;
        while (matcher.find()) {
            if (first == null) first = matcher.group();
            last = matcher.group();
        }
        if (first == null) return null;
        String trimmed = versionId.trim();
        return trimmed.matches("^[0-9]+\\.[0-9]+(?:\\.[0-9]+)?(?:[-+].*)?$") ? first : last;
    }

    public static boolean matchesAny(String[] values, String expected) {
        if (values == null || values.length == 0 || expected == null || expected.isEmpty()) return true;
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(expected)) return true;
        }
        return false;
    }

    public static boolean loaderMatches(String[] values, String expected) {
        if (values == null || values.length == 0 || expected == null || expected.isEmpty()) return true;
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
