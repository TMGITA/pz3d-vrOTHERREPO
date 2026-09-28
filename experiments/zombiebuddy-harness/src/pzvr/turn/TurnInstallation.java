package pzvr.turn;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.*;
import net.bytebuddy.jar.asm.*;
import static net.bytebuddy.jar.asm.Opcodes.*;

/** Additive hooks; native joypad values are unchanged outside owned VR gameplay. */
public final class TurnInstallation implements ClassFileTransformer {
    public static final List<String> TARGETS=List.of("com/pavelvoronin/pz3d/Main","com/pavelvoronin/pz3d/NativeAvatar","zombie/input/JoypadManager$Joypad");
    private final ClassLoader loader;private final Set<String> accepted=new HashSet<>();private Throwable failure;
    private TurnInstallation(ClassLoader loader){this.loader=loader;}
    public static void install(Instrumentation inst,ClassLoader loader)throws Exception{
        var hook=new TurnInstallation(loader);Class<?>[] types=new Class<?>[TARGETS.size()];
        for(int i=0;i<types.length;i++){types[i]=Class.forName(TARGETS.get(i).replace('/','.'),false,loader);if(types[i].getClassLoader()!=loader||!inst.isModifiableClass(types[i]))throw new IllegalStateException("Turn target ownership");}
        inst.addTransformer(hook,true);
        try{inst.retransformClasses(types);if(hook.failure!=null||hook.accepted.size()!=types.length)throw new IllegalStateException("Turn hooks incomplete",hook.failure);TurnRuntime.installed=true;}
        catch(Throwable e){TurnRuntime.installed=false;inst.removeTransformer(hook);inst.retransformClasses(types);throw new IllegalStateException("Turning disabled",e);}
    }
    @Override public byte[] transform(ClassLoader actual,String name,Class<?> type,ProtectionDomain domain,byte[] bytes){
        if(!TARGETS.contains(name))return null;
        try{if(actual!=loader)throw new IllegalStateException("Turn loader mismatch");byte[] result=transform(name,bytes,loader);accepted.add(name);return result;}
        catch(Throwable e){failure=e;TurnRuntime.installed=false;return null;}
    }
    public static byte[] transform(String name,byte[] bytes,ClassLoader loader){
        var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){@Override protected ClassLoader getClassLoader(){return loader;}};
        Set<String> found=new HashSet<>();String owner="pzvr/turn/TurnRuntime";
        new ClassReader(bytes).accept(new ClassVisitor(ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String desc,String sig,String[] ex){
                var target=super.visitMethod(access,method,desc,sig,ex);int kind=-1;
                if(name.equals(TARGETS.get(0))&&method.equals("tick")&&desc.equals("()V"))kind=10;
                if(name.equals(TARGETS.get(1))&&method.equals("attackAim")&&desc.equals("()Z"))kind=11;
                if(name.equals(TARGETS.get(2))){
                    if(Set.of("getAimingAxisXRaw","getAimingAxisYRaw").contains(method)&&desc.equals("()F"))kind=0;
                    if(method.equals("getLTValue")&&desc.equals("()F"))kind=1;
                    if(desc.equals("()Z"))kind=switch(method){case "isLBPressed"->2;case "isRBPressed"->3;case "isR3Pressed"->4;default->kind;};
                    if(Set.of("isButtonPressed","wasButtonPressed","isButtonStartPress","isButtonReleasePress").contains(method)&&desc.equals("(I)Z"))kind=5;
                }
                if(kind<0)return target;
                if(!found.add(method))throw new IllegalStateException("Duplicate turn method");final int k=kind;
                return new MethodVisitor(ASM9,target){
                    @Override public void visitCode(){super.visitCode();
                        if(k==10)super.visitMethodInsn(INVOKESTATIC,owner,"tick","()V",false);
                        else if(k<10){
                            super.visitVarInsn(ALOAD,0);super.visitLdcInsn(k);
                            if(k==5)super.visitVarInsn(ILOAD,1);else super.visitInsn(ICONST_M1);
                            super.visitMethodInsn(INVOKESTATIC,owner,"suppress","(Ljava/lang/Object;II)Z",false);
                            Label ordinary=new Label();super.visitJumpInsn(IFEQ,ordinary);
                            if(k<=1){super.visitInsn(k==1?FCONST_1:FCONST_0);if(k==1)super.visitInsn(FNEG);super.visitInsn(FRETURN);}else{super.visitInsn(ICONST_0);super.visitInsn(IRETURN);}
                            super.visitLabel(ordinary);
                        }
                    }
                    @Override public void visitInsn(int op){if(k==11&&op==IRETURN){super.visitMethodInsn(INVOKESTATIC,owner,"aim","()Z",false);super.visitInsn(IOR);}super.visitInsn(op);}
                    @Override public void visitMethodInsn(int op,String o,String m,String d,boolean itf){if(o.equals(owner))throw new IllegalStateException("Turn hooks already installed");super.visitMethodInsn(op,o,m,d,itf);}
                };
            }
        },ClassReader.EXPAND_FRAMES);
        int expected=name.equals(TARGETS.get(2))?10:1;
        if(!TARGETS.contains(name)||found.size()!=expected)throw new IllegalStateException("Turn shape: "+name+found);
        byte[] result=writer.toByteArray();
        var verifier=java.lang.classfile.ClassFile.of(java.lang.classfile.ClassFile.ClassHierarchyResolverOption.of(java.lang.classfile.ClassHierarchyResolver.ofResourceParsing(loader)));
        if(!verifier.verify(result).isEmpty())throw new IllegalStateException("Turn bytecode verification");return result;
    }
}
