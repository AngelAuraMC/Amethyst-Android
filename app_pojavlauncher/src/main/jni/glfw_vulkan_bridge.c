#include <jni.h>
#include <assert.h>
#include <dlfcn.h>

#include <stdbool.h>

#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>
#include <pthread.h>
#include <stdlib.h>
#include "log.h"
#include "environ/environ.h"

// This means that the function is an external API and that it will be used
#define EXTERNAL_API __attribute__((used))

typedef struct VulkanFuncs {
    PFN_vkGetInstanceProcAddr vkGetInstanceProcAddr;
    PFN_vkCreateAndroidSurfaceKHR vkCreateAndroidSurfaceKHR;
} VulkanFuncs;

static VulkanFuncs g_vulkanFuncs = {0};
static void* g_vulkanLib = NULL;

static bool loadVulkanLib() {
    g_vulkanLib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!g_vulkanLib) {
        LOGE("Using Vulkan is only possible on Android 7 and above!");
        return false;
    }
    return true;
}

static VulkanFuncs* initVulkanFuncs(VkInstance instance) {
    if (!loadVulkanLib()) abort();

    if (!g_vulkanFuncs.vkGetInstanceProcAddr)
        g_vulkanFuncs.vkGetInstanceProcAddr = (PFN_vkGetInstanceProcAddr)dlsym(g_vulkanLib, "vkGetInstanceProcAddr");
    if (!g_vulkanFuncs.vkGetInstanceProcAddr) {
        LOGE("Failed to dlsym vkGetInstanceProcAddr from libvulkan.so");
        abort();
    }

    if (!g_vulkanFuncs.vkCreateAndroidSurfaceKHR && g_vulkanFuncs.vkGetInstanceProcAddr)
        g_vulkanFuncs.vkCreateAndroidSurfaceKHR = (PFN_vkCreateAndroidSurfaceKHR)g_vulkanFuncs.vkGetInstanceProcAddr(instance, "vkCreateAndroidSurfaceKHR");

    if (!g_vulkanFuncs.vkCreateAndroidSurfaceKHR) {
        LOGE("Failed to vkGetInstanceProcAddr vkCreateAndroidSurfaceKHR");
        abort();
    }

    return &g_vulkanFuncs;
}


#define GLFW_TRUE 1
#define GLFW_FALSE 0

EXTERNAL_API void pojavInitVulkanLoader(long loader){}

EXTERNAL_API int pojavVulkanSupported() {
    if (!loadVulkanLib()) return GLFW_FALSE;
    return GLFW_TRUE;
}

EXTERNAL_API const char** pojavGetRequiredInstanceExtensions(uint32_t* count) {
    assert(count != NULL);
    *count = 0;

    static const char* extensions[] = {
            "VK_KHR_surface",
            "VK_KHR_android_surface"
    };

    *count = 2; // Because there's two extensions.
    return extensions;
}

EXTERNAL_API void* pojavGetInstanceProcAddress(VkInstance instance, const char* procname){
    VulkanFuncs* funcs = initVulkanFuncs(instance);
    return funcs->vkGetInstanceProcAddr(instance, procname);
}

EXTERNAL_API bool pojavGetPhysicalDevicePresentationSupport(VkInstance instance, VkPhysicalDevice device, int queuefamily){
    return GLFW_TRUE;
}

EXTERNAL_API int pojavCreateWindowSurface(VkInstance instance, void* window, const VkAllocationCallbacks* allocator, VkSurfaceKHR* surface) {
    VulkanFuncs* funcs = initVulkanFuncs(instance);
    VkAndroidSurfaceCreateInfoKHR createInfo = {0};
    createInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    createInfo.window = pojav_environ->pojavWindow;
    createInfo.pNext = NULL;
    createInfo.flags = 0;

    VkResult result = funcs->vkCreateAndroidSurfaceKHR(instance, &createInfo, NULL, surface);
    if (result != VK_SUCCESS) {
        switch (result) {
            case VK_ERROR_NATIVE_WINDOW_IN_USE_KHR:
                LOGE("vkCreateAndroidSurfaceKHR failed! VK_ERROR_NATIVE_WINDOW_IN_USE_KHR");
                break;
            case VK_ERROR_OUT_OF_HOST_MEMORY:
                LOGE("vkCreateAndroidSurfaceKHR failed! VK_ERROR_OUT_OF_HOST_MEMORY");
                break;
            case VK_ERROR_OUT_OF_DEVICE_MEMORY:
                LOGE("vkCreateAndroidSurfaceKHR failed! VK_ERROR_OUT_OF_DEVICE_MEMORY");
                break;
            case VK_ERROR_UNKNOWN:
                LOGE("vkCreateAndroidSurfaceKHR failed! VK_ERROR_UNKNOWN");
                break;
            case VK_ERROR_VALIDATION_FAILED_EXT:
                LOGE("vkCreateAndroidSurfaceKHR failed! VK_ERROR_VALIDATION_FAILED_EXT");
                break;
        }
    }
    return result;
}
