package org.angelauramc.methodsInjectorAgent.lwjgl2_methods_injector;

import static org.objectweb.asm.Opcodes.*;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * Adds a variant of alcGetCurrentContext() that returns type ALCcontext as expected by LWJGL2
 * Should not run when ASM is missing.
 */
// The lwjgl classes jar isn't bytecode patched because that increases complexity in the LWJGL repo
// and makes it harder to debug if something breaks. Also this is more portable.
public class ALC10Injector extends ClassVisitor implements ClassFileTransformer {
    protected ALC10Injector(int api) {
        super(api);
    }

    public static void premain(String args, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader l, String name, Class c,
                                    ProtectionDomain d, byte[] b) {
                if (!"org/lwjgl/openal/ALC10".equals(name)) {
                    return null;
                }
                try { // Minecraft makes it ugly if we use println
                    System.out.write("Amethyst-Android: Adding missing LWJGL2 methods for better sound mod compatibility...\n".getBytes());
                    System.out.flush();
                } catch (Exception ignored) {}
                ClassReader cr = new ClassReader(b);
                ClassWriter cw = new ClassWriter(cr, 0);
                ClassVisitor cv = new AddMethodAdapter(cw);
                cr.accept(cv, 0);
                return cw.toByteArray();
            }
        });

    }

    public static class AddMethodAdapter extends ClassVisitor {
        public AddMethodAdapter(ClassVisitor cv) {
            super(ASM4, cv);
        }
        public void visitEnd() {
            // Create the method: public static ALCcontext alcGetCurrentContext()
            MethodVisitor mv = cv.visitMethod(
                    ACC_PUBLIC | ACC_STATIC,                        // method modifiers
                    "alcGetCurrentContext",                         // method name
                    "()Lorg/lwjgl/openal/ALCcontext;",              // descriptor (return type)
                    null,                                           // signature
                    null                                            // exceptions
            );

            mv.visitCode();

            // GETSTATIC org/lwjgl/openal/ALC10.alcContext : Lorg/lwjgl/openal/ALCcontext;
            mv.visitFieldInsn(
                    GETSTATIC,
                    "org/lwjgl/openal/ALC10",                       // owner (this class)
                    "alcContext",                                   // field name
                    "Lorg/lwjgl/openal/ALCcontext;"                 // field type descriptor
            );

            // Return it
            mv.visitInsn(ARETURN);

            // Stack size = 1 (field value), locals = 0 (static method)
            mv.visitMaxs(1, 0);
            mv.visitEnd();

            super.visitEnd();
        }
    }
}

