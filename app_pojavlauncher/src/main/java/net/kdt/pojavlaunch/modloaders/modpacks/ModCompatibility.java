package net.kdt.pojavlaunch.modloaders.modpacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

/** Compatibility helpers for the selected Minecraft profile and common loader version constraints. */
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

    /** Returns null when the metadata format is unknown; otherwise indicates whether the version is accepted. */
    public static Boolean isMinecraftRequirementSatisfied(String requirement, String currentVersion) {
        if (requirement == null || requirement.trim().isEmpty() || currentVersion == null || currentVersion.trim().isEmpty()) return null;
        String source = requirement.trim();
        if ("*".equals(source)) return true;

        if ((source.startsWith("[") || source.startsWith("(")) &&
                !(source.startsWith("[") && source.length() > 1 && source.charAt(1) == '"') &&
                !source.startsWith("['")) {
            if (source.length >= 2) {
                String opening = source.substring(0, 1);
                String closing = source.substring(source.length() - 1);
                String body = source.substring(1, source.length() - 1);
                int comma = body.indexOf(',');
                if (comma >= 0) {
                    String lowerBound = body.substring(0, comma).trim();
                    String upperBound = body.substring(comma + 1).trim();
                    int lower = lowerBound.isEmpty() ? Integer.MIN_VALUE : compareVersions(currentVersion, lowerBound);
                    int upper = upperBound.isEmpty() ? Integer.MAX_VALUE : compareVersions(currentVersion, upperBound);
                    boolean lowerOk = lowerBound.isEmpty() || (opening.equals("[") ? lower >= 0 : lower > 0);
                    boolean upperOk = upperBound.isEmpty() || (closing.equals("]") ? upper <= 0 : upper < 0);
                    return lowerOk && upperOk;
                }
            }
        }

        // Fabric and Quilt use Maven-like comparisons such as >=1.20 <1.21.
        Matcher constraint = Pattern.compile("(>=|<=|>|<|=)?\\s*([0-9]+(?:\\.[0-9]+){1,2}(?:\\.x)?)", Pattern.CASE_INSENSITIVE).matcher(source);
        List<String> operators = new ArrayList<>();
        List<String> versions = new ArrayList<>();
        while (constraint.find()) {
            operators.add(constraint.group(1) == null ? "=" : constraint.group(1));
            versions.add(constraint.group(2));
        }
        if (versions.isEmpty()) return null;

        // Provider JSON arrays represent alternative versions; a bracketed Maven range was handled above.
        if (source.startsWith("[") && source.endsWith("]") && source.indexOf('"') >= 0) {
            for (String candidate : versions) {
                if (isExactOrWildcardMatch(currentVersion, candidate)) return true;
            }
            return false;
        }

        for (int i = 0; i < versions.size(); i++) {
            int comparison = compareVersions(currentVersion, versions.get(i));
            String operator = operators.get(i);
            boolean accepted;
            switch (operator) {
                case ">=": accepted = comparison >= 0; break;
                case "<=": accepted = comparison <= 0; break;
                case ">": accepted = comparison > 0; break;
                case "<": accepted = comparison < 0; break;
                default: accepted = isExactOrWildcardMatch(currentVersion, versions.get(i)); break;
            }
            if (!accepted) return false;
        }
        return true;
    }

    private static boolean isExactOrWildcardMatch(String current, String expected) {
        if (expected.toLowerCase(Locale.ROOT).endsWith(".x")) {
            String prefix = expected.substring(0, expected.length() - 2);
            return current.equals(prefix) || current.startsWith(prefix + ".");
        }
        return compareVersions(current, expected) == 0;
    }

    private static int compareVersions(String left, String right) {
        int[] a = versionParts(left);
        int[] b = versionParts(right);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return av < bv ? -1 : 1;
        }
        return 0;
    }

    private static int[] versionParts(String value) {
        Matcher matcher = Pattern.compile("[0-9]+").matcher(value == null ? "" : value);
        List<Integer> parts = new ArrayList<>();
        while (matcher.find()) {
            try { parts.add(Integer.parseInt(matcher.group())); }
            catch (NumberFormatException ignored) { parts.add(0); }
        }
        int[] result = new int[parts.size()];
        for (int i = 0; i < result.length; i++) result[i] = parts.get(i);
        return result;
    }
}
