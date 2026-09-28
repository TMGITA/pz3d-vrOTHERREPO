import java.util.*;
import java.util.jar.JarFile;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import net.bytebuddy.jar.asm.*;
import static net.bytebuddy.jar.asm.Opcodes.*;
import pzvr.melee.MeleeInstallation;

/** Transform-only checks using copied classes; never initializes or executes the targets. */
public final class MeleeTransformTest {
    static int checks;
    static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    static void rejected(Runnable action) { try { action.run(); } catch(IllegalStateException expected) { checks++; return; } throw new AssertionError("Incompatible hooks accepted"); }
    public static void main(String[] args) throws Exception {
        ClassLoader loader=MeleeTransformTest.class.getClassLoader();
        var verifier=ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofResourceParsing(loader)));
        try(JarFile game=new JarFile(args[0]); JarFile pz=new JarFile(args[1])) {
            for(String name:MeleeInstallation.TARGETS) {
                JarFile jar=name.startsWith("zombie/")?game:pz;
                byte[] bytes; try(var in=jar.getInputStream(jar.getJarEntry(name+".class"))) { bytes=in.readAllBytes(); }
                byte[] patched=MeleeInstallation.transform(name,bytes,loader);
                check(verifier.verify(patched).isEmpty(),"Patched class verifies: "+name);
                rejected(()->MeleeInstallation.transform(name,patched,loader));
                ClassWriter decorated=new ClassWriter(0);
                new ClassReader(bytes).accept(new ClassVisitor(ASM9,decorated) {
                    @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                        return new MethodVisitor(ASM9,super.visitMethod(a,n,d,s,e)) {
                            @Override public void visitCode() { super.visitCode(); super.visitInsn(NOP); }
                        };
                    }
                },0);
                byte[] composed=MeleeInstallation.transform(name,decorated.toByteArray(),loader);
                check(verifier.verify(composed).isEmpty(),"Additive instrumentation composes: "+name);
                ClassWriter missing=new ClassWriter(0);
                new ClassReader(bytes).accept(new ClassVisitor(ASM9,missing) {
                    @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                        if(Set.of("beforeAnimation","calculateAttackVars","DoAttack","OnAnimEvent_StompAnim").contains(n)) return null;
                        return super.visitMethod(a,n,d,s,e);
                    }
                },0);
                rejected(()->MeleeInstallation.transform(name,missing.toByteArray(),loader));
            }
        }
        System.out.println("Melee transformation contract checks passed: "+checks);
    }
}
