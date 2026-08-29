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

#include "fasthook/nsbypass_dlfcn.h"

#include "platform.h"

// Creating this needed to base off of pojav and liblinkernsbypass. These are my conclusions
// after studying those implementations

/*
 * The Pojav Implementation
 *
 * On startup, turnip, libvulkan, and a library hooking android_dlopen_ext/android_load_sphal_library
 * are loaded into "driver_namespace" which is
 * local_android_create_namespace("pojav-driver",
 *                                 /system/lib64/:<nativeLibraryDir>,
 *                                 /system/lib64/:<nativeLibraryDir>,
 *                                 ANDROID_NAMESPACE_TYPE_SHARED_ISOLATED,
 *                                 "/system/:/data/:/vendor/:/apex/", // Bugged cause some phones grab from /system_ext
 *                                 NULL);
 * Turnip is loaded and then the handle is saved, which is returned by the hook when libvulkan
 * tries requesting a android_dlopen_ext/android_load_sphal_library of vulkan.<soc_codename>.so
 * aka stock vulkan driver
 *
 * The handle for libvulkan is then saved as env var VULKAN_PTR which is patched into LWJGL3
 *
 * Behaviour is buggy when called multiple times to create a same named namespace (which it was)
 */

/*
 * The libadrenotools implementation
 *
 * Upon calling adrenotools_open_libvulkan, a namespace "hookNs" is made with a library hooking
 * android_dlopen_ext/android_load_sphal_library and then libvulkan.so is loaded.
 *
 * Upon libvulkan.so calling android_dlopen_ext/android_load_sphal_library, the hook intercepts and
 * creates a namespace "driverNs" and provided extinfo by those aforementioned dlopen methods is
 * used to get the parent namespace for namespace created for the driver.
 *
 * More hooks is loaded inside "driverNs" and then subsequently turnip.
 *
 * The hooks are more file redirects for the driver to work properly + configuration.
 *
 * This is closer to how android itself loads it. Actually, this is how xiaomi loads its hooks.
 * See libmivk.so, wherever you may find a dump of it online.
 *
 * Only difference is they split the hook implementation and actual hook functions seperately to go
 * around some bug or something, never encountered one though, probably cause I don't use the
 * plt/got to resolve android_dlopen_ext but they do.
 */

static private_dl_funcs s_privateDlFuncs = {0};
static private_namespace_funcs s_linkerFuncs = {0};
struct android_namespace_t* escapeNs;

// Aligns pointer to page size so mprotect properly gets the whole page
static void *align_ptr_to_pagesize(void *ptr) {
    return (void *)(((uintptr_t)ptr) & ~(getpagesize() - 1));
}
#if (defined __aarch64__)
#include <sys/mman.h>

// The logic of doing this stems from
// https://cs.android.com/android/platform/superproject/+/329d792f6d5e33e8a6fc5a02809c795ce17774ab:bionic/libdl/libdl.cpp;drc=a493fe415304efd19f089cbfc7d78c9db7d7263c;l=86-114
// Where the dl* functions all just call __loader variants

// This is just coincidentally convenient for ARM64 opcodes.
// The other arches don't find this approach very simple.

/* upper 6 bits of an ARM64 instruction are the instruction name */
#define OP_MS 0b11111100000000000000000000000000
/* Branch Label instruction opcode and immediate mask */
#define BL_OP 0b10010100000000000000000000000000
#define BL_IM 0b00000011111111111111111111111111

static void* find_branch_label(void* func_start) {
    long page_size = sysconf(_SC_PAGESIZE);
    // Some devices (MIUI) ship with --X mapping for executables so work around that
    if (mprotect(align_ptr_to_pagesize(func_start), page_size, PROT_READ | PROT_EXEC)){
        LOGW("Failed to set readable bit on provided func! This might fail..  %p", func_start);
    }
    uint32_t* bl_addr = func_start;
    // Search for the "branch to label" opcode
    while((*bl_addr & OP_MS) != BL_OP) {
        bl_addr++; // walk through memory until we find it or die
    }
    // Offset the address to find where the "branch to label" instrunction
    // points to.
    void* t = ((char*)bl_addr) + (*bl_addr & BL_IM) * 4;
    // Reprotecting the functions removes (BTI) protection from indirect jumps.
    // While technically out of scope of "find_branch_label", this is just
    // cleaner overall.
    if (mprotect(align_ptr_to_pagesize(t), page_size, PROT_WRITE | PROT_READ | PROT_EXEC) != 0) {
        LOGW("Failed to remove BTI protection from private API page. This might fail..  %p", t);
    }
    return t;
}
#endif

