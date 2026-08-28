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

extern clns_funcs g_clnsFuncs;
extern private_namespace_funcs g_linkerFuncs;
extern private_dl_funcs g_privateDlFuncs;
extern struct android_namespace_t* escapeNs;

void* linker_ns_dlopen(const char* name, int flag, struct android_namespace_t* ns);
void* linker_ns_dlopen_unique(const char* tmpDir, const char* libDir, const char* libName, int flag, struct android_namespace_t* ns);

// Dirs where system libs are stored.
#define SYSTEM_LIBS_PATH "/system/:/system_ext/:/data/:/vendor/:/apex/"

#endif //AMETHYST_NSBYPASS_H
