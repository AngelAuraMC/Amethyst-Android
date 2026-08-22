
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
    linkerFuncs.link_namespaces(driver_namespace, NULL, "ld-android.so");
    linkerFuncs.link_namespaces(driver_namespace, NULL, "libnativeloader.so");
    linkerFuncs.link_namespaces(driver_namespace, NULL, "libnativeloader_lazy.so");
}






