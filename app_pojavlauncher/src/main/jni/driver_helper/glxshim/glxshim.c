//
// Created by tom on 8/29/26.
//
#include "../../ctxbridges/egl_loader.h"

// LWJGL versions earlier than 3.3.5 use glXGetProcAddress and never fall back to eglGetProcAddress
// https://github.com/LWJGL/lwjgl3/commit/05ef6288b187862897338a1ccddc9e4f854eb1a0#diff-2397f45c6e4ee297fc4d54fdad3f2b76ec211e4483a5573ab3cc946ba8e9e1e1R190
__attribute__((used)) void *glXGetProcAddress(const char *name) {
    return getProcAddress(name);
}