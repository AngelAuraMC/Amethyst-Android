#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_org_angelauramc_android_1linker_1namespace_1bypass_NativeLib_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
/*
 * TODO: Make seperate hook lib
 * 1) Create hookNS
 * 2) Load linker_ns_bypass funcs in hookNS
 * 3) Get the original func pointers we are gonna hook via linker_ns_bypass funcs
 * end) we get single hook without the weird passing of pointers across namespaces
 *
 * Possible issues:
 * Original func pointers in g_default_namespace may differ from the func pointers in
 * classloader NS. Make sure they're the same before implementing this.
 *
 * They likely aren't, this probably won't work. Pointer-passing via dlsym is the way.
 *
 * For the hook, always expose a pointer passer func, aka copy libadrenotools.
 * This is the only way to consistently get the correct pointers
 *
 * This has to be possible, libmivk can hook without seperate hook impl preload.
 *
 */