/**
 * Fetches the function pointers in three ways, in descending order of priority.\n
 *
 *  - (aarch64 only) Using &dlopen, scan the assembly instructions until it finds the private API
 *  call and uses the pointers from there. This is likely to be libdl.so being scanned.\n
 *  - Public API dlopen & dlsym on libdl.so for the private API pointers\n
 *  - Scan /proc/self/maps for an r-xp instance of linker64 then scan that instance for pointers\n
 * @return Private API versions of dlFunc*
 */
private_dl_funcs get_private_dl_functions()
{
    // TODO: Verify if this works on Android 8 or lower, they have a weird thing
    // that doesn't exactly just have __loader_* laying around so am not sure about it.
    private_dl_funcs dlFuncs = {0};
    // First attempt the normal libadrenotools method (ARM64 shenanigans)
#if (defined __aarch64__)
    LOGI("Obtaining private API dlFuncs via BTI instruction from libdl.so");
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
    LOGW("Obtaining dlFuncs via branch label instruction search failed, this is not supposed to happen on aarch64. Falling back.");
#endif

    void* linkerHandle = dlopen("libdl.so", RTLD_LAZY);
    // eat any stale ones
    char *error = dlerror();
    if (error) LOGI("Stale dlerror: %s", error);
    LOGI("Obtaining private API dlFuncs via dlopen/dlsym, haha funny, this resolves to linker64 symbol ptrs btw");
    if (!dlFuncs.dlopen) dlFuncs.dlopen = dlsym(linkerHandle, "__loader_dlopen");
    if (!dlFuncs.dlopen_ext) dlFuncs.dlopen_ext = dlsym(linkerHandle, "__loader_android_dlopen_ext");
    if (!dlFuncs.dlclose) dlFuncs.dlclose = dlsym(linkerHandle, "__loader_dlclose");
    if (!dlFuncs.dlsym) dlFuncs.dlsym = dlsym(linkerHandle, "__loader_dlsym");
    if (error) {
        LOGW("dlerror in using public API to acquire private API ptrs:  %s", error);
    }
    if (dlFuncs.dlopen != NULL &&
            dlFuncs.dlopen_ext != NULL &&
            dlFuncs.dlclose != NULL &&
            dlFuncs.dlsym != NULL) {
        return dlFuncs;
    }

    // Now fallback to full memory scans
    // This is unreliable so, more reason for mixing.
    LOGW("Obtaining private API dlFuncs via memory scanning, this isn't very reliable.");
    linkerHandle = nsbypass_dlopen(LINKER, 0); // NEVER dlsym this handle, SIGSEGV will murder you.zzzzz
    if (!linkerHandle) return dlFuncs;
    if (!dlFuncs.dlopen) dlFuncs.dlopen = nsbypass_dlsym(linkerHandle, "__loader_dlopen");
    if (!dlFuncs.dlopen_ext) dlFuncs.dlopen_ext = nsbypass_dlsym(linkerHandle, "__loader_android_dlopen_ext");
    if (!dlFuncs.dlclose) dlFuncs.dlclose = nsbypass_dlsym(linkerHandle, "__loader_dlclose");
    if (!dlFuncs.dlsym) dlFuncs.dlsym = nsbypass_dlsym(linkerHandle, "__loader_dlsym");

    return dlFuncs;
}

/**
 * Tests the provided dl functions to see if they work.
 * This way, any SIGSEGV or other stuff hard crashes early.
 * @param dlFuncs
 * @returns False if even 1 test fails, otherwise true.
 */
