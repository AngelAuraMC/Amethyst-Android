#include <android/dlext.h>
#include <android/log.h>
#include <asm/unistd.h>
#include <dlfcn.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <linux/limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/user.h>
#include <unistd.h>

#include "android_linker_namespace_bypass/elf_soname_patcher.h"
#include "android_linker_namespace_bypass/nsbypass.h"
#include "android_linker_namespace_bypass/platform.h"
#include "fasthook/nsbypass_dlfcn.h"

// https://cs.android.com/android/platform/superproject/+/329d792f6d5e33e8a6fc5a02809c795ce17774ab:art/libnativeloader/library_namespaces.cpp
// clns is the namespace we are in by default.
// g_default_namespace is the private API namespace where you can access the private API libs.

// A namespace created with the parent or linked to g_default_namespace is referred
// to as an escape namespace (bylaws/liblinkernsbypass)
// https://android.googlesource.com/platform/bionic/%2B/1ffec1cc4d0e283bb1ff6f49843769a3493b8d73/linker/dlfcn.cpp#294
// Later android code has more confusing code where it inherits from ld-android.
// Default namespace has permissions to load from /system and /vendor which is needed for
// like all the custom drivers.

// ld-android.so and linker64 provide the same SONAME in readelf.

// ld-android.so is not present in /proc/self/maps so it cannot be found
// by the memory scanning from fasthook.

// libdl somehow exports the __loader variants of its dlFuncs?? idk either


// Creating this needed to base off of pojav and liblinkernsbypass. These are my conclusions
// after studying those implementations

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
//  making those entirely seperate hooks or putting both libvulkan and the driver in one namespace.
//
//  So what ends up happening is you get hookNS which has libvulkan and every single hook
//  and driverNS which has all the turnip driver stuff and also every single hook.
//  So much for keeping everything tidy.

// The TLDR;
//   - libvulkan is patched and loaded loaded alongside a hook for android_load_sphal_library
//     and android_dlopen_ext which redirects all loads to vulkan related libs to turnip handle.
//     This must be in a custom namespace and modified name or else we conflict with android sysvk.
//     Does not need escape namespace.
//   - The libadrenotools hook creates the driverNS on demand as android_dlopen_ext is called by
//     libvulkan.
//   - The pojav hook puts hook, driver, and libvulkan in a single NS, loaded in that order.
//   - The libadrenotools hook uses escape namespaces.
//   - The pojav hook uses permitted_when_isolated_path.
//   - The patched libvulkan is used as the handle for programs that use the custom driver.
//   - The custom driver can be loaded anywhere so long as libvulkan.so access it via
//     android_load_sphal_library and android_dlopen_ext. Turnip needs an escape
//     namespace because it depends on private apis. libcutil and libhardware afaik
//  https://cs.android.com/android/platform/superproject/+/android-latest-release:bionic/libc/include/android/dlext.h;drc=414dd2d6b5fbbaf23423abe5cdee7f40f9d95ec1;l=77-80
//  https://cs.android.com/android/platform/superprojehttps://cs.android.com/android/platform/superproject/+/android-latest-release:bionic/libc/include/android/dlext.h;drc=414dd2d6b5fbbaf23423abe5cdee7f40f9d95ec1;l=97-99
//   - libvulkan.so filename and soname should be modified or else it might use systemvk for symbol
//     resolution. This goes the same with any other libraries. Only issue is it might break the
//     .dynamic needed SONAMEs. So instead we should just create namespace, load the libs we need,
//     and only after do we link to escape/global namespace in case the libs decide to call dlopen
//     or dlsym on other stuff. And preload all DT_NEEDED.


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

/**
 * Tests the provided dl functions to see if they work.
 * This way, any SIGSEGV or other stuff hard crashes early.
 * @param dlFuncs
 * @returns False if even 1 test fails, otherwise true.
 */
bool test_dlfuncs(private_dl_funcs dlFuncs);

/**
 * Tests the provided namespace functions to see if they work
 * This way, any SIGSEGV or other stuff hard crashes early.
 * Leaks memory.
 * @returns False if even 1 test fails, otherwise true.
 */
