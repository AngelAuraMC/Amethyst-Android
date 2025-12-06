package org.angelauramc.lwjgl2_methods_injector;

import java.lang.instrument.Instrumentation;

public class startInjectors {
    public static void premain(String args, Instrumentation inst) {
        try {
            // Check if we have the asm classes we need
            Class.forName("org.objectweb.asm.ClassReader");
            Class.forName("org.objectweb.asm.ClassVisitor");
            Class.forName("org.objectweb.asm.ClassWriter");
            Class.forName("org.objectweb.asm.MethodVisitor");
            Class.forName("org.objectweb.asm.Opcodes");
            ALC10Injector.premain(args, inst);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
        }
    }
}
