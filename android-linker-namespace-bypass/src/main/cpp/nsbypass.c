
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
#include "fasthook/nsbypass_dlfcn.h"
#include "platform.h"
#include "elf_soname_patcher.c"
#include <stdlib.h>
#include "nsbypass.h"
#include <jni.h>

// libdl_android.so and ld-android.so are aliases to linker64 impl
// libdl_android.so provides WEAK symbols and missing dlFuncs. Don't use it.
// ld-android.so is more complete, so fallback to that if dl functions can be acquired.

// This means a configuration of libdl + ld-android is possible
// The preferred configuration on arm64 will be libdl + linker64

// We have two sources for this, linker64/linker or libdl.so via ARM64 shenanigans
private_dl_funcs get_dl_functions(){
    private_dl_funcs dlFuncs;
    void* linkerHandle = nsbypass_dlopen(LINKER_PATH, 0);
    // First attempt the normal libadrenotools method (ARM64 shenanigans)
#if (defined __aarch64__)
    // This searches libdl which has WEAK funcs.
    dlFuncs.dlopen = find_branch_label(&dlopen);
    dlFuncs.dlopen_ext = find_branch_label(&android_dlopen_ext);
    dlFuncs.dlclose = find_branch_label(&dlclose);
    dlFuncs.dlsym = find_branch_label(&dlsym);
#endif
    // If that fails, try looking for it in memory
    if (!dlFuncs.dlopen) dlFuncs.dlopen = nsbypass_dlsym(linkerHandle, "__loader_dlopen");
    if (!dlFuncs.dlopen_ext) dlFuncs.dlopen_ext = nsbypass_dlsym(linkerHandle, "__loader_android_dlopen_ext");
    if (!dlFuncs.dlclose) dlFuncs.dlclose = nsbypass_dlsym(linkerHandle, "__loader_dlclose");
    if (!dlFuncs.dlsym) dlFuncs.dlsym = nsbypass_dlsym(linkerHandle, "__loader_dlsym");
    // Don't dlclose that, it's not our property.
    return dlFuncs;
}

private_linker_funcs get_namespace_functions(){
    private_linker_funcs linkerFuncs = {0};
    void* linkerHandle = nsbypass_dlopen(LINKER_PATH, 0);
    if (!linkerHandle && g_privateDlFuncs.dlopen) {
        // This fallback is only possible with the private API, or else namespace restrictions
        // stops us. &dlopen leads to g_default_namespace so this bypasses that.
        linkerHandle = g_privateDlFuncs.dlopen("ld-android.so", RTLD_LAZY, &dlopen);
    }
    if (linkerHandle && g_privateDlFuncs.dlsym != 0) { // Check if it found a handle
        // Note: liblinkernsbypass uses ld-android.so for link* and libdl_android.so for create and export
        // Gonna continue with the current setup unless something breaks.
        linkerFuncs.create_namespace = g_privateDlFuncs.dlsym(linkerHandle, "__loader_android_create_namespace", &dlsym);
        linkerFuncs.link_namespaces = g_privateDlFuncs.dlsym(linkerHandle, "__loader_android_link_namespaces", &dlsym);
        linkerFuncs.link_namespace_all_libs = g_privateDlFuncs.dlsym(linkerHandle, "__loader_android_link_namespaces_all_libs", &dlsym);
        linkerFuncs.get_exported_namespace = g_privateDlFuncs.dlsym(linkerHandle, "__loader_android_get_exported_namespace", &dlsym);
    } else {
        LOGE("Unable to load namespace functions! dlFunction loading probably failed?");
    }

    return linkerFuncs;
}

static struct android_namespace_t* driver_namespace;
private_dl_funcs g_privateDlFuncs = {0};
private_linker_funcs g_linkerFuncs = {0};
clns_funcs g_clnsFuncs = {0};

