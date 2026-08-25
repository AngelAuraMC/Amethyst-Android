//
// Created on 2019/3/20.
//

int nsbypass_dlclose(void *handle);
void *nsbypass_dlopen(const char *libpath, int flags);
void *nsbypass_dlsym(void *handle, const char *name);

typedef int (*dlclose_function)(
        void *handle);

typedef void *(*dlopen_function)(
        const char *libpath,
        int flags);

typedef void *(*dlsym_function)(
        void *handle,
        const char *name);