bool test_namespace_funcs(private_namespace_funcs nsFuncs);

/**
 * Fetches the function pointers in three ways, in descending order of priority.\n
 *
 *  - (aarch64 only) Using &dlopen, scan the assembly instructions until it finds the private API
 *  call and uses the pointers from there. This is likely to be libdl.so being scanned.\n
 *  - Scan /proc/self/maps for an r-xp instance of linker64 then dlsym that instance for pointers\n
 *  - Public API dlopen & dlsym on libdl.so for the private API pointers\n
 *  - Scan /proc/self/maps for an r-xp instance of linker64 then scan that instance for pointers\n
 * @return Private API versions of dlFunc*
 */
private_dl_funcs get_private_dl_functions(){
    // TODO: Verify if this works on Android 8 or lower, they have a weird thing
    // that doesn't exactly just have __loader_* laying around so am not sure about it.

    // First attempt the normal libadrenotools method (ARM64 shenanigans)
#if (defined __aarch64__)
    dlFuncs.dlopen = find_branch_label(&dlopen);
    dlFuncs.dlopen_ext = find_branch_label(&android_dlopen_ext);
    dlFuncs.dlclose = find_branch_label(&dlclose);
    dlFuncs.dlsym = find_branch_label(&dlsym);
    if (dlFuncs.dlopen != NULL &&
            dlFuncs.dlopen_ext != NULL &&
            dlFuncs.dlclose != NULL &&
            dlFuncs.dlsym != NULL) {
        return dlFuncs;
    }
#endif
    private_dl_funcs dlFuncs = {0};
    bool using_libdl = false;
    // Now attempt to scan memory.
    // Probably /apex/com.android.runtime/bin/linker64 but not 100% sure on that so just linker64
    void* linkerHandle = nsbypass_dlopen(LINKER, 0);
    // If scanning memory fails, do try this funny (this works, don't ask why idk either) [TEST ME]
    if (!linkerHandle) {
        LOGW("Memory scanning for linker/linker64 failed, falling back to libdl.so");
        linkerHandle = dlopen("libdl.so", RTLD_LAZY);
        using_libdl = true;
    }
    // eat any stale ones
    char *error = dlerror();
    if (error) LOGI("Stale dlerror: %s", error);

    // The funcs here are mixed with the arm64 ones if those fail, this is on purpose.
    if (!dlFuncs.dlopen) dlFuncs.dlopen = dlsym(linkerHandle, "__loader_dlopen");
    if (!dlFuncs.dlopen_ext) dlFuncs.dlopen_ext = dlsym(linkerHandle, "__loader_android_dlopen_ext");
    if (!dlFuncs.dlclose) dlFuncs.dlclose = dlsym(linkerHandle, "__loader_dlclose");
    if (!dlFuncs.dlsym) dlFuncs.dlsym = dlsym(linkerHandle, "__loader_dlsym");

    if (error) {
        LOGW("dlerror in using public API to acquire private API ptrs:  %s", error);
        linkerHandle = nsbypass_dlopen(LINKER, 0);
    }

    if (dlFuncs.dlopen != NULL &&
            dlFuncs.dlopen_ext != NULL &&
            dlFuncs.dlclose != NULL &&
            dlFuncs.dlsym != NULL) {
        return dlFuncs;
    }

    if (using_libdl) dlclose(linkerHandle);

    // Now fallback to full memory scans
    // This is unreliable so, more reason for mixing.

    // Possibly libdl.so handle, make sure that's not the case.
    linkerHandle = nsbypass_dlopen(LINKER, 0);
    // Not using using_libdl here cause maybe 2nd time's the charm? Eh probably insanity.
    if (!linkerHandle) return dlFuncs;
    if (!dlFuncs.dlopen) dlFuncs.dlopen = nsbypass_dlsym(linkerHandle, "__loader_dlopen");
    if (!dlFuncs.dlopen_ext) dlFuncs.dlopen_ext = nsbypass_dlsym(linkerHandle, "__loader_android_dlopen_ext");
    if (!dlFuncs.dlclose) dlFuncs.dlclose = nsbypass_dlsym(linkerHandle, "__loader_dlclose");
    if (!dlFuncs.dlsym) dlFuncs.dlsym = nsbypass_dlsym(linkerHandle, "__loader_dlsym");

    return dlFuncs;
}

