package pzvr.melee;

import java.lang.instrument.*;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.security.ProtectionDomain;
import java.util.*;
import net.bytebuddy.jar.asm.*;
import static net.bytebuddy.jar.asm.Opcodes.*;

/** VersionGate must pass first. Additive hooks tolerate existing ZombieBuddy/PZ3D advice. */
public final class MeleeInstallation implements ClassFileTransformer {
    public static final List<String> TARGETS=List.of("com/pavelvoronin/pz3d/NativeAvatar","zombie/CombatManager","zombie/characters/IsoPlayer","zombie/ai/states/SwipeStatePlayer");
    private static final String HOOK="pzvr/melee/MeleeRuntime",CHR="Lzombie/characters/IsoGameCharacter;";
    private final ClassLoader loader;
    private final Set<String> accepted=new HashSet<>();
    private volatile String failure;
    private MeleeInstallation(ClassLoader loader) { this.loader=loader; }
    public static void install(Instrumentation instrumentation,ClassLoader loader) throws Exception {
        MeleeInstallation install=new MeleeInstallation(loader);
        Class<?>[] types=new Class<?>[TARGETS.size()];
        for(int i=0;i<types.length;i++) {
            types[i]=Class.forName(TARGETS.get(i).replace('/','.'),false,loader);
            if(types[i].getClassLoader()!=loader || !instrumentation.isModifiableClass(types[i])) throw new IllegalStateException("Melee class ownership");
        }
        instrumentation.addTransformer(install,true);
        try {
            instrumentation.retransformClasses(types);
            if(install.failure!=null || install.accepted.size()!=types.length) throw new IllegalStateException("Melee hooks incomplete: "+install.failure);
            MeleeRuntime.installed=true;
        } catch(Throwable error) {
            MeleeRuntime.installed=false; instrumentation.removeTransformer(install);
            try { instrumentation.retransformClasses(types); } catch(Throwable rollback) { error.addSuppressed(rollback); }
            throw new IllegalStateException("Melee disabled; renderer remains available",error);
        }
    }
    @Override public synchronized byte[] transform(ClassLoader actual,String name,Class<?> type,ProtectionDomain domain,byte[] bytes) {
        if(!TARGETS.contains(name)) return null;
        try {
            if(actual!=loader) throw new IllegalStateException("Melee target loader mismatch");
            byte[] result=transform(name,bytes,loader);
            var verifier=ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofResourceParsing(loader)));
            if(!verifier.verify(result).isEmpty()) throw new IllegalStateException("Melee class verification failed");
            accepted.add(name); return result;
        } catch(Throwable error) { failure=error.toString(); MeleeRuntime.installed=false; System.err.println("[PZ3D VR Melee] "+failure); return null; }
    }
    public static byte[] transform(String name,byte[] bytes,ClassLoader loader) {
        if(!TARGETS.contains(name)) throw new IllegalArgumentException("Unknown melee target");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS) {
            @Override protected ClassLoader getClassLoader() { return loader; }
        };
        Set<String> found=new HashSet<>();
        Set<String> queueFields=new HashSet<>();
        new ClassReader(bytes).accept(new ClassVisitor(ASM9,writer) {
            @Override public FieldVisitor visitField(int access,String field,String desc,String signature,Object value) {
                if(name.equals(TARGETS.get(0)) && Set.of("kw","kx").contains(field) && desc.equals("I") && (access&ACC_STATIC)!=0) queueFields.add(field);
                return super.visitField(access,field,desc,signature,value);
            }
            @Override public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] exceptions) {
                MethodVisitor original=super.visitMethod(access,method,desc,signature,exceptions);
                String kind=null;
                if(name.equals(TARGETS.get(0))) {
                    if(method.equals("beforeAnimation")&&desc.equals("("+CHR+")V")) kind="tick";
                    if(method.equals("attackAim")&&desc.equals("()Z")) kind="aim";
                } else if(name.equals(TARGETS.get(1))) {
                    if(method.equals("calculateHitInfoList")&&desc.equals("("+CHR+")V")) kind="contacts";
                    if(method.equals("processTreeHit")&&desc.equals("("+CHR+"Lzombie/inventory/types/HandWeapon;)Z")) kind="objects";
                    if(method.equals("calculateAttackVars")&&desc.equals("(Lzombie/characters/IsoLivingCharacter;Lzombie/network/fields/hit/AttackVars;)V")) kind="vars";
                } else if(name.equals(TARGETS.get(2))) {
                    if(method.equals("DoAttack")&&desc.equals("(FLjava/lang/String;)Z")) kind="start";
                } else {
                    if(method.equals("enter")&&desc.equals("("+CHR+")V")) kind="contactEnter";
                    if(method.equals("exit")&&desc.equals("("+CHR+")V")) kind="exit";
                    if(method.equals("OnAnimEvent_AttackCollisionCheck")&&desc.equals("("+CHR+"Lzombie/AttackType;)V")) kind="impact";
                    if(Set.of("OnAnimEvent_ShoveAnim","OnAnimEvent_StompAnim","OnAnimEvent_GrappleGrabAnim").contains(method)&&desc.equals("("+CHR+"Z)V")) kind=method;
                    if(method.equals("OnAnimEvent_GrappleGrabCollisionCheck")&&desc.equals("("+CHR+"Ljava/lang/String;)V")) kind="grapple";
                }
                if(kind==null) return original;
                if(!found.add(kind)) throw new IllegalStateException("Duplicate melee hook "+kind);
                final String hook=kind;
                return new MethodVisitor(ASM9,original) {
                    private void call(String method,String descriptor) { super.visitMethodInsn(INVOKESTATIC,HOOK,method,descriptor,false); }
                    @Override public void visitCode() {
                        super.visitCode();
                        if(hook.equals("contacts")||hook.equals("objects")) {
                            Label proceed=new Label(); super.visitVarInsn(ALOAD,1);
                            super.visitMethodInsn(INVOKESTATIC,"pzvr/contact/ContactRuntime",hook.equals("contacts")?"hitList":"owns","(Ljava/lang/Object;)Z",false);
                            super.visitJumpInsn(IFEQ,proceed);
                            if(hook.equals("objects")) {
                                super.visitVarInsn(ALOAD,0); super.visitInsn(ACONST_NULL); super.visitFieldInsn(PUTFIELD,name,"objHit","Lzombie/iso/IsoObject;");
                                super.visitVarInsn(ALOAD,0); super.visitInsn(ACONST_NULL); super.visitFieldInsn(PUTFIELD,name,"treeHit","Lzombie/iso/objects/IsoTree;");
                                super.visitInsn(ICONST_0); super.visitInsn(IRETURN);
                            } else super.visitInsn(RETURN);
                            super.visitLabel(proceed);
                        }
                        if(hook.equals("tick")) {
                            super.visitVarInsn(ALOAD,0);
                            super.visitFieldInsn(GETSTATIC,name,"kw","I"); super.visitFieldInsn(GETSTATIC,name,"kx","I"); super.visitInsn(IOR);
                            call("gameTick","(Ljava/lang/Object;I)V");
                        }
                        if(hook.equals("start") || hook.equals("impact") || hook.startsWith("OnAnimEvent_") || hook.equals("grapple")) {
                            Label proceed=new Label();
                            super.visitVarInsn(ALOAD,hook.equals("start")?0:1);
                            if(hook.startsWith("OnAnimEvent_")) { super.visitVarInsn(ILOAD,2); call("skipUnarmed","(Ljava/lang/Object;Z)Z"); }
                            else call(hook.equals("start")?"skipStart":hook.equals("impact")?"allowImpact":"skipGrapple","(Ljava/lang/Object;)Z");
                            super.visitJumpInsn(hook.equals("impact")?IFNE:IFEQ,proceed);
                            if(hook.equals("start")) { super.visitInsn(ICONST_0); super.visitInsn(IRETURN); } else super.visitInsn(RETURN);
                            super.visitLabel(proceed);
                        }
                    }
                    @Override public void visitInsn(int opcode) {
                        if(opcode==RETURN && hook.equals("contactEnter")) {
                            super.visitVarInsn(ALOAD,1); super.visitMethodInsn(INVOKESTATIC,"pzvr/contact/ContactRuntime","resolve","(Ljava/lang/Object;)V",false);
                        }
                        if(opcode==IRETURN && hook.equals("aim")) { call("aimActive","()Z"); super.visitInsn(IOR); }
                        if(opcode==RETURN && (hook.equals("vars")||hook.equals("exit"))) {
                            super.visitVarInsn(ALOAD,1); call(hook.equals("vars")?"afterVars":"finish","(Ljava/lang/Object;)V");
                        }
                        super.visitInsn(opcode);
                    }
                    @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean iface) {
                        if(owner.equals(HOOK)||owner.equals("pzvr/contact/ContactRuntime")) throw new IllegalStateException("Melee hooks already present");
                        super.visitMethodInsn(opcode,owner,method,descriptor,iface);
                    }
                };
            }
        },ClassReader.EXPAND_FRAMES);
        int expected=name.equals(TARGETS.get(0))?2:name.equals(TARGETS.get(3))?7:name.equals(TARGETS.get(1))?3:1;
        if(found.size()!=expected) throw new IllegalStateException("Melee hook shape mismatch "+name+": "+found);
        if(name.equals(TARGETS.get(0)) && queueFields.size()!=2) throw new IllegalStateException("Native attack queue shape mismatch");
        return writer.toByteArray();
    }
}
