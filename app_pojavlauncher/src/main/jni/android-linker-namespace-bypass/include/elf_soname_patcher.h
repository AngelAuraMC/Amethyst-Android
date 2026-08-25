//
// Created by tom on 8/25/26.
//

#ifndef AMETHYST_ELF_SONAME_PATCHER_H
#define AMETHYST_ELF_SONAME_PATCHER_H

#include <sys/stat.h>
#include <sys/mman.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include "platform.h"
#include <errno.h>
#include <fcntl.h>

/**
 * @brief  Overwrites the first three characters of a soname
 * @note   IMPORTANT: The supplied soname patch will overwrite the first strlen(sonamePatch) chars of the soname
 * @param  realfd File descriptor to source library
 * @param  patchfd File descriptor to location of patched library
 * @param  patchid Numeric patch ID, prefixes with 0
 * @return True on success
 */
bool patch_elf_soname(int realfd, int patchfd, uint16_t patchid);

/**
 * @brief  Overwrites the first three characters of a soname
 * @note   IMPORTANT: The supplied soname patch will overwrite the first strlen(sonamePatch) chars of the soname
 * @param  elfPath Full path to the elf to patch
 * @param  patchfd File descriptor to location of patched library
 * @param  patchid Numeric patch ID, prefixes with 0
 * @return True on success
 */
bool patch_elf_soname_path(const char *elfPath, int patchfd, uint16_t patchid);
#endif //AMETHYST_ELF_SONAME_PATCHER_H
