//
// Created by tom on 8/22/26.
//

#include <unistd.h>
#include "platform.h"
#include "nsbypass_t.h"

#ifndef AMETHYST_NSBYPASS_H
#define AMETHYST_NSBYPASS_H

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

/*
 * Some basics are in order.
 * See https://source.android.com/docs/core/architecture/vndk/linker-namespace to find what this
 * bypasses.
 *
 * There are two namespaces you can reliably access without creating your own. the classloader
 * namespace, and the default namespace
 * https://cs.android.com/android/platform/superproject/+/329d792f6d5e33e8a6fc5a02809c795ce17774ab:art/libnativeloader/library_namespaces.cpp
 * Classloader namespace is names like clns-XX with XX being integers. This is provided to every
 * application on startup automatically and looks through your nativeLibraryDir and specified
 * libraries defined as "public API" by android.
 *
 * g_default_namespace is the private API namespace where you can access the private API libs.
 * It's also the default if it wasn't obvious.
 *
 * By using &dlopen as the caller_addr, we trick the linker into thinking we are calling from
 * libdl.so thereby letting us dlopen as if we were in the default namespace.
 *
 */

// Dirs where system libs are stored.
#define SYSTEM_LIBS_PATH "/system/:/system_ext/:/data/:/vendor/:/apex/"

/**
 * A namespace that has access to SYSTEM_LIBS_PATH.
 */
extern struct android_namespace_t* escapeNs;

/**
 * dlopen, but you can specify which namespace to open the library in.
 * @param ns Specified namespace to open the library in
 */
void* linker_ns_dlopen(const char* name, int flag, struct android_namespace_t* ns);
/**
 * linker_ns_dlopen, but it patches the SONAME to another one in case another namespace that your
 * specified namespace is linked to loaded it but you want another copy
 * (like libvulkan.so, the vulkan loader, to load custom driver)
 * @param tmpDir Directory to "store" the patched file (may not be saved to disk, its a gamble)
 * @param libDir Directory of SONAME
 * @param libName SONAME of library to patch & dlopen
 * @param flag dlopen flags
 * @param ns Specified namespace to open the library in
 */
void* linker_ns_dlopen_unique(const char* tmpDir, const char* libDir, const char* libName, int flag, struct android_namespace_t* ns);

/**
 * Create a new android linker namespace (not linux namespaces, these only affect the linker)
 * @param name Name of namespace
 * @param ld_library_path Prioritized path to look for SONAMEs
 * @param default_library_path Default path to look for SONAMEs
 * @param type Namespace type flags, see enums in nsbypass_t.h
 * @param permitted_when_isolated_path See ANDROID_NAMESPACE_TYPE_ISOLATED
 * @param parent_namespace Namespace to inherit loaded SONAMEs and search paths from
 * @param caller_addr __builtin_return_address(0) for current namespace or &dlopen for default
 * @return The created namespace
 */
struct android_namespace_t* private_create_namespace(
        const char* name,
        const char* ld_library_path,
        const char* default_library_path,
        uint64_t type,
        const char* permitted_when_isolated_path,
        struct android_namespace_t* parent_namespace,
        const void* caller_addr);

/**
 * Allow a namespace to look in another namespace for specific SONAMEs. Does not affect search paths.
 * @param from Namespace that will be allowed to look in "to" for the specified "shared_libs_sonames"
 * @param to Additional namespace that "from" is allowed to look into for the specified
 * "shared_libs_sonames". If NULL, it defaults to default namespace.
 * @param shared_libs_sonames Colon-seperated list of SONAMEs (NOT PATHS)
 * @return Whether successful.
 */
bool private_link_namespaces(
        struct android_namespace_t* from,
        struct android_namespace_t* to,
        const char* shared_libs_sonames);

/**
 * Allow a namespace to look in another namespace for SONAMEs. Does not affect search paths.
 * @param from Namespace that will be allowed to look in "to"
 * @param to Additional namespace that "from" is allowed to look into, unlike
 * private_link_namespaces, passing NULL results in an error.
 * @return Whether successful.
 */
bool private_link_namespaces_all_libs(
        struct android_namespace_t* from,
        struct android_namespace_t* to);

/*
 * Some namespaces are
 * "(default)" for g_default_namespace
 * "sphal" "vendor" "default" for vendor namespace, used in android_load_sphal_library
 *
 * Android doesn't let you see the ld.config.txt file so this is pretty much a guessing game.
 * See https://cs.android.com/android/platform/superproject/+/android-latest-release:system/linkerconfig/
 */
/**
 * Get an exported namespace
 * @param name The namespace to get
 * @return The namespace you got
 */
struct android_namespace_t* private_get_exported_namespace(
        const char* name);

/**
 * Pretty much just the normal dlclose, you don't need this, it's here if you want it tho
 */
int private_dlclose(void* handle);

/**
 * dlopen but now you can edit caller_addr
 * @param caller_addr __builtin_return_address(0) for current namespace or &dlopen for default
 * @return
 */
void* private_dlopen(
        const char* filename,
        int flags,
        const void* caller_addr);

/**
 * private_dlopen but now you can edit extinfo
 * @param extinfo See dlext.h in the NDK for whats android_dlextinfo
 * @return
 */
void* private_dlopen_ext(
        const char* filename,
        int flags,
        const android_dlextinfo* extinfo,
        const void* caller_addr);

/**
 * dlsym but now you can edit caller_addr
 * @param caller_addr __builtin_return_address(0) for current namespace or &dlsym for default
 * @return
 */
void* private_dlsym(
        void* handle,
        const char* symbol,
        const void* caller_addr);

#endif //AMETHYST_NSBYPASS_H