bool test_dlfuncs(private_dl_funcs dlFuncs) {
#ifdef DISABLE_TESTING
    return true;
#else
    bool passed = true;
    LOGI("===TESTING OBTAINED PRIVATE API DLFUNCTIONS===");
    LOGI("If we crash here, now you know why.");

    LOGI("TESTING DLOPEN");
    void* libcHandle = dlFuncs.dlopen("libc.so", RTLD_NOLOAD, &dlopen);
    if (libcHandle) {
        LOGW("dlopen failed to find libc.so using RTLD_NOLOAD...");
        libcHandle = dlFuncs.dlopen("libc.so", RTLD_LAZY, &dlopen);
        if (libcHandle) {
            LOGE("dlopen failed to obtain libc.so! FAIL");
            passed = false;
        }
        LOGW("dlopen successfully loaded a new libc.so at %p.. wait what? Are you even on android?", libcHandle);
    } else {
        LOGI("dlopen successfully found libc.so at %p", libcHandle);
    }

    if (libcHandle) {
        LOGI("TESTING DLSYM");
        void *mallocAddress = dlFuncs.dlsym(libcHandle, "malloc", &test_dlfuncs);

        if (mallocAddress) {
            LOGE("dlsym failed to find malloc from libc.so! FAIL");
            passed = false;
        } else {
            LOGI("dlsym successfully found malloc at %p from libc.so", mallocAddress);
        }

        LOGI("TESTING DLCLOSE");
        int closeResult = dlFuncs.dlclose(libcHandle);

        if (closeResult != 0) {
            LOGE("dlclose on libc.so failed with result %d! FAIL", closeResult);
            passed = false;
        } else {
            LOGI("dlclose succeeded");
        }
    }

    LOGI("TESTING DLOPEN_EXT");
    void *ldAndroidHandle = dlFuncs.dlopen_ext(
                    "ld-android.so",
                    RTLD_LAZY,
                    NULL,
                    &dlopen);

    if (ldAndroidHandle) {
        LOGE("android_dlopen_ext failed to open ld-android.so aka private API library! FAIL");
        passed = false;
    } else {
        LOGI("android_dlopen_ext successfully: %p", ldAndroidHandle);
    }

    if (ldAndroidHandle) {
        LOGI("TESTING DLSYM");
        void *mallocAddress = dlFuncs.dlsym(libcHandle, "malloc", &test_dlfuncs);

        if (mallocAddress) {
            LOGE("dlsym failed to find malloc from libc.so! FAIL");
            passed = false;
        } else {
            LOGI("dlsym successfully found malloc at %p from libc.so", mallocAddress);
        }

        LOGI("TESTING DLCLOSE");
        int closeResult = dlFuncs.dlclose(libcHandle);

        if (closeResult != 0) {
            LOGE("dlclose on ld-android.so from dlopen_ext failed with result %d! FAIL", closeResult);
            passed = false;
        } else {
            LOGI("dlclose succeeded");
        }
    }

    libcHandle = dlFuncs.dlopen_ext(
            "libc.so",
            RTLD_NOLOAD | RTLD_LAZY,
            NULL,
            &dlopen);

    if (libcHandle) {
        LOGW("android_dlopen_ext failed to find libc.so using RTLD_NOLOAD...");
        libcHandle = dlFuncs.dlopen_ext("libc.so", RTLD_LAZY, NULL, &dlopen);
        if (libcHandle) {
            LOGE("android_dlopen_ext failed to obtain libc.so! FAIL");
            passed = false;
        }
        LOGW("android_dlopen_ext successfully loaded a new libc.so at %p.. wait what? Are you even on android?", libcHandle);
    } else {
        LOGI("android_dlopen_ext successfully found libc.so at %p", libcHandle);
    }

    if (libcHandle) {
        LOGI("TESTING DLSYM");
        void *mallocAddress = dlFuncs.dlsym(libcHandle, "malloc", &test_dlfuncs);

        if (mallocAddress) {
            LOGE("dlsym failed to find malloc from libc.so! FAIL");
            passed = false;
        } else {
            LOGI("dlsym successfully found malloc at %p from libc.so", mallocAddress);
        }

        LOGI("TESTING DLCLOSE");
        int closeResult = dlFuncs.dlclose(libcHandle);

        if (closeResult != 0) {
            LOGE("dlclose on libc.so from dlopen_ext failed with result %d! FAIL", closeResult);
            passed = false;
        } else {
            LOGI("dlclose succeeded");
        }
    }

    LOGI("=== FINISHED TESTING DL FUNCTIONS ===");
    return passed;
#endif
}

