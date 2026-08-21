#include <jni.h>
#include <string>
#include <fasthook/nsbypass_dlfcn.h>

extern "C" JNIEXPORT jstring JNICALL
Java_org_angelauramc_android_1linker_1namespace_1bypass_NativeLib_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

