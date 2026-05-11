package net.kdt.pojavlaunch.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

/**
 * Search filters, passed to APIs
 */
public class SearchFilters {
    public boolean isModpack;
    public String name;
    @Nullable public String mcVersion;
    /** Mod loader filter: "fabric", "forge", "quilt", "neoforge", or null/empty for any */
    @Nullable public String modLoader;

}