//
// Created by maks on 05.06.2023.
//

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
#include "android_linker_namespace_bypass/nsbypass.h"

/* upper 6 bits of an ARM64 instruction are the instruction name */
#define OP_MS 0b11111100000000000000000000000000
/* Branch Label instruction opcode and immediate mask */
#define BL_OP 0b10010100000000000000000000000000
#define BL_IM 0b00000011111111111111111111111111
/* Library search path */
#define SEARCH_PATH "/system/lib64"
#define ELF_EHDR Elf64_Ehdr
#define ELF_SHDR Elf64_Shdr
#define ELF_HALF Elf64_Half
#define ELF_XWORD Elf64_Xword
#define ELF_DYN Elf64_Dyn
#define ELF_SYM Elf64_Sym

//#define ADRENO_POSSIBLE
bool linker_ns_load(const char* lib_search_path, struct android_namespace_t** ns) {
    // assemble the full path search path
    char full_path[strlen(SEARCH_PATH) + strlen(lib_search_path) + 2 + 1];
    sprintf(full_path, "%s:%s", SEARCH_PATH, lib_search_path);
    *ns = g_linkerFuncs.create_namespace("pojav-driver",
                                                      full_path,
                                                      full_path,
                                                      3 /* TYPE_SHAFED | TYPE_ISOLATED */,
                                                      "/system/:/data/:/vendor/:/apex/", NULL, __builtin_return_address(0));
    // THIS IS VERY IMPORTANT and how I trolled FoldCraft:
    // You need to link the new driver_namespace with NULL and and add ld-android.so
    // in the link list, to pass through the driver_namespace correctly.
    // Not doing this fucks up internal __loader symbol lookup
    // inside the new driver_namespace, thus breaking it on
    // a lot of android versions
    // FoldCraft got trolled because they copied the
    // old broken code verbatim and didn't even test it thoroughly
    g_linkerFuncs.link_namespaces(*ns, NULL, "ld-android.so");
    // Also establish links to use the libnativeloader(_lazy).so libraries
    // from the global namespace. This is a workaround for an EMUI issue where
    // the newly loaded libnativeloader_lazy for some unknown reason links
    // to itself and causes a deadlock when loading the vulkan driver.
    g_linkerFuncs.link_namespaces(*ns, NULL, "libnativeloader.so");
    g_linkerFuncs.link_namespaces(*ns, NULL, "libnativeloader_lazy.so");
    return true;
}