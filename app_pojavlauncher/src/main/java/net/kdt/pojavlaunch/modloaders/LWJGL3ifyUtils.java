package net.kdt.pojavlaunch.modloaders;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LWJGL3ifyUtils {
    public static LWJGL3ifyVersionList getLWJGL3ifyVersionList(ModpackApi modpackApi) throws IOException {
        ModDetail lwjgl3ifyModDetail = getLWJGL3ifyModDetail(modpackApi);
        List<LWJGL3ifyMod> supportedVersions = new ArrayList<>(), brokenVersions = new ArrayList<>();
        for (int i = 0; i < lwjgl3ifyModDetail.versionNames.length; i++) {
            String normalizedVersion = normalizeVersionName(lwjgl3ifyModDetail.versionNames[i], lwjgl3ifyModDetail.apiSource);
            boolean isSupportedVersion = Integer.parseInt(normalizedVersion.split("\\.")[0]) < 3;

            LWJGL3ifyMod version = new LWJGL3ifyMod(
                    normalizedVersion,
                    lwjgl3ifyModDetail.versionUrls[i],
                    lwjgl3ifyModDetail.imageUrl,
                    lwjgl3ifyModDetail.id,
                    lwjgl3ifyModDetail.versionHashes[i],
                    lwjgl3ifyModDetail.dependencies[i]
            );
            // LWJGL3ify 3.x uses SDL which needs to be fixed first
            (isSupportedVersion ? supportedVersions : brokenVersions).add(version);
        }
        return new LWJGL3ifyVersionList(supportedVersions, brokenVersions);
    }

    /**
     * @param jarName LWJGL3ify jar name, the same as Curseforge {@code versionNames} (ex. {@code 3.0.16}, {@code 2.1.18})
     * @param source Either {@link Constants#SOURCE_MODRINTH} or {@link Constants#SOURCE_CURSEFORGE}
     * @return Filled out {@link LWJGL3ifyMod} corresponding to version provided
     * @throws IllegalArgumentException If LWJGL3ify version was not found in the source provided
     * @throws IOException If provided source is not what was expected
     */
    public static LWJGL3ifyMod getLWJGL3ifyVersion(String jarName, ModpackApi source) throws IOException {
        // This is a hack but it should work
        String providedNormalizedVersion = normalizeVersionName(jarName, Constants.SOURCE_CURSEFORGE);
        ModDetail lwjgl3ifyModDetail = getLWJGL3ifyModDetail(source);
        for (int i = 0; i < lwjgl3ifyModDetail.versionNames.length; i++) {
            String normalizedVersion = normalizeVersionName(lwjgl3ifyModDetail.versionNames[i], lwjgl3ifyModDetail.apiSource);
            if (providedNormalizedVersion.equals(normalizedVersion))
                return new LWJGL3ifyMod(
                    providedNormalizedVersion,
                    lwjgl3ifyModDetail.versionUrls[i],
                    lwjgl3ifyModDetail.imageUrl,
                    lwjgl3ifyModDetail.id,
                    lwjgl3ifyModDetail.versionHashes[i],
                    lwjgl3ifyModDetail.dependencies[i]
            );
        }
        String sourceName = (lwjgl3ifyModDetail.apiSource == Constants.SOURCE_MODRINTH) ? "Modrinth" : "Curseforge";
        throw new IllegalArgumentException("Cannot find LWJGL3ify version "+providedNormalizedVersion+" from "+sourceName);
    }

    /**
     * @return Flat list of all dependencies needed by {@code lwjgl3ifyMod}.
     */
    public static List<LWJGL3ifyMod> collectDependencies(LWJGL3ifyMod lwjgl3ifyMod, ModpackApi modpackApi) {
        List<LWJGL3ifyMod> allDeps = new ArrayList<>();
        for (ModDetail.Dependencies dep : lwjgl3ifyMod.dependencies) {
            ModDetail detail = getModDetail(modpackApi, dep.project_id);
            if (detail != null) {
                LWJGL3ifyMod newMod = new LWJGL3ifyMod(
                        detail.versionNames[0],
                        detail.versionUrls[0],
                        detail.imageUrl,
                        detail.id,
                        detail.versionHashes[0],
                        detail.dependencies[0]
                );

                allDeps.add(newMod);
                // omg recursion!?!?!
                allDeps.addAll(collectDependencies(newMod, modpackApi));
            }
        }
        return allDeps;
    }

    @NonNull
    private static String normalizeVersionName(String versionName, int apiSource) {
        if (apiSource == Constants.SOURCE_MODRINTH) { // Ex. 3.0.16 - 1.7.10
            versionName = versionName.replaceAll(" - .*", "");
        }else if (apiSource == Constants.SOURCE_CURSEFORGE) { // Ex. lwjgl3ify-3.0.16.jar - 1.7.10
            versionName = versionName.split("-")[1].replace(".jar", "");
        }else throw new IllegalArgumentException("LWJGL3ify is only available on Modrinth or Curseforge!");
        return versionName;
    }

    private static ModDetail getModDetail(ModpackApi modpackApi, String id){
        ModDetail modDetail = null;
        // Modrinth is more complete in this context. Curseforge is missing some releases.
        if (modDetail == null && id != null && !id.isEmpty()) {
            modDetail = fetch(modpackApi, Constants.SOURCE_MODRINTH, id);
        }
        try {
            if (modDetail == null && id != null && !id.isEmpty()) {
                Integer.parseInt(id); // Triggers exception, skipping call to CF if provided id isn't int
                modDetail = fetch(modpackApi, Constants.SOURCE_CURSEFORGE, id);
            }
        } catch (NumberFormatException ignored) {}
        return modDetail;
    }

    private static ModDetail fetch(ModpackApi modpackApi, int source, String id) {
        ModItem item = new ModItem(source, false, id, null, null, null);
        return modpackApi.getModDetails(item);
    }

    @NonNull
    private static ModDetail getLWJGL3ifyModDetail(ModpackApi modpackApi) throws IOException {
        ModDetail lwjgl3ifyModDetail = getModDetail(modpackApi, "lwjgl3ify");
        if (lwjgl3ifyModDetail == null) // Hardcoded ID is a bad idea, but it'll work well enough
            lwjgl3ifyModDetail = getModDetail(modpackApi, "998880");
        if (lwjgl3ifyModDetail == null) throw new IOException("Unable to fetch LWJGL3ify version list from Curseforge and Modrinth. " +
                "Please check your internet connection and whether Modrinth and Curseforge are accessible.");
        return lwjgl3ifyModDetail;
    }
    // TODO: Turn this into a generic ModItem class for general mods and refactor that crusty modpack naming scheme
    public static class LWJGL3ifyMod {
        public final String versionName;
        public final String downloadUrl;
        public final String iconUrl;
        public final String id;
        public final String hash;
        public final ModDetail.Dependencies[] dependencies;

        public LWJGL3ifyMod(String versionName, String downloadUrl, String iconUrl, String id, String hash, ModDetail.Dependencies[] dependencies) {
            this.versionName = versionName;
            this.downloadUrl = downloadUrl;
            this.iconUrl = iconUrl;
            this.id = id;
            this.hash = hash;
            this.dependencies = dependencies;
        }
    }
    public static class LWJGL3ifyVersionList {
        public final List<LWJGL3ifyMod> supportedVersions;
        public final List<LWJGL3ifyMod> brokenVersions; // SDL versions for now

        public LWJGL3ifyVersionList(List<LWJGL3ifyMod> mSupportedVersions, List<LWJGL3ifyMod> mBrokenVersions) {
            this.supportedVersions = mSupportedVersions;
            this.brokenVersions = mBrokenVersions;
        }
    }
}
