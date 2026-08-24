//
// Created by tom on 8/22/26.
//

#ifndef AMETHYST_NSBYPASS_H
#define AMETHYST_NSBYPASS_H

// Aligns pointer to page size so mprotect properly gets the whole page
static void *align_ptr(void *ptr) {
    return (void *)(((uintptr_t)ptr) & ~(getpagesize() - 1));
}

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
    if (mprotect(align_ptr(func_start), page_size, PROT_READ | PROT_EXEC)){
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

// https://cs.android.com/android/platform/superproject/+/329d792f6d5e33e8a6fc5a02809c795ce17774ab:bionic/libc/platform/bionic/dlext_namespaces.h;l=120-134
typedef struct android_namespace_t* (*android_create_namespace_t)(
        const char* name,
        const char* ld_library_path,
        const char* default_library_path,
        uint64_t type,
        const char* permitted_when_isolated_path,
        struct android_namespace_t* parent);

typedef bool (*android_link_namespaces_t)(
        struct android_namespace_t* from,
        struct android_namespace_t* to,
        const char* shared_libs_sonames);

typedef bool (*android_link_namespaces_all_libs_t)(
        struct android_namespace_t* from,
        struct android_namespace_t* to);

typedef struct android_namespace_t* (*android_get_exported_namespace_t)(
        const char* name);

// https://cs.android.com/android/platform/superproject/+/329d792f6d5e33e8a6fc5a02809c795ce17774ab:bionic/linker/dlfcn.cpp;drc=fda4c10ddf33a1c4cb56c58fae98dd9c2239fdc9;l=82-85
typedef int (*ld_dlclose_function)(
        void *handle);

typedef void *(*ld_dlopen_function)(
        const char* filename,
        int flags,
        const void* caller_addr);

typedef void *(*ld_dlsym_function)(
        void* handle,
        const char* symbol,
        const void* caller_addr);

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
    android_create_namespace_t create_namespace;
    android_link_namespaces_t link_namespaces;
    android_link_namespaces_all_libs_t link_namespace_all_libs;
    android_get_exported_namespace_t get_exported_namespace;
    void* handle
} linker_funcs;

typedef struct {
    ld_dlopen_function dlopen;
    ld_dlclose_function dlclose;
    ld_dlsym_function dlsym;
    void* handle
} loader_dl_funcs;

typedef struct {
    dlopen_function dlopen;
    dlclose_function dlclose;
    dlsym_function dlsym;
    void* handle
} dl_funcs;

extern linker_funcs g_linkerFuncs;
#endif //AMETHYST_NSBYPASS_H
