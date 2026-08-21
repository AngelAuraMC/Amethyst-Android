package org.angelauramc.android_linker_namespace_bypass;

public class NativeLib {

    // Used to load the 'android_linker_namespace_bypass' library on application startup.
    static {
        System.loadLibrary("android_linker_namespace_bypass");
    }

    /**
     * A native method that is implemented by the 'android_linker_namespace_bypass' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}