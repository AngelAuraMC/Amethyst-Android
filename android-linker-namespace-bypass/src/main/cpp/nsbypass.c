
#include <dlfcn.h>
#include <android/dlext.h>
#include <android/log.h>
#include <sys/mman.h>
#include <sys/user.h>
#include <string.h>
#include <stdio.h>
#include <linux/limits.h>
#include <errno.h>
#include <unistd.h>
#include <asm/unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <elf.h>
#include "nsbypass_dlfcn.h"
#include "platform.h"
#include <stdlib.h>
#include "nsbypass.h"

loader_dl_funcs get_dl_functions(){
    loader_dl_funcs ldDlFuncs = {0};
    // First attempt the normal libadrenotools method
#if (defined __aarch64__)
    ldDlFuncs.l_dlopen = find_branch_label(&dlopen);
    ldDlFuncs.l_dlclose = find_branch_label(&dlclose);
    ldDlFuncs.l_dlsym = find_branch_label(&dlsym);
    return ldDlFuncs;
#endif
    // If that fails, try looking for it in memory, inside the linker
    ldDlFuncs.handle = nsbypass_dlopen(LINKER_PATH, 0);
    ldDlFuncs.l_dlopen = nsbypass_dlsym(ldDlFuncs.handle, "__loader_dlopen");
    ldDlFuncs.l_dlclose = nsbypass_dlsym(ldDlFuncs.handle, "__loader_dlclose");
    ldDlFuncs.l_dlsym = nsbypass_dlsym(ldDlFuncs.handle, "__loader_dlsym");
    // Don't dlclose that, it's not our property.
    return ldDlFuncs;
}

linker_funcs get_namespace_functions(){
    linker_funcs linkerFuncs = {0};
    loader_dl_funcs dlFunctions = get_dl_functions();
    if (dlFunctions.l_dlopen != 0) {
        linkerFuncs.handle = dlFunctions.l_dlopen("ld-android.so", RTLD_LAZY, &dlopen);
        linkerFuncs.create_namespace = dlFunctions.l_dlsym(linkerFuncs.handle, "__loader_android_create_namespace", &dlsym);
        linkerFuncs.link_namespaces = dlFunctions.l_dlsym(linkerFuncs.handle, "__loader_android_link_namespaces", &dlsym);
        linkerFuncs.link_namespace_all_libs = dlFunctions.l_dlsym(linkerFuncs.handle, "__loader_android_link_namespaces_all_libs", &dlsym);
        linkerFuncs.get_exported_namespace = dlFunctions.l_dlsym(linkerFuncs.handle, "__loader_android_get_exported_namespace", &dlsym);
    } else {
        LOGE("Attempting hail mary fetch of namespace functions. What the hell are you using?!");
        // If we couldn't find a handle to the private api, try looking through memory anyway.
        // It's almost definitely not there.
        linkerFuncs.handle = nsbypass_dlopen("ld-android.so", 0);
        // If it's not found in memory dlopen it maybe?? Probably fails
        if (linkerFuncs.handle == 0) {
            if (dlopen("ld-android.so", RTLD_LAZY) == NULL) return linkerFuncs;
            linkerFuncs.handle = nsbypass_dlopen("ld-android.so", 0);
            if (linkerFuncs.handle == 0) return linkerFuncs;
        }

        linkerFuncs.create_namespace = nsbypass_dlsym(linkerFuncs.handle, "__loader_android_create_namespace");
        linkerFuncs.link_namespaces = nsbypass_dlsym(linkerFuncs.handle, "__loader_android_link_namespaces");
        linkerFuncs.link_namespace_all_libs = nsbypass_dlsym(linkerFuncs.handle, "__loader_android_link_namespaces_all_libs");
        linkerFuncs.get_exported_namespace = nsbypass_dlsym(linkerFuncs.handle, "__loader_android_get_exported_namespace");
    }

    return linkerFuncs;
}

static struct android_namespace_t* driver_namespace;

__attribute__((constructor)) void resolve_linker_symbols() {
//    // Makes bytehook stop being all crashy about hooky
//    if(driver_namespace != NULL) return true;
    linker_funcs linkerFuncs = get_namespace_functions();
    if (!linkerFuncs.handle ||
            !linkerFuncs.create_namespace ||
            !linkerFuncs.link_namespaces) {
        LOGE("Failed to resolve Android linker namespace functions! Cannot run nsbypass.");
        return;
    }
    // assemble the full path search path
    // FIXME: Use JNI to fetch this. We will need to unconstructor to get JNIEnv from JNI_OnLoad.
    const char* native_dir = getenv("POJAV_NATIVEDIR");
    const char* cache_dir = getenv("TMPDIR");
    char full_path[strlen(SEARCH_PATH) + strlen(native_dir) + 2 + 1];
    sprintf(full_path, "%s:%s", SEARCH_PATH, native_dir);
    driver_namespace = linkerFuncs.create_namespace("mesa-driver-namespace",
            getenv("LD_LIBRARY_PATH_DRIVER_NAMESPACE"),
            full_path,
            ANDROID_NAMESPACE_TYPE_SHARED_ISOLATED,
            "/system/:/data/:/vendor/:/apex/", NULL);
    // Setting parent to &dlopen lets us access the default namespace (thereby creating an escape).
    // We should link to an escape namespace instead of..this.
    linkerFuncs.link_namespaces(driver_namespace, NULL, "ld-android.so");
    linkerFuncs.link_namespaces(driver_namespace, NULL, "libnativeloader.so");
    linkerFuncs.link_namespaces(driver_namespace, NULL, "libnativeloader_lazy.so");

}