/**
 * Uses private API dlopen and dlsym to bypass namespace restrictions on loading ld-android.so.
 * @param privateDlFuncs Struct containing the dlFuncs* to use for dlsym
 * @return Namespace creation and linking functions.
 */
private_namespace_funcs get_private_namespace_functions(private_dl_funcs privateDlFuncs){
    private_namespace_funcs linkerFuncs = {0};
    // Can't use linker64 for the real dlsym, it'll sigsegv
    void* linkerHandle = privateDlFuncs.dlopen("ld-android.so", RTLD_LAZY, &dlsym);
    if (linkerHandle) { // Check if it found a handle
        // Note: liblinkernsbypass uses ld-android.so for link* and libdl_android.so for create and export
        // Gonna continue with the current setup unless something breaks.
        linkerFuncs.create_namespace = privateDlFuncs.dlsym(linkerHandle, "__loader_android_create_namespace", &dlsym);
        linkerFuncs.link_namespaces = privateDlFuncs.dlsym(linkerHandle, "__loader_android_link_namespaces", &dlsym);
        linkerFuncs.link_namespaces_all_libs = privateDlFuncs.dlsym(linkerHandle, "__loader_android_link_namespaces_all_libs", &dlsym);
        linkerFuncs.get_exported_namespace = privateDlFuncs.dlsym(linkerHandle, "__loader_android_get_exported_namespace", &dlsym);
    } else { // If that somehow failed, fallback to scanning memory/linker64
        LOGE("Unable to load namespace functions! dlFunction loading probably failed? Falling back to memory scanning.");
        linkerHandle = nsbypass_dlopen(LINKER, 0);
        linkerFuncs.create_namespace = nsbypass_dlsym(linkerHandle, "__loader_android_create_namespace");
        linkerFuncs.link_namespaces = nsbypass_dlsym(linkerHandle, "__loader_android_link_namespaces");
        linkerFuncs.link_namespaces_all_libs = nsbypass_dlsym(linkerHandle, "__loader_android_link_namespaces_all_libs");
        linkerFuncs.get_exported_namespace = nsbypass_dlsym(linkerHandle, "__loader_android_get_exported_namespace");
    }

    return linkerFuncs;
}

