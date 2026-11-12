package net.kdt.pojavlaunch.modloaders.modpacks;

import net.kdt.pojavlaunch.modloaders.modpacks.ModManagerAdapter.ModEntry;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Offline mod audit. It only inspects metadata and never loads or executes a mod JAR. */
public final class ModHealthReport {
    public enum Severity { ERROR, WARNING, INFO }

    public static final class Issue {
        public final Severity severity;
        public final String modName;
        public final String code;
        public final String message;
        public final String dependencyId;

        Issue(Severity severity, String modName, String code, String message, String dependencyId) {
            this.severity = severity;
            this.modName = modName;
            this.code = code;
            this.message = message;
            this.dependencyId = dependencyId;
        }
    }

    public static final class Report {
        public final List<Issue> issues = new ArrayList<>();
        public int enabledMods;
        public int disabledMods;
        public int inspectedMetadata;

        public int count(Severity severity) {
            int count = 0;
            for (Issue issue : issues) if (issue.severity == severity) count++;
            return count;
        }

        public boolean hasProblems() {
            return !issues.isEmpty();
        }

        public List<Issue> missingRequiredDependencies() {
            ArrayList<Issue> missing = new ArrayList<>();
            for (Issue issue : issues) {
                if ("MISSING_REQUIRED_DEPENDENCY".equals(issue.code)) missing.add(issue);
            }
            return missing;
        }

        public String summary() {
            return enabledMods + " enabled • " + disabledMods + " disabled • " +
                    count(Severity.ERROR) + " errors • " + count(Severity.WARNING) + " warnings";
        }

        public String toDisplayText() {
            if (issues.isEmpty()) return "No compatibility problems were detected in the metadata that could be read.";
            StringBuilder text = new StringBuilder();
            for (Issue issue : issues) {
                if (text.length() > 0) text.append("\n\n");
                text.append(issue.severity.name()).append(" — ").append(issue.modName)
                        .append("\n").append(issue.message);
            }
            return text.toString();
        }
    }

    private ModHealthReport() {}

    public static List<ModEntry> scanDirectory(File directory, String activeLoader) {
        ArrayList<ModEntry> entries = new ArrayList<>();
        File[] files = directory == null ? null : directory.listFiles();
        if (files == null) return entries;
        for (File file : files) {
            if (!file.isFile()) continue;
            String lower = file.getName().toLowerCase(Locale.ROOT);
            boolean enabled = lower.endsWith(".jar");
            if (!enabled && !lower.endsWith(".jar.disabled")) continue;
            ModMetadataReader.Info info = ModMetadataReader.read(file.getName(), file);
            entries.add(new ModEntry(file, info, enabled, activeLoader));
        }
        Collections.sort(entries, Comparator.comparing(entry -> entry.file.getName().toLowerCase(Locale.ROOT)));
        return entries;
    }

    public static Report analyze(List<ModEntry> entries, String minecraftVersion, String activeLoader) {
        Report report = new Report();
        Map<String, List<ModEntry>> installedById = new HashMap<>();
        for (ModEntry entry : entries) {
            if (!entry.enabled) {
                report.disabledMods++;
                continue;
            }
            report.enabledMods++;
            if (entry.info == null) continue;
            if (entry.info.loader != null && !"Unknown".equals(entry.info.loader) &&
                    !"Unreadable JAR".equals(entry.info.loader)) {
                report.inspectedMetadata++;
            }
            String id = normalize(entry.info.modId);
            if (id != null) {
                List<ModEntry> sameId = installedById.get(id);
                if (sameId == null) {
                    sameId = new ArrayList<>();
                    installedById.put(id, sameId);
                }
                sameId.add(entry);
            }
        }

        for (Map.Entry<String, List<ModEntry>> item : installedById.entrySet()) {
            if (item.getValue().size() > 1) {
                for (ModEntry entry : item.getValue()) {
                    report.issues.add(new Issue(Severity.ERROR, displayName(entry), "DUPLICATE_MOD_ID",
                            "Another enabled JAR declares the same mod ID: " + item.getKey() + ". Keep only one copy.", null));
                }
            }
        }

        Set<String> installedIds = new HashSet<>(installedById.keySet());
        for (ModEntry entry : entries) {
            if (!entry.enabled || entry.info == null) continue;
            ModMetadataReader.Info info = entry.info;
            String name = displayName(entry);

            if (info.hasLoaderMismatch(activeLoader)) {
                report.issues.add(new Issue(Severity.ERROR, name, "LOADER_MISMATCH",
                        "This JAR declares " + info.loader + ", but the selected profile uses " + activeLoader + ".", null));
            }

            Boolean compatible = ModCompatibility.isMinecraftRequirementSatisfied(info.minecraftRequirement, minecraftVersion);
            if (Boolean.FALSE.equals(compatible)) {
                report.issues.add(new Issue(Severity.ERROR, name, "MINECRAFT_VERSION_MISMATCH",
                        "Required Minecraft version: " + info.minecraftRequirement +
                                ". Selected version: " + (minecraftVersion == null ? "unknown" : minecraftVersion) + ".", null));
            }

            for (String dependency : info.requiredDependencies) {
                String normalized = normalize(dependency);
                if (normalized == null || ModMetadataReader.isPlatformDependency(normalized) || installedIds.contains(normalized)) continue;
                report.issues.add(new Issue(Severity.ERROR, name, "MISSING_REQUIRED_DEPENDENCY",
                        "Required dependency is not installed: " + dependency + ".", dependency));
            }

            for (String dependency : info.optionalDependencies) {
                String normalized = normalize(dependency);
                if (normalized == null || ModMetadataReader.isPlatformDependency(normalized) || installedIds.contains(normalized)) continue;
                report.issues.add(new Issue(Severity.WARNING, name, "MISSING_OPTIONAL_DEPENDENCY",
                        "Optional dependency is not installed: " + dependency + ".", dependency));
            }

            for (String conflict : info.conflicts) {
                if (installedIds.contains(normalize(conflict))) {
                    report.issues.add(new Issue(Severity.ERROR, name, "MOD_CONFLICT",
                            "This mod declares a conflict with installed mod: " + conflict + ".", conflict));
                }
            }
            for (String brokenBy : info.breaks) {
                if (installedIds.contains(normalize(brokenBy))) {
                    report.issues.add(new Issue(Severity.ERROR, name, "MOD_BREAKS",
                            "This mod declares that it is incompatible with installed mod: " + brokenBy + ".", brokenBy));
                }
            }
        }
        return report;
    }

    public static List<String> findRequiredDependents(String modId, List<ModEntry> entries) {
        ArrayList<String> dependents = new ArrayList<>();
        String target = normalize(modId);
        if (target == null) return dependents;
        for (ModEntry entry : entries) {
            if (!entry.enabled || entry.info == null || entry.info.requiredDependencies == null) continue;
            for (String dependency : entry.info.requiredDependencies) {
                if (target.equals(normalize(dependency))) {
                    dependents.add(displayName(entry));
                    break;
                }
            }
        }
        return dependents;
    }

    private static String normalize(String id) {
        if (id == null) return null;
        String value = id.trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    private static String displayName(ModEntry entry) {
        if (entry.info != null && entry.info.displayName != null && !entry.info.displayName.trim().isEmpty()) {
            return entry.info.displayName;
        }
        return entry.file.getName();
    }
}
