package org.angelauramc.methodsInjectorAgent.mod_compatibility_injector;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.angelauramc.methodsInjectorAgent.lwjgl2_methods_injector.ALC10Injector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// Older veil versions overrides imgui.library.name with libimgui-javaarm64.dylib if on arm/arm64
// which isn't the right lib. So veil fails to load.
public class VeilImguiOverrideDisable extends ClassVisitor implements ClassFileTransformer  {
    protected VeilImguiOverrideDisable(int api) {
        super(api);
    }

    public static void premain(String args, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader l, String name, Class c,
                                    ProtectionDomain d, byte[] b) {
                if (!"foundry/veil/impl/client/imgui/VeilImGuiImpl".equals(name)) {
                    return null;
                }
                ClassReader cr = new ClassReader(b);
                ClassWriter cw = new ClassWriter(cr, 0);
                ClassVisitor cv = new DisableMethodAdapter(cw);
                cr.accept(cv, 0);
                return cw.toByteArray();
            }
        });
    }

    public static class DisableMethodAdapter extends ClassVisitor {

        public DisableMethodAdapter(ClassVisitor cv) {
            super(Opcodes.ASM4, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            if (name.equals("setImGuiPath")
                    && desc.equals("()V")) {
                try { // Minecraft makes it ugly if we use println
                    System.out.write(("Amethyst-Android: Patching VeilImGuiImpl for faulty setImGuiPath()...\n" +
                            "This issue is fixed in Veil 3.1.1 and above. See https://github.com/FoundryMC/Veil/commit/8e0e09365049a106bfa3634e2ed78a0310c5b4df\n" +
                            "The intended target of this patch is Veil 3.1.0 and below. If you see this log output without Veil 3.1.0 or lower running, please send a bug report to Amethyst-Android.\n" +
                            "https://github.com/AngelAuraMC/Amethyst-Android/issues/new?template=bug_report.yml").getBytes());

                    System.out.flush();
                } catch (Exception ignored) {}
                return getMethodVisitor(mv);
            }
            return mv;
        }
        private MethodVisitor getMethodVisitor(MethodVisitor delegate) {
            return new MethodVisitor(this.api, null) {
                @Override
                public void visitCode() {
                    delegate.visitCode();
                    delegate.visitInsn(Opcodes.RETURN); // Add our return
                    delegate.visitMaxs(0, 0);           // ClassWriter.COMPUTE_FRAMES will fix this
                    delegate.visitEnd();
                }
            };
        }
    }
}



