package net.kdt.pojavlaunch.modloaders.modpacks.api;


import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;

import java.io.IOException;
import java.io.File;
import java.security.NoSuchAlgorithmException;


/**
 *
 */
public interface ModpackApi {

    /**
     * @param searchFilters Filters
     * @param previousPageResult The result from the previous page
     * @return the list of mod items from specified offset
     */
    SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult);

    /**
     * @param searchFilters Filters
     * @return A list of mod items
     */
    default SearchResult searchMod(SearchFilters searchFilters) {
        return searchMod(searchFilters, null);
    }

    /**
     * Fetch the mod details
     * @param item The moditem that was selected
     * @return Detailed data about a mod(pack)
     */
    ModDetail getModDetails(ModItem item);

    /**
     * Download and install the mod(pack)
     * @param modDetail The mod detail data
     * @param selectedVersion The selected version
     */
    default void handleInstallation(Context context, ModDetail modDetail, int selectedVersion) {
        if (!modDetail.isModpack) {
            Toast.makeText(context, R.string.novacraft_mod_install_started, Toast.LENGTH_SHORT).show();
            PojavApplication.sExecutorService.execute(() -> {
                try {
                    installMod(modDetail, selectedVersion);
                    boolean hasDependencies = false;
                    if (modDetail.dependencies != null && selectedVersion < modDetail.dependencies.length) {
                        ModDetail.Dependencies[] dependencies = modDetail.dependencies[selectedVersion];
                        if (dependencies != null) {
                            for (ModDetail.Dependencies dependency : dependencies) {
                                if (dependency != null && dependency.dependency_type != null &&
                                        dependency.dependency_type.toLowerCase(java.util.Locale.ROOT).contains("required")) {
                                    hasDependencies = true;
                                    break;
                                }
                            }
                        }
                    }
                    final int message = hasDependencies
                            ? R.string.novacraft_mod_installed_check_dependencies
                            : R.string.novacraft_mod_install_success;
                    Tools.runOnUiThread(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
                } catch (IOException e) {
                    Tools.showErrorRemote(context, R.string.novacraft_mod_install_failed, e);
                }
            });
            return;
        }

        // For modpacks, loader installation and the original progress workflow remain unchanged.
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                ModLoader loaderInfo = installMod(modDetail, selectedVersion);
                if (loaderInfo == null) return;
                loaderInfo.getDownloadTask(new NotificationDownloadListener(context, loaderInfo)).run();
            }catch (IOException e) {
                Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
            }
        });
    }

    /**
     * Install the mod(pack).
     * May require the download of additional files.
     * May requires launching the installation of a modloader
     * @param modDetail The mod detail data
     * @param selectedVersion The selected version
     */
    ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException;

    /**
     * Imports the mod(pack) from a file.
     * May require the download of additional files.
     * May requires launching the installation of a modloader
     * @param modpackFile Zip file to mrpack or cf zip pack
     */
    ModLoader importModpack(File modpackFile) throws IOException, NoSuchAlgorithmException;
}