bool test_dlfuncs(
        private_dl_funcs dlFuncs)
{
#ifndef ENABLE_TESTS
    return true;
#else
    bool passed = true;
    LOGI("===TESTING OBTAINED PRIVATE API DLFUNCTIONS===");
    LOGI("If we crash here, now you know why.");

    LOGI("TESTING DLOPEN ON LIBDL.SO");
    void* libdlHandle = dlFuncs.dlopen("libdl.so", RTLD_NOLOAD, &dlopen);
    if (!libdlHandle) {
        LOGE("dlopen failed to obtain libdl.so! FAIL");
        passed = false;
    }

    if (libdlHandle) {
        LOGI("TESTING DLSYM ON LIBDL.SO TO FIND dlopen");
        void *dlopenAddress = dlFuncs.dlsym(libdlHandle, "dlopen", &dlopen);

        if (!dlopenAddress) {
            LOGE("dlsym failed to find dlopen from libdl.so! FAIL");
            passed = false;
        } else {
            LOGI("dlsym successfully found dlopen at %p from libdl.so", dlopenAddress);
        }

        LOGI("TESTING DLCLOSE ON LIBDL");
        int closeResult = dlFuncs.dlclose(libdlHandle);

        if (closeResult != 0) {
            LOGE("dlclose on libc.so failed with result %d! FAIL", closeResult);
            passed = false;
        } else {
            LOGI("dlclose succeeded");
        }
    }

    LOGI("TESTING DLOPEN_EXT ON LD-ANDROID.SO AKA PRIVATE API");
    void *ldAndroidHandle = dlFuncs.dlopen_ext(
                    "ld-android.so",
                    RTLD_LAZY,
                    NULL,
                    &dlopen);

    if (!ldAndroidHandle) {
        LOGE("android_dlopen_ext failed to open ld-android.so aka private API library! FAIL");
        passed = false;
        LOGI("TESTING DLOPEN ON LD-ANDROID.SO");
        ldAndroidHandle = dlFuncs.dlopen(
                "ld-android.so",
                RTLD_LAZY,
                &dlopen);
        if (ldAndroidHandle) {
            LOGW("dlopen opened ld-android.so aka private API library..android_dlopen_ext is likely invalid.");
        } else LOGE("dlopen failed to open ld-android.so. FAIL");
    } else {
        LOGI("android_dlopen_ext successfully: %p", ldAndroidHandle);
    }

    if (ldAndroidHandle) {
        LOGI("TESTING DLSYM ON LD-ANDROID.SO TO FIND __loader_android_dlopen_ext");
        void *dlopen_ext = dlFuncs.dlsym(ldAndroidHandle, "__loader_android_dlopen_ext", &dlsym);

        if (!dlopen_ext) {
            LOGE("dlsym failed to find __loader_android_dlopen_ext from ld-android.so! FAIL");
            passed = false;
        } else {
            LOGI("dlsym successfully found __loader_android_dlopen_ext at %p from ld-android.so", dlopen_ext);
        }

        LOGI("TESTING DLCLOSE ON LD-ANDROID.SO");
        int closeResult = dlFuncs.dlclose(ldAndroidHandle);

        if (closeResult != 0) {
            LOGE("dlclose on ld-android.so from dlopen_ext failed with result %d! FAIL", closeResult);
            passed = false;
        } else {
            LOGI("dlclose succeeded");
        }
    }

    LOGI("TESTING DLOPEN_EXT ON LIBC.SO");
    void* libcHandle = dlFuncs.dlopen_ext(
            "libc.so",
            RTLD_NOLOAD,
            NULL,
            &dlopen);

    if (!libcHandle) {
        LOGW("android_dlopen_ext failed to find libc.so using RTLD_NOLOAD...");
        libcHandle = dlFuncs.dlopen_ext("libc.so", RTLD_LAZY, NULL, &dlopen);
        if (!libcHandle) {
            LOGE("android_dlopen_ext failed to obtain libc.so! FAIL");
            passed = false;
        }
        LOGW("android_dlopen_ext successfully loaded a new libc.so at %p.. wait what? Are you even on android?", libcHandle);
    } else {
        LOGI("android_dlopen_ext successfully found libc.so at %p", libcHandle);
    }

    if (libcHandle) {
        LOGI("TESTING DLSYM");
        void *mallocAddress = dlFuncs.dlsym(libcHandle, "malloc", &dlsym);

        if (!mallocAddress) {
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
private_namespace_funcs get_private_namespace_functions(
        private_dl_funcs privateDlFuncs)
{
    private_namespace_funcs linkerFuncs = {0};
    // ld-android.so and linker64 provide the same SONAME in readelf.
    // ld-android.so is not present in /proc/self/maps so it cannot be found by the memory scanning
    // Can't use linker64 for dlsym, it'll sigsegv
    void* linkerHandle = privateDlFuncs.dlopen_ext("ld-android.so", RTLD_LAZY, NULL, &dlsym);
    if (linkerHandle) { // Check if it found a handle
        LOGI("Obtaining linker namespace funcs via obtained dlFuncs");
        // Note: liblinkernsbypass uses ld-android.so for link* and libdl_android.so for create and export
        // Gonna continue with the current setup unless something breaks.
        linkerFuncs.create_namespace = privateDlFuncs.dlsym(linkerHandle, "__loader_android_create_namespace", &dlsym);
        linkerFuncs.link_namespaces = privateDlFuncs.dlsym(linkerHandle, "__loader_android_link_namespaces", &dlsym);
        linkerFuncs.link_namespaces_all_libs = privateDlFuncs.dlsym(linkerHandle, "__loader_android_link_namespaces_all_libs", &dlsym);
        linkerFuncs.get_exported_namespace = privateDlFuncs.dlsym(linkerHandle, "__loader_android_get_exported_namespace", &dlsym);
    } else { // If that somehow failed, fallback to scanning memory/linker64
        // Note that "dlsym" is not actually dlsym here, it is purely memory scanning.
        LOGE("Unable to load namespace functions! dlFunction loading probably failed? Falling back to memory scanning.");
        linkerHandle = nsbypass_dlopen(LINKER, 0);
        linkerFuncs.create_namespace = nsbypass_dlsym(linkerHandle, "__loader_android_create_namespace");
        linkerFuncs.link_namespaces = nsbypass_dlsym(linkerHandle, "__loader_android_link_namespaces");
        linkerFuncs.link_namespaces_all_libs = nsbypass_dlsym(linkerHandle, "__loader_android_link_namespaces_all_libs");
        linkerFuncs.get_exported_namespace = nsbypass_dlsym(linkerHandle, "__loader_android_get_exported_namespace");
    }

    return linkerFuncs;
}

/**
 * Tests the provided namespace functions to see if they work
 * This way, any SIGSEGV or other stuff hard crashes early.
 * Leaks memory.
 * @returns False if even 1 test fails, otherwise true.
 */
bool test_namespace_funcs(private_namespace_funcs nsFuncs)
{
#ifndef ENABLE_TESTS
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
            SYSTEM_LIBS_PATH,
            ANDROID_NAMESPACE_TYPE_SHARED,
            SYSTEM_LIBS_PATH,
            NULL,
            &dlopen); // This address is libdl.so, which should resolve to g_default_namespace
    if (escapeNs) {
        LOGI("android_create_namespace successfully made escapeNs");
    } else {
        LOGE("android_create_namespace failed to create namespace escapeNs, testing cannot continue. FAIL");
        return false;
    }
    // This is a memory leak, but its only once and for the process lifetime. So might as well make
    // it useful if it's gonna be leaking.
    // AFAIK there is no way to get rid of a namespace sadly. If you know how, make an issue.
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

/**
 * Resolves all the global externs at load time, so they should always be available.
 * Fails hard if any of them are not.
 */
__attribute__((constructor)) static void resolve_global_symbols()
{
#ifndef FORCE_LOAD_PREANDROID_7
    if (is_android_6_or_lower()){
        // Do not allow being loaded there, I don't know what we would do but it's definitely not
        // gonna be safe down there.

        LOGI("Running on unsupported android version, killing process. Recompile with -DFORCE_LOAD_PREANDROID_7 to continue.");
        _exit(120);
    }
#endif
    // NOTE: Tests are fast enough for release actually, but I'll disable them anyway.
    s_privateDlFuncs = get_private_dl_functions();
    test_dlfuncs(s_privateDlFuncs);
    s_linkerFuncs = get_private_namespace_functions(s_privateDlFuncs);
    test_namespace_funcs(s_linkerFuncs);

    if (!s_linkerFuncs.create_namespace ||
            !s_linkerFuncs.link_namespaces ||
            !s_linkerFuncs.link_namespaces_all_libs ||
            !s_linkerFuncs.get_exported_namespace) {
        LOGE("Failed to resolve Android linker namespace functions! Cannot run nsbypass.");
        exit(121);
    }
    // Create if not yet made, which is the case if tests are disabled
    if (!escapeNs){
        escapeNs = s_linkerFuncs.create_namespace(
                "g_default_namespace_copy",
                NULL,
                SYSTEM_LIBS_PATH,
                ANDROID_NAMESPACE_TYPE_SHARED,
                SYSTEM_LIBS_PATH,
                NULL,
                &dlopen);
        if (!escapeNs) {
            LOGD("Failed to create escapeNs!");
            exit(122); // idk it felt like a 120
        }
    }
}

/*
  Note:
    You may have found that escapeNs does not have search paths within your app native dir and thus
    cannot properly resolve any NEEDED's from there. Make your own namespace. I suggest creating
    a SHARED ns with parent escapeNs. Simply add your nativeLibraryDir to default_library_path and
    it will be appended to the list that it will search for NEEDEDs!

    or yknow, preload them. dlopen them before the lib that needs them, assuming its not isolated.
 */

// dlopen in a specific namespace
void* linker_ns_dlopen(
        const char* name,
        int flag,
        struct android_namespace_t* ns)
{
    if (!ns) return NULL;
    android_dlextinfo dlextinfo = {0};
    dlextinfo.flags = ANDROID_DLEXT_USE_NAMESPACE;
    dlextinfo.library_namespace = ns;
    return android_dlopen_ext(name, flag, &dlextinfo);
}

// This technically has a chance of collision but that only happens after 4096 and even then would
// be rare. So this is probably fiiiine. If you wanna fix it, fix patch_elf_soname being limited
// to overwriting instead of prepending.
static uint16_t patch_id;
void* linker_ns_dlopen_unique(
        const char* tmpDir,
        const char* libDir,
        const char* libName,
        int flags,
        struct android_namespace_t* ns)
{
    if (!ns) return NULL;
    char pathbuf[PATH_MAX];
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
    patch_id++;
    android_dlextinfo extinfo = {0};
    extinfo.flags = ANDROID_DLEXT_USE_NAMESPACE | ANDROID_DLEXT_USE_LIBRARY_FD;
    extinfo.library_fd = patch_fd;
    extinfo.library_namespace = ns;
    snprintf(pathbuf, PATH_MAX, "/proc/self/fd/%d", patch_fd);
    return android_dlopen_ext(pathbuf, flags, &extinfo);
}

// Here in case we want to add any other behaviour. Pointers alone are janky.

struct android_namespace_t* private_create_namespace(
        const char* name,
        const char* ld_library_path,
        const char* default_library_path,
        uint64_t type,
        const char* permitted_when_isolated_path,
        struct android_namespace_t* parent_namespace,
        const void* caller_addr)
{
    return s_linkerFuncs.create_namespace(
            name,
            ld_library_path,
            default_library_path,
            type,
            permitted_when_isolated_path,
            parent_namespace,
            caller_addr);
}

bool private_link_namespaces(
        struct android_namespace_t* from,
        struct android_namespace_t* to,
        const char* shared_libs_sonames)
{
    return s_linkerFuncs.link_namespaces(
            from,
            to,
            shared_libs_sonames);
}

bool private_link_namespaces_all_libs(
        struct android_namespace_t* from,
        struct android_namespace_t* to)
{
    return s_linkerFuncs.link_namespaces_all_libs(
            from,
            to);
}

struct android_namespace_t* private_get_exported_namespace(
        const char* name)
{
    return s_linkerFuncs.get_exported_namespace(
            name);
}

int private_dlclose(void* handle)
{
    return s_privateDlFuncs.dlclose(handle);
}

void* private_dlopen(
        const char* filename,
        int flags,
        const void* caller_addr)
{
    return s_privateDlFuncs.dlopen(
            filename,
            flags,
            caller_addr);
}

void* private_dlopen_ext(
        const char* filename,
        int flags,
        const android_dlextinfo* extinfo,
        const void* caller_addr)
{
    return s_privateDlFuncs.dlopen_ext(
            filename,
            flags,
            extinfo,
            caller_addr);
}

void* private_dlsym(
        void* handle,
        const char* symbol,
        const void* caller_addr)
{
    return s_privateDlFuncs.dlsym(
            handle,
            symbol,
            caller_addr);
}



