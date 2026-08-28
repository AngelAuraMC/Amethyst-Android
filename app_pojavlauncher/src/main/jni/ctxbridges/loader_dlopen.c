//
// Created by maks on 26.10.2024.
//
#include <string.h>
#include <stdio.h>
#include <dlfcn.h>
#include <linux/limits.h>
#include <stdbool.h>
#include <stdlib.h>

#include "driver_helper/nsbypass.h"

typedef void *(*dlopen_func)(const char* path, int flags);

void* loader_dlopen(char* primaryName, char* secondaryName, int flags, bool bypassNamespace) {
    const char* nativedir = getenv("POJAV_NATIVEDIR");
    if(!nativedir) {
        printf("PojavExec: native dir not set, cannot enforce nsbypass, sorry\n");
        bypassNamespace = false;
    } else linker_ns_load(nativedir);
    // Some EGL libraries (e.g. MESA) link to android_stub which is just libraries in /system/lib
    // Android is cringe and disallows loading these libraries in an classloader namespace
    // That's why we load it through nsbypass
    // This actually will break on some legacy Androids, but I don't care at this point? Mesa shouldn't work on them anyway
    dlopen_func _dlopen = bypassNamespace ? linker_ns_dlopen : dlopen;
    void* dl_handle;
    if(primaryName == NULL) goto secondary;

    dl_handle = _dlopen(primaryName, flags);
    if(dl_handle != NULL) return dl_handle;
    if(secondaryName == NULL) goto dl_error;

    secondary:
    dl_handle = _dlopen(secondaryName, flags);
    if(dl_handle == NULL) goto dl_error;
    return dl_handle;

    dl_error:
    printf("%s", dlerror());
    return NULL;
}