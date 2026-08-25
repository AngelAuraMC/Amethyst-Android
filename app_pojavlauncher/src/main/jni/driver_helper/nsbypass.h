
//
// Created by maks on 05.06.2023.
//

#ifndef POJAVLAUNCHER_NSBYPASS_H
#define POJAVLAUNCHER_NSBYPASS_H

#include <stdbool.h>
struct android_namespace_t;
bool linker_ns_load(const char* lib_search_path, struct android_namespace_t** ns);

#endif //POJAVLAUNCHER_NSBYPASS_H
