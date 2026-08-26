//
// Created by tom on 8/26/26.
//

#include <android/dlext.h>

#ifndef AMETHYST_NSBYPASS_T_H
#define AMETHYST_NSBYPASS_T_H
// https://cs.android.com/android/platform/superproject/+/android-9.0.0_r1:bionic/linker/dlfcn.cpp;l=48-68
typedef struct android_namespace_t* (*private_create_namespace_t)(
        const char* name,
        const char* ld_library_path,
        const char* default_library_path,
        uint64_t type,
        const char* permitted_when_isolated_path,
        struct android_namespace_t* parent_namespace,
        const void* caller_addr);

typedef bool (*private_link_namespaces_t)(
        struct android_namespace_t* from,
        struct android_namespace_t* to,
        const char* shared_libs_sonames);

typedef bool (*private_link_namespaces_all_libs_t)(
        struct android_namespace_t* from,
        struct android_namespace_t* to);

typedef struct android_namespace_t* (*private_get_exported_namespace_t)(
        const char* name);

// https://cs.android.com/android/platform/superproject/+/329d792f6d5e33e8a6fc5a02809c795ce17774ab:bionic/linker/dlfcn.cpp;drc=fda4c10ddf33a1c4cb56c58fae98dd9c2239fdc9;l=82-85
typedef int (*private_dlclose_function_t)(
        void *handle);

typedef void *(*private_dlopen_function_t)(
        const char* filename,
        int flags,
        const void* caller_addr);

typedef void *(*private_dlsym_function_t)(
        void* handle,
        const char* symbol,
        const void* caller_addr);

// http://cs.android.com/android/platform/superproject/+/329d792f6d5e33e8a6fc5a02809c795ce17774ab:bionic/linker/dlfcn.cpp;drc=fda4c10ddf33a1c4cb56c58fae98dd9c2239fdc9;l=58-61
// Just pass __builtin_return_address(0); for caller_addr
// extinfo is nullable and doing so is equivalent to calling __loader_dlopen
typedef void *(*private_dlopen_ext_function_t)(const char* filename,
        int flags,
        const android_dlextinfo* extinfo,
        const void* caller_addr);

// https://cs.android.com/android/platform/superproject/+/329d792f6d5e33e8a6fc5a02809c795ce17774ab:bionic/libdl/libdl.cpp;drc=a493fe415304efd19f089cbfc7d78c9db7d7263c;l=135-138
typedef void *(*android_dlopen_ext_t)(
        const char* filename,
        int flag,
        const android_dlextinfo* extinfo);

// https://cs.android.com/android/platform/superproject/+/android-latest-release:bionic/libdl/libdl_android.cpp;drc=8e5de06bc59b02641a9fb4a86f921f9534a3bef5;l=117-119
typedef struct android_namespace_t *(*android_get_exported_namespace_t)(
        const char* name);

// https://cs.android.com/android/platform/superproject/+/0a492a4685377d41fef2b12e9af4ebfa6feef9c2:art/libnativeloader/include/nativeloader/dlext_namespaces.h;l=25;bpv=1;bpt=1
enum {
    /* A regular namespace is the namespace with a custom search path that does
     * not impose any restrictions on the location of native libraries.
     */
    ANDROID_NAMESPACE_TYPE_REGULAR = 0,

    /* An isolated namespace requires all the libraries to be on the search path
     * or under permitted_when_isolated_path. The search path is the union of
     * ld_library_path and default_library_path.
     */
    ANDROID_NAMESPACE_TYPE_ISOLATED = 1,

    /* The shared namespace clones the list of libraries of the caller namespace upon creation
     * which means that they are shared between namespaces - the caller namespace and the new one
     * will use the same copy of a library if it was loaded prior to android_create_namespace call.
     *
     * Note that libraries loaded after the namespace is created will not be shared.
     *
     * Shared namespaces can be isolated or regular. Note that they do not inherit the search path nor
     * permitted_path from the caller's namespace.
     */
    ANDROID_NAMESPACE_TYPE_SHARED = 2,

    /* This flag instructs linker to enable exempt-list workaround for the namespace.
     * See http://b/26394120 for details.
     */
    ANDROID_NAMESPACE_TYPE_EXEMPT_LIST_ENABLED = 0x08000000,

    /* This flag instructs linker to use this namespace as the anonymous
     * namespace. The anonymous namespace is used in the case when linker cannot
     * identify the caller of dlopen/dlsym. This happens for the code not loaded
     * by dynamic linker; for example calls from the mono-compiled code. There can
     * be only one anonymous namespace in a process. If there already is an
     * anonymous namespace in the process, using this flag when creating a new
     * namespace causes an error.
     */
    ANDROID_NAMESPACE_TYPE_ALSO_USED_AS_ANONYMOUS = 0x10000000,

    ANDROID_NAMESPACE_TYPE_SHARED_ISOLATED =
    ANDROID_NAMESPACE_TYPE_SHARED | ANDROID_NAMESPACE_TYPE_ISOLATED,
};
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

// These are pointers which are likely to be hooked by whoever is using this library.
typedef struct {
    android_dlopen_ext_t clns_android_dlopen_ext;
} clns_funcs;

#endif //AMETHYST_NSBYPASS_T_H
