#include <string.h>
#include <dlfcn.h>
#include <stdlib.h>
#include "android_linker_namespace_bypass/nsbypass_t.h"
#include "android_linker_namespace_bypass/platform.h"

static void* turnipHandle;

static private_namespace_funcs *privateNamespaceFuncs;
static private_dl_funcs *privateDlFuncs;
static void* (*linker_ns_dlopen)(const char* name, int flag, struct android_namespace_t* ns);
static void* (*linker_ns_dlopen_unique)(const char* tmpDir, const char* libDir, const char* libName, int flag, struct android_namespace_t* ns);

static struct android_namespace_t* turnipNs;

static uint64_t (*atrace_get_enabled_tags_p)();

__attribute__((constructor)) void init_handles() {
    // dlopen manually so the linker dependency is more explicit.
    // don't want it to do anything funny.
    void* libandroidnsbypassHandle = dlopen("libandroid_linker_namespace_bypass.so", RTLD_LOCAL | RTLD_LAZY);
    privateNamespaceFuncs = dlsym(libandroidnsbypassHandle, "g_linkerFuncs");
    privateDlFuncs = dlsym(libandroidnsbypassHandle, "g_privateDlFuncs");
    atrace_get_enabled_tags_p = privateDlFuncs->dlsym(
            privateDlFuncs->dlopen("libcutils.so", RTLD_LOCAL | RTLD_LAZY, &dlopen),
            "atrace_get_enabled_tags",
            &dlopen
            );
    linker_ns_dlopen = dlsym(libandroidnsbypassHandle, "linker_ns_dlopen");
    linker_ns_dlopen_unique = dlsym(libandroidnsbypassHandle, "linker_ns_dlopen_unique");
}

__attribute__((visibility("default"), used)) void *android_dlopen_ext(const char *filename, int flags, const android_dlextinfo *extinfo) {
    if(!strstr(filename, "vulkan."))
        return privateDlFuncs->dlopen_ext(filename, flags, extinfo, &android_dlopen_ext);
    if (!turnipHandle){
        // We aren't checking for flags haha.
        // This namespace must be isolated to keep shenanigans at bay
        turnipNs = privateNamespaceFuncs->create_namespace(
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
                privateDlFuncs->dlopen_ext
                );
        void* turnip_driver_handle = linker_ns_dlopen("libvulkan_freedreno.so", RTLD_LOCAL | RTLD_NOW, turnipNs);
        if(turnip_driver_handle == NULL) {
            printf("AdrenoSupp: Failed to load Turnip!\n%s\n", dlerror());
            return NULL;
        }
    }
    return turnipHandle;
}

__attribute__((visibility("default"), used)) void *android_load_sphal_library(const char *filename, int flags) {
    const char *sphal_namespaces[3] = {
            "sphal", "vendor", "default"
    };

    struct android_namespace_t* androidNamespace;
    for(int i = 0; i < 3; i++) {
        androidNamespace = privateNamespaceFuncs->get_exported_namespace(sphal_namespaces[i]);
        if(androidNamespace != NULL) break;
    }
    android_dlextinfo info = {0};
    info.flags = ANDROID_DLEXT_USE_NAMESPACE;
    info.library_namespace = androidNamespace;
    return android_dlopen_ext(filename, flags, &info);
}

__attribute__((visibility("default"), used)) uint64_t atrace_get_enabled_tags() {
    return atrace_get_enabled_tags_p();
}