__attribute__((constructor)) void resolve_global_symbols() {
    g_privateDlFuncs = get_dl_functions();
    g_linkerFuncs = get_namespace_functions();
    g_clnsFuncs.clns_android_dlopen_ext = android_dlopen_ext;

    if (!g_linkerFuncs.create_namespace ||
            !g_linkerFuncs.link_namespaces ||
            !g_linkerFuncs.link_namespace_all_libs ||
            !g_linkerFuncs.get_exported_namespace) {
        LOGE("Failed to resolve Android linker namespace functions! Cannot run nsbypass.");
        return;
    }
//    // assemble the full path search path
//    // FIXME: Use JNI to fetch this. We will need to unconstructor to get JNIEnv from JNI_OnLoad.
//    const char* native_dir = getenv("POJAV_NATIVEDIR");
//    const char* cache_dir = getenv("TMPDIR");
//    char full_path[strlen(SEARCH_PATH) + strlen(native_dir) + 2 + 1];
//    sprintf(full_path, "%s:%s", SEARCH_PATH, native_dir);
//    driver_namespace = g_linkerFuncs.create_namespace("mesa-driver-namespace",
//            getenv("LD_LIBRARY_PATH_DRIVER_NAMESPACE"),
//            full_path,
//            ANDROID_NAMESPACE_TYPE_SHARED_ISOLATED,
//            "/system/:/data/:/vendor/:/apex/", NULL);
//    g_linkerFuncs.link_namespaces(driver_namespace, NULL, "ld-android.so");
//    g_linkerFuncs.link_namespaces(driver_namespace, NULL, "libnativeloader.so");
//    g_linkerFuncs.link_namespaces(driver_namespace, NULL, "libnativeloader_lazy.so");
    // https://android.googlesource.com/platform/bionic/%2B/1ffec1cc4d0e283bb1ff6f49843769a3493b8d73/linker/dlfcn.cpp#294
    // Later android code has more confusing code where it inherits from ld-android.
    // Basically setting parent to &dlopen lets us access g_default_namespace.
    // Default namespace has permissions to load from /system and /vendor which is needed for
    // like all the custom drivers.

    // This means we can create a namespace that inherits from g_default_namespace. This is called
    // an escape namespace by bylaws/libadrenotools.

    // The pojav implementation
    //   - Places turnip in the driver_namespace (SHARED_ISOLATED orphan, with custom path load override imitating link_all_libs)
    //   - Places liblinkerhook in the driver_namespace
    //   - Loads renamed libvulkan.so -> 000vulkan.so into driver_namespace as vulkan loader
    //   - 000vulkan.so acts as vulkan loader, handle is used by program
    //
    //  LWJGL is modified to use VULKAN_PTR so we don't need as extensive hooking faculties as
    //  libadrenotools.
    //
    //  Code flows like this. On startup, turnip, libvulkan, and liblinkerhook are all put inside
    //  a SHARED_ISOLATED orphan namespace with permission to load from /system/:/data/:/vendor/:/apex/
    //  and libvulkan's handle from then on is passed to LWJGL every time it wants it.
    //  No mod in their sane mind would ever dlopen vulkan themselves. If they did though, they'd
    //  likely get the android implementation and fail miserably.


    // The libadrenotools implementation
    //   - Places libhook_impl in an escape namespace (hookNS)
    //   - Loads renamed libvulkan.so into hookNS (not sure what its renamed to)
    //   - On trigger of hook, we copy what bionic does (create new driverNS) but also load
    //     libandroid.so and the hook into there.
    //   - Set a bunch of adreno features settings
    //   - Place turnip in driverNS
    //
    //  The patched/hooked libvulkan needs to be in its own namespace because that's how the hook
    //  works. The driver exists in its own namespace because of the adreno feature toggles that
    //  are themselves, their own natives that need to be loaded. This separation just keeps things
    //  tidy.
    //
    //  Code flows like this. adrenotools_open_libvulkan is called, create hookNS which is the
    //  place where patched/hooked libvulkan stays in. hookNS cause libvulkan to load turnip as
    //  how android would load a vk driver, only that there are modifications to the namespace
    //  creation parameters and extra libraries are loaded inside, this is named driverNS.
    //
    //  Now the confusing bit, it also loads the hook library inside the namespace. This is only
    //  because they have extra hooks on kgsl and fopen. I have no idea why they do this instead of
    //  making those entirely seperate hooks.
    //
    //  So what ends up happening is you get hookNS which has libvulkan and every single hook
    //  and driverNS which has all the turnip driver stuff and also every single hook.
    //  So much for keeping everything tidy.

    // The TLDR;
    //   - libvulkan is patched and loaded loaded alongside a hook for android_load_sphal_library
    //     and android_dlopen_ext which redirects all loads to vulkan related libs to turnip handle.
    //     This must be in a custom namespace or else we conflict with android sysvk. Does not need
    //     escape namespace.
    //   - The patched libvulkan is used as the handle for programs that use the custom driver.
    //   - The custom driver can be loaded anywhere so long as libvulkan.so access it via
    //     android_load_sphal_library and android_dlopen_ext. Turnip needs an escape
    //     namespace because it depends on private apis. libcutil and libhardware afaik
    //   - libvulkan DT_SONAME should be modified or else it may use an already loaded instance.
    //     its a safety/prevention measure. This goes the same with any other libraries. Only issue
    //     is it breaks the .dynamic needed SONAMEs. So instead we should just create namespace,
    //     load the libs we need, and only after do we link to escape/global namespace in case the
    //     libs decide to call dlopen or dlsym on other stuff.

    /*
     * Can we imitate libadrenotools structure on OpenGL? Maybe.
     * libEGL_mesa.so and libgallium_dri.so after all. The issue would be we need to hook dlopen
     * for both of those to redirect to the handle inside the already setup namespace.
     *
     * Problem is both of those could technically be considered the driver since they're pretty
     * much one and the same. Both even need escape namespace since libEGL_mesa needs the gallium
     * driver and they almost definitely wont like being in seperate namespaces.
     *
     * We don't really have a hookNS here, just a driverNS. The two expect to be loaded in the
     * same namespace after all.
     *
     *
     * So for GL we can likely get away with just creating an escape namespace for the EGL and GL
     * libs to live inside and just refer to their handles from then on.
     *
     * So we end up needing two seperate frontends for GL and vk. how annoying
     */

    // TODO: Swap to link_namespace_all_libs and an escape namespace.

    // Setting the 2nd namespace to NULL defaults it to g_default_namespace.
    // This is just the manual version of link_namespace_all_libs to a new namespace with parent
    // &dlopen (which is just g_default_namespace)
}

