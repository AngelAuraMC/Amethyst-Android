//
// Created by maks on 26.10.2024.
//
#include <dlfcn.h>
#include <linux/limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "androidnsbypass/nsbypass.h"
#include "androidnsbypass/nsbypass_t.h"
#include "global_state.h"

void* loader_dlopen(char* primaryName, char* secondaryName, int flags) {
    void* dl_handle;
    if(primaryName == NULL) goto secondary;

    dl_handle = dlopen(primaryName, flags);
    if(dl_handle != NULL) return dl_handle;
    // Fallback to nsbypass if it didn't work
    if (!app_escapeNs) {
        app_escapeNs = private_create_namespace(
                "app-escapeNs",
                NULL,
                getenv("POJAV_NATIVEDIR"), // append to search path!
                ANDROID_NAMESPACE_TYPE_SHARED, // Inherit from escapeNs paths
                getenv("POJAV_NATIVEDIR"), // not needed, useless for non-isolate
                get_escape_namespace(), // Inherit from escapeNs so we get the system lib paths too
                __builtin_return_address(0));
    }
    dl_handle = linker_ns_dlopen(primaryName, RTLD_LOCAL | RTLD_LAZY, app_escapeNs);
    if(dl_handle != NULL) return dl_handle;

    if(secondaryName == NULL) goto dl_error;

    secondary:
    dl_handle = dlopen(secondaryName, flags);
    if(dl_handle == NULL) goto dl_error;
    // Fallback to nsbypass if it didn't work
    if (!app_escapeNs) {
        app_escapeNs = private_create_namespace(
                "app-escapeNs",
                NULL,
                getenv("POJAV_NATIVEDIR"), // append to search path!
                ANDROID_NAMESPACE_TYPE_SHARED, // Inherit from escapeNs paths
                getenv("POJAV_NATIVEDIR"), // not needed, useless for non-isolate
                get_escape_namespace(), // Inherit from escapeNs so we get the system lib paths too
                __builtin_return_address(0));
    }
    dl_handle = linker_ns_dlopen(secondaryName, RTLD_LOCAL | RTLD_LAZY, app_escapeNs);
    return dl_handle;

    dl_error:
    printf("%s", dlerror());
    return NULL;
}