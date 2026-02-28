package org.angelauramc.methodsInjectorAgent.lwjgl2_methods_injector;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * Used to forcibly run asm 5.0.4 without any pesky detection scheme stopping us.
 * We can't run ASM 4 and lower because JRE incompatibility.
 */
public class ASM5OverrideInjector extends ClassVisitor {
    protected ASM5OverrideInjector(int api, ClassVisitor classVisitor) {
        super(api, classVisitor);
    }

    public static void premain(String args, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader l, String name, Class c, ProtectionDomain d, byte[] b) {
                if (name.endsWith("ClassVisitor") ||
                    name.endsWith("MethodVisitor") ||
                    name.endsWith("FieldVisitor") ||
                    name.endsWith("AnnotationVisitor") ||
                    name.endsWith("SignatureVisitor")) {
                    try { // Minecraft makes it ugly if we use println
                        System.out.print("Amethyst-Android: Modifying ASM classes for ASM4 comaptibility...\n");
                    } catch (Exception ignored) {}
                    ClassReader cr = new ClassReader(b);
                    ClassWriter cw = new ClassWriter(cr, 0);
                    ClassVisitor cv = new disableApiVersionDetection(cw);
                    cr.accept(cv, 0);
                    return cw.toByteArray();
                } else return null;
            }
        });
    }

    /**
     * Removes the code causing an IllegalArgumentException if the api version passed is invalid.
     * We don't need it, we aren't using ASM properly anyway.
     */
    public static class disableApiVersionDetection extends ClassVisitor {
        public disableApiVersionDetection(ClassVisitor cv) {
            super(Opcodes.ASM4, cv);
        }
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            if ("<init>".equals(name)) {
                return getMethodVisitor(mv);
            }
            return mv;
        }
        private MethodVisitor getMethodVisitor(MethodVisitor mv) {
            return new MethodVisitor(this.api, mv) {
                @Override
                public void visitTypeInsn(int opcode, String type) {
                    if (opcode == Opcodes.NEW && "java/lang/IllegalArgumentException".equals(type)) {
                        super.visitInsn(Opcodes.NOP);
                    } else {
                        super.visitTypeInsn(opcode, type);
                    }
                }
                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                    if (opcode == Opcodes.INVOKESPECIAL && "java/lang/IllegalArgumentException".equals(owner) && "<init>".equals(name)) {
                        super.visitInsn(Opcodes.NOP);
                    } else {
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                    }
                }
                @Override
                public void visitInsn(int opcode) {
                    if (opcode == Opcodes.ATHROW || opcode == Opcodes.DUP) {
                        super.visitInsn(Opcodes.NOP);
                    } else {
                        super.visitInsn(opcode);
                    }
                }
            };
        }
    }
}

