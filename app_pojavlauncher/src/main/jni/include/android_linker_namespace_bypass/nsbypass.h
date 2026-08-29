//
// Created by tom on 8/22/26.
//

#include <unistd.h>
#include "nsbypass_t.h"

#ifndef AMETHYST_NSBYPASS_H
#define AMETHYST_NSBYPASS_H

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
 * libraries defined as "public API" by android. This is what you have been running in the entire
 * time.
 *
 *
 * g_default_namespace is the private API namespace where you can access the private API libs.
 * It's also the default if it wasn't obvious.
 * https://android.googlesource.com/platform/bionic/%2B/1ffec1cc4d0e283bb1ff6f49843769a3493b8d73/linker/dlfcn.cpp#294
 * It is typically accessed by passing &dlopen as the caller_addr, to trick the linker into
 * thinking we are calling from the default namespace.
 *
 */

// Dirs where system libs are stored.
#define SYSTEM_LIBS_PATH "/system/:/system_ext/:/data/:/vendor/:/apex/"

/**
 * A namespace that has access to SYSTEM_LIBS_PATH.
 * Fun fact: bylaws/liblinkernsbypass coined this term
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

// Structs to store pointers in

// This does not include __loader_android_init_anonymous_namespace
// because its useless and about to be deleted.
// https://cs.android.com/android/platform/superproject/+/329d792f6d5e33e8a6fc5a02809c795ce17774ab:bionic/linker/linker.cpp;l=2448-2449
typedef struct {
    private_create_namespace_t create_namespace;
    private_link_namespaces_t link_namespaces;
    private_link_namespaces_all_libs_t link_namespaces_all_libs;
    private_get_exported_namespace_t get_exported_namespace;
} private_namespace_funcs;

typedef struct {
    private_dlopen_function_t dlopen;
    private_dlopen_ext_function_t dlopen_ext;
    private_dlclose_function_t dlclose;
    private_dlsym_function_t dlsym;
} private_dl_funcs;


#endif //AMETHYST_NSBYPASS_H
