#include "environ/environ.h"
#include "utils.h"
#include "native_hooks.h"
#include "log.h"

#include <bytehook.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdlib.h>
#include <string.h>
// WARNING: Hooking dlopen and dlsym does not work for all devices, seems to be a conflict with
// the turnip loader, perhaps Dobby would fare better.
typedef void *(*dlopen_func_t)(const char *, int);
typedef void *(*dlsym_func_t)(void *, const char *);
typedef jint (*JNI_OnLoad_t)(JavaVM *vm, void *reserved);

#define SDL_LIBS \
    "libSDL3.so", \
    "libSDL2.so"

static const char *const sdl_libs[] = {
        SDL_LIBS
};

static const char *const redirected_libs[] = {
        SDL_LIBS,
};

// Strip the full paths of specific natives so we look inside LD_LIBRARY_PATH instead
static const char *redirect_dlopen_path(const char *filename) {
    if (filename == NULL)
        return NULL;

    const char *basename = strrchr(filename, '/');
    basename = basename ? basename + 1 : filename;

    for (size_t i = 0; i < sizeof(redirected_libs) / sizeof(*redirected_libs); ++i) {
        if (strcmp(basename, redirected_libs[i]) == 0) {
            LOGI("Redirecting dlopen: %s -> %s", filename, redirected_libs[i]);
            return redirected_libs[i];
        }
    }

    return filename;
}

static void *sdl3_handle = NULL;

static JNI_OnLoad_t orig_sdl3_JNI_OnLoad;

static bool ifSdl(const char *filename) {
    if (filename == NULL)
        return false;

    const char *basename = strrchr(filename, '/');
    basename = basename ? basename + 1 : filename;

    for (size_t i = 0; i < sizeof(sdl_libs) / sizeof(sdl_libs[0]); ++i) {
        if (strcmp(basename, sdl_libs[i]) == 0){
            return true;
        }
    }
    return false;
}

// Skip if not in dalvik vm cause register_methods needs to be ran in android land
static jint custom_sdl3_JNI_OnLoad(JavaVM *vm, void *reserved){
    if (pojav_environ->dalvikJavaVMPtr == vm) {
        return orig_sdl3_JNI_OnLoad(vm, reserved);
    }
    return JNI_VERSION_1_4;
}

void *custom_dlopen(const char *filename, int flags) {
    void *result = BYTEHOOK_CALL_PREV(
            custom_dlopen,
            dlopen_func_t,
            redirect_dlopen_path(filename),
            flags);
    if (ifSdl(filename)) sdl3_handle = result;

    BYTEHOOK_POP_STACK();
    return result;
}

void *custom_dlsym(void *handle, const char *symbol) {
    void *result = BYTEHOOK_CALL_PREV(
            custom_dlsym,
            dlsym_func_t,
            handle,
            symbol);
    BYTEHOOK_POP_STACK();
    if (sdl3_handle == NULL) sdl3_handle = dlopen("libSDL3.so", RTLD_LOCAL | RTLD_NOW);

    if (sdl3_handle && handle == sdl3_handle && strcmp(symbol, "JNI_OnLoad") == 0) {
        orig_sdl3_JNI_OnLoad = (JNI_OnLoad_t) result;
        // This outputs in the minecraft logs
        LOGI("Amethyst-Android: Intercepted SDL3 JNI_OnLoad: %p", result);
        return (void *) custom_sdl3_JNI_OnLoad;
    }
    return result;
}

void create_dlopen_hooks(bytehook_hook_all_t bytehook_hook_all_p) {
    // FIXME: Hooking dlopen causes a crash with Turnip loader, so let's stop doing that entirely
    bytehook_stub_t stub_dlopen = (void *)(uintptr_t)0xD15AB1ED;
//            bytehook_hook_all_p(NULL, "dlopen", &custom_dlopen, NULL, NULL);
    bytehook_stub_t stub_dlsym =
            bytehook_hook_all_p(NULL, "dlsym", &custom_dlsym, NULL, NULL);
    LOGI("Successfully initialized dlopen hooks, stub: %p %p", stub_dlopen, stub_dlsym);
}