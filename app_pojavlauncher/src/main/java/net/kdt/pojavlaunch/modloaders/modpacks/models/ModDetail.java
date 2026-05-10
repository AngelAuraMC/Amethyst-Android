package net.kdt.pojavlaunch.modloaders.modpacks.models;


import androidx.annotation.NonNull;

import java.util.Arrays;

public class ModDetail extends ModItem {
    /* A cheap way to map from the front facing name to the underlying id */
    public String[] versionNames;
    public String [] mcVersionNames;
    public String[] versionUrls;
    /* SHA 1 hashes, null if a hash is unavailable */
    public String[] versionHashes;
    /* Per-version dependency project IDs */
    public String[][] versionDependencyIds;
    /* Per-version dependency types — "required" or "optional" */
    public String[][] versionDependencyTypes;

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionUrls, String[] hashes) {
        this(item, versionNames, mcVersionNames, versionUrls, hashes, null, null);
    }

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionUrls, String[] hashes,
                     String[][] depIds, String[][] depTypes) {
        super(item.apiSource, item.isModpack, item.id, item.title, item.description, item.imageUrl);
        this.isRestricted = item.isRestricted;
        this.websiteUrl = item.websiteUrl;
        this.versionNames = versionNames;
        this.mcVersionNames = mcVersionNames;
        this.versionUrls = versionUrls;
        this.versionHashes = hashes;
        this.versionDependencyIds = depIds;
        this.versionDependencyTypes = depTypes;

        // Add the mc version to the version model
        for (int i=0; i<versionNames.length; i++){
            if (mcVersionNames[i] != null && !versionNames[i].contains(mcVersionNames[i]))
                versionNames[i] += " - " + mcVersionNames[i];
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "ModDetail{" +
                "versionNames=" + Arrays.toString(versionNames) +
                ", mcVersionNames=" + Arrays.toString(mcVersionNames) +
                ", versionUrls=" + Arrays.toString(versionUrls) +
                ", id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", apiSource=" + apiSource +
                ", isModpack=" + isModpack +
                '}';
    }
}