package org.angelauramc.methodsInjectorAgent.mod_compatibility_injector;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.objectweb.asm.*;

public class DistantHorizonsZstdVersionOverrideInjector {
    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader l, String name, Class className,
                                    ProtectionDomain d, byte[] classfileBuffer) {

                if (!"com/github/luben/zstd/util/ZstdVersion".equals(name)) {
                    return null;
                }
                ClassReader cr = new ClassReader(classfileBuffer);
                ClassWriter cw = new ClassWriter(cr, 0);
                ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                    @Override
                    public FieldVisitor visitField(int access, String name, String descriptor,
                                                   String signature, Object value) {
                        if ("VERSION".equals(name)) {
                            value = "1.5.7-6"; // Keep this in sync with app_pojavlauncher/build.gradle
                        }
                        return super.visitField(access, name, descriptor, signature, value);
                    }
                };
                cr.accept(cv, 0);
                return cw.toByteArray();
            }
        });
    }
}
