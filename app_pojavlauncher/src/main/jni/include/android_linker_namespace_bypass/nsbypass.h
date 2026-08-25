//
// Created by tom on 8/22/26.
//

#include <android/dlext.h>
#include <sys/unistd.h>
#include <sys/mman.h>

#ifndef AMETHYST_NSBYPASS_H
#define AMETHYST_NSBYPASS_H

// Aligns pointer to page size so mprotect properly gets the whole page
static void *align_ptr_to_pagesize(void *ptr) {
    return (void *)(((uintptr_t)ptr) & ~(getpagesize() - 1));
}
#if (defined __aarch64__)
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
    // Reprotecting the functions removes (BTI) protection from indirect jumps
    // while technically out of scope of "find_branch_label", this is just
    // cleaner overall.
    if (mprotect(t, page_size, PROT_WRITE | PROT_READ | PROT_EXEC) != 0) {
        LOGW("Failed to remove BTI protection from private API page. This might fail..  %p", t);
    }
    return t;
}
#endif
// https://cs.android.com/android/platform/superproject/+/329d792f6d5e33e8a6fc5a02809c795ce17774ab:bionic/libc/platform/bionic/dlext_namespaces.h;l=120-134
typedef struct android_namespace_t* (*private_create_namespace_t)(
        const char* name,
        const char* ld_library_path,
        const char* default_library_path,
        uint64_t type,
        const char* permitted_when_isolated_path,
        struct android_namespace_t* parent);

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
    ANDROID_NAMESPACE_TYPE_REGULAR = 0,
    ANDROID_NAMESPACE_TYPE_ISOLATED = 1,
    ANDROID_NAMESPACE_TYPE_SHARED = 2,
    ANDROID_NAMESPACE_TYPE_EXEMPT_LIST_ENABLED = 0x08000000,
    ANDROID_NAMESPACE_TYPE_ALSO_USED_AS_ANONYMOUS = 0x10000000,
    ANDROID_NAMESPACE_TYPE_SHARED_ISOLATED = ANDROID_NAMESPACE_TYPE_SHARED | ANDROID_NAMESPACE_TYPE_ISOLATED,
};

typedef struct {
    private_create_namespace_t create_namespace;
    private_link_namespaces_t link_namespaces;
    private_link_namespaces_all_libs_t link_namespace_all_libs;
    private_get_exported_namespace_t get_exported_namespace;
} private_linker_funcs;

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

extern clns_funcs g_clnsFuncs;
extern private_linker_funcs g_linkerFuncs;
extern private_dl_funcs g_privateDlFuncs;

void* linker_ns_dlopen(const char* name, int flag, struct android_namespace_t* ns);
void* linker_ns_dlopen_unique(const char* tmpDir, const char* libDir, const char* libName, int flag, struct android_namespace_t* ns);


#endif //AMETHYST_NSBYPASS_H
