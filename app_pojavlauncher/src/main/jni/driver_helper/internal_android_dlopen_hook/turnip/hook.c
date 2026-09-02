#include <string.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <inttypes.h>

#include <androidnsbypass/nsbypass_t.h>
#include <androidnsbypass/nsbypass.h>

#include "log.h"

static void* turnipHandle;

static struct android_namespace_t* turnipNs;

static uint64_t (*atrace_get_enabled_tags_p)();

__attribute__((visibility("default"), used)) void *android_dlopen_ext(const char *filename, int flags, const android_dlextinfo *extinfo) {
    if(!strstr(filename, "vulkan."))
        return private_dlopen_ext(filename, flags, extinfo, &android_dlopen_ext);
    if (!turnipHandle){
        // We aren't checking for flags haha.
        // This namespace must be isolated to keep shenanigans at bay
        turnipNs = private_create_namespace(
                "turnip-driver-NS",
                NULL,
                getenv("POJAV_NATIVEDIR"),
                // Inherit list of open libraries of the caller namespace
                // After creation, only ever look in provided paths given at ns creation for linking
                ANDROID_NAMESPACE_TYPE_SHARED_ISOLATED,
                NULL,
                // libvulkan should feed itself here so we inherit all its loaded NEEDED sonames
                // which are all of turnips NEEDED too.
                extinfo->library_namespace,
                __builtin_return_address(0)
                );
        turnipHandle = linker_ns_dlopen("libvulkan_freedreno.so", RTLD_LOCAL | RTLD_NOW, turnipNs);
        if(turnipHandle == NULL) {
            printf("AdrenoSupp: Failed to load Turnip!\n%s\n", dlerror());
            return NULL;
        }
    }
    return turnipHandle;
}

__attribute__((visibility("default"), used)) void *android_load_sphal_library(const char *filename, int flags) {
    // https://cs.android.com/android/platform/superproject/+/android-latest-release:system/core/libvndksupport/linker.cpp;drc=5248c5d72ad2a14f3426b7872b8867f97818650f;l=42
    const char *sphal_namespaces[3] = {
            "sphal", "vendor", "default"
    };

    struct android_namespace_t* androidNamespace;
    for(int i = 0; i < 3; i++) {
        androidNamespace = private_get_exported_namespace(sphal_namespaces[i]);
        if(androidNamespace != NULL) break;
    }
    android_dlextinfo info = {0};
    info.flags = ANDROID_DLEXT_USE_NAMESPACE;
    info.library_namespace = androidNamespace;
    return android_dlopen_ext(filename, flags, &info);
}

// https://cs.android.com/android/platform/superproject/+/android-latest-release:system/core/libcutils/include/cutils/trace.h;drc=0c44d8d68d56c7aecb828d8d87fba7dcb114f3d9;l=32-82
__attribute__((visibility("default"), used)) uint64_t atrace_get_enabled_tags() {
    if (!atrace_get_enabled_tags_p) {
        dlerror(); // no stale pls
        void* cutilsHandle = private_dlopen("libcutils.so", RTLD_LOCAL | RTLD_LAZY, &dlopen);
        if (!cutilsHandle) {
            LOGW("Unable to load libcutils in liblinkerhook, are we in an escape namespace? Assuming atrace tags to be ATRACE_TAG_NEVER.\n dlopen error: %s", dlerror());
            return 0; // ATRACE_TAG_NEVER
        }
        atrace_get_enabled_tags_p = private_dlsym(
                cutilsHandle,
                "atrace_get_enabled_tags",
                &dlopen
        );
        if (!atrace_get_enabled_tags_p) {
            LOGW("Unable to get atrace_get_enabled_tags from libcutils, dlsym error: %s", dlerror());
            return 0; // ATRACE_TAG_NEVER
        }
    }
    uint64_t result = atrace_get_enabled_tags_p();
    return result;
}