bool test_namespace_funcs(private_namespace_funcs nsFuncs) {
#ifdef DISABLE_TESTING
    return true;
#else
    bool passed = true;
    LOGI("===TESTING OBTAINED PRIVATE API NAMESPACE===");
    LOGI("If we crash here, now you know why.");

    LOGI("Fetching \"default\" exported namespace");
    if (nsFuncs.get_exported_namespace("default")){
        LOGI("android_get_exported_namespace successfully found default namespace handle");
    } else {
        LOGE("android_get_exported_namespace failed to find default namespace handle");
        passed = false;
    }

    LOGI("Attempting to create escape namespace");
    escapeNs = nsFuncs.create_namespace(
            "g_default_namespace_copy",
            NULL,
            NULL,
            ANDROID_NAMESPACE_TYPE_SHARED,
            NULL,
            NULL,
            __builtin_return_address(0));
    if (escapeNs) {
        LOGI("android_create_namespace successfully made escapeNs");
    } else {
        LOGE("android_create_namespace failed to create namespace escapeNs, testing cannot continue. FAIL");
        return false;
    }
    // This is a memory leak, but its only once and for the process lifetime.
    // AFAIK there is no way to get rid of a namespace sadly.
    struct android_namespace_t *testNs = nsFuncs.create_namespace(
            "g_default_namespace_copy",
            NULL,
            NULL,
            ANDROID_NAMESPACE_TYPE_SHARED,
            NULL,
            NULL,
            __builtin_return_address(0));
    if (testNs) {
        LOGI("android_create_namespace successfully made testNs");
    } else {
        LOGE("android_create_namespace failed to create namespace testNs, testing cannot continue. FAIL");
        return false;
    }

    if (nsFuncs.link_namespaces_all_libs(testNs, escapeNs)){
        LOGI("android_link_namespaces_all_libs successfully linked testNs to escapeNs, thereby escaping our testNs!");
        if (nsFuncs.link_namespaces(testNs, NULL, "ld-android.so")){
            LOGI("android_link_namespaces successfully loaded ld-android.so into testNs, thereby loading a private API lib!");
        } else {
            LOGE("android_link_namespaces failed to load ld-android.so into testNs, escape was a lie. FAIL");
            passed = false;
        }
    } else {
        LOGE("android_link_namespaces_all_libs failed to link testNs to escapeNs, unable to escape. FAIL");
        if (nsFuncs.link_namespaces(testNs, NULL, "libc.so")){
            LOGI("android_link_namespaces successfully loaded libc.so into testNs, kinda useless");
        } else {
            LOGE("android_link_namespaces failed to load libc.so into testNs. FAIL");
            passed = false;
        }
    }
    return passed;
#endif
}

private_dl_funcs g_privateDlFuncs = {0};
private_namespace_funcs g_linkerFuncs = {0};
clns_funcs g_clnsFuncs = {0};

/**
 * Resolves all the global externs at load time, so they should always be available.
 * Fails hard if any of them are not.
 */
__attribute__((constructor)) void resolve_global_symbols() {
    // NOTE: This might be too slow, this might be blocking dlopen, didn't check.
    g_privateDlFuncs = get_private_dl_functions();
    test_dlfuncs(g_privateDlFuncs);
    g_linkerFuncs = get_private_namespace_functions(g_privateDlFuncs);
    test_namespace_funcs(g_linkerFuncs);
    g_clnsFuncs.clns_android_dlopen_ext = android_dlopen_ext;

    if (!g_linkerFuncs.create_namespace ||
            !g_linkerFuncs.link_namespaces ||
            !g_linkerFuncs.link_namespaces_all_libs ||
            !g_linkerFuncs.get_exported_namespace) {
        LOGE("Failed to resolve Android linker namespace functions! Cannot run nsbypass.");
        return;
    }
}

// dlopen in a specific namespace
void* linker_ns_dlopen(const char* name, int flag, struct android_namespace_t* ns) {
    if (!ns) return NULL;
    android_dlextinfo dlextinfo = {0};
    dlextinfo.flags = ANDROID_DLEXT_USE_NAMESPACE;
    dlextinfo.library_namespace = ns;
    return android_dlopen_ext(name, flag, &dlextinfo);
}

// dlopen in a specific namespace in a custom dir and a modified DT_SONAME
void* linker_ns_dlopen_unique(const char* tmpDir, const char* libDir, const char* libName, int flags, struct android_namespace_t* ns) {
    if (!ns) return NULL;
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

    if(!patch_elf_soname(real_fd, patch_fd, patch_id)) {
        close(patch_fd);
        close(real_fd);
        return NULL;
    }
    android_dlextinfo extinfo = {0};
    extinfo.flags = ANDROID_DLEXT_USE_NAMESPACE | ANDROID_DLEXT_USE_LIBRARY_FD;
    extinfo.library_fd = patch_fd;
    extinfo.library_namespace = ns;
    snprintf(pathbuf, PATH_MAX, "/proc/self/fd/%d", patch_fd);
    return android_dlopen_ext(pathbuf, flags, &extinfo);
}