// dlopen in a specific namespace
void* linker_ns_dlopen(const char* name, int flag, struct android_namespace_t* ns) {
    if (!ns) return NULL;
    android_dlextinfo dlextinfo;
    dlextinfo.flags = ANDROID_DLEXT_USE_NAMESPACE;
    dlextinfo.library_namespace = ns;
    return android_dlopen_ext(name, flag, &dlextinfo);
}

// dlopen in a specific namespace in a custom dir and a modified DT_SONAME
void* linker_ns_dlopen_unique(const char* tmpDir, const char* libDir, const char* libName, int flags, struct android_namespace_t* ns) {
    char pathbuf[PATH_MAX];
    static uint16_t patch_id;
    int patch_fd, real_fd;

    snprintf(pathbuf, PATH_MAX ,"%s/%d%s_patched.so", tmpDir, patch_id, libName);
    patch_fd = open(pathbuf, O_CREAT | O_RDWR, S_IRUSR | S_IWUSR);
    if(patch_fd == -1) return NULL;

    snprintf(pathbuf,PATH_MAX,"%s/%s", libDir, libName);
    real_fd = open(pathbuf, O_RDONLY);
    if(real_fd == -1) {
        close(patch_fd);
        return NULL;
    }

    if(!patch_elf_soname(real_fd, patch_id, patch_id)) {
        close(patch_fd);
        close(real_fd);
        return NULL;
    }
    android_dlextinfo extinfo;
    extinfo.flags = ANDROID_DLEXT_USE_NAMESPACE | ANDROID_DLEXT_USE_LIBRARY_FD;
    extinfo.library_fd = patch_fd;
    extinfo.library_namespace = ns;
    snprintf(pathbuf, PATH_MAX, "/proc/self/fd/%d", patch_fd);
    return android_dlopen_ext(pathbuf, flags, &extinfo);
}






