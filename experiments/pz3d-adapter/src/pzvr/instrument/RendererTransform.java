package pzvr.instrument;
import java.io.*;
import java.util.*;
import net.bytebuddy.jar.asm.*;
import static net.bytebuddy.jar.asm.Opcodes.*;

/** Shared offline/runtime transformation; no automatic installation. */
public class RendererTransform {
    public static final String P="com/pavelvoronin/pz3d/", FRAME=P+"Renderer$Frame", HOOK="pzvr/PairHooks";
    protected final ClassLoader resources;
    public final Map<String,Integer> evidence=new TreeMap<>();
    private final boolean harnessEntry;
    public RendererTransform(ClassLoader resources) { this(resources,false); }
    public RendererTransform(ClassLoader resources,boolean harnessEntry) { this.resources=resources; this.harnessEntry=harnessEntry; }
    void hit(String s) { evidence.merge(s,1,Integer::sum); }
    public void require(String key,int expected) { if(evidence.getOrDefault(key,0)!=expected) throw new IllegalStateException("Unsupported boundary: "+key+" "+evidence); }
    public byte[] transform(String name,byte[] source) {
        ClassReader reader=new ClassReader(source);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a,String b) { return common(a,b); }
        };
        reader.accept(new ClassVisitor(ASM9,writer) {
            @Override public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] exceptions) {
                MethodVisitor target=super.visitMethod(access,method,desc,signature,exceptions);
                if(name.equals(FRAME) && method.equals("draw") && desc.equals("(Z)V")) return draw(target);
                if(name.equals(P+"TreeRenderer")) return new MethodVisitor(ASM9,target) {
                    @Override public void visitFieldInsn(int op,String owner,String field,String d) {
                        super.visitFieldInsn(op,owner,field,d);
                        if(op==GETSTATIC && owner.equals(P+"TreeEnvironment") && field.equals("state")) {
                            hit("tree-environment-read");
                            super.visitMethodInsn(INVOKESTATIC,HOOK,"treeEnvironment","(Ljava/lang/Object;)Ljava/lang/Object;",false);
                            super.visitTypeInsn(CHECKCAST,Type.getType(d).getInternalName());
                        }
                    }
                };
                if(name.equals(P+"StreamFade") && method.equals("aB") && desc.equals("()F")) return new MethodVisitor(ASM9,target) {
                    @Override public void visitMethodInsn(int op,String owner,String m,String d,boolean itf) {
                        if(owner.equals("java/lang/System") && m.equals("nanoTime") && d.equals("()J")) {
                            hit("fade-clock"); super.visitMethodInsn(INVOKESTATIC,HOOK,"fadeClock",d,false);
                        } else super.visitMethodInsn(op,owner,m,d,itf);
                    }
                };
                return target;
            }
        },ClassReader.SKIP_FRAMES);
        return writer.toByteArray();
    }
    MethodVisitor draw(MethodVisitor target) {
        return new MethodVisitor(ASM9,target) {
            @Override public void visitCode() {
                super.visitCode();
                if(harnessEntry) {
                    Label ordinary=new Label();
                    super.visitVarInsn(ALOAD,0); super.visitVarInsn(ILOAD,1);
                    super.visitMethodInsn(INVOKESTATIC,"pzvr/harness/CaptureHarness","intercept","(Ljava/lang/Object;Z)Z",false);
                    super.visitJumpInsn(IFEQ,ordinary); super.visitInsn(RETURN); super.visitLabel(ordinary);
                    hit("harness-entry");
                }
            }
            final Label eyeLoop=new Label(); boolean inViews;
            void hook(String name,String desc) { super.visitMethodInsn(INVOKESTATIC,HOOK,name,desc,false); }
            void discard(String desc) {
                Type[] args=Type.getArgumentTypes(desc);
                for(int i=args.length-1;i>=0;i--) super.visitInsn(args[i].getSize()==2?POP2:POP);
            }
            // Arguments are already on the operand stack at this branch; both paths consume them.
            void conditional(int op,String owner,String method,String desc,boolean itf,String action) {
                Label ordinary=new Label(),done=new Label();
                hook("active","()Z"); super.visitJumpInsn(IFEQ,ordinary);
                if(action.equals("projection")) hook("projection",desc);
                else {
                    discard(desc);
                    if(action.equals("copy")) hook("copyEye","()V");
                    else if(Type.getReturnType(desc).getSort()==Type.BOOLEAN) super.visitInsn(ICONST_0);
                    else if(Type.getReturnType(desc).getSort()==Type.OBJECT) super.visitMethodInsn(INVOKESTATIC,"java/util/List","of","()Ljava/util/List;",true);
                }
                super.visitJumpInsn(GOTO,done); super.visitLabel(ordinary);
                super.visitMethodInsn(op,owner,method,desc,itf); super.visitLabel(done);
            }
            @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf) {
                if(harnessEntry && owner.equals(P+"Main") && method.equals("fail") && desc.equals("(Ljava/lang/String;Ljava/lang/Throwable;)V")) {
                    Label ordinary=new Label(),done=new Label(); hook("active","()Z"); super.visitJumpInsn(IFEQ,ordinary);
                    hook("renderFailure",desc); super.visitJumpInsn(GOTO,done); super.visitLabel(ordinary);
                    super.visitMethodInsn(op,owner,method,desc,itf); super.visitLabel(done); hit("capture-failure-isolation"); return;
                }
                if(owner.equals(P+"SceneUpload") && method.equals("advance")) { if(inViews) throw new IllegalStateException("Scene adoption inside views"); hit("scene-adoption"); }
                if(owner.equals(FRAME) && method.equals("ad")) { if(inViews) throw new IllegalStateException("Prediction inside views"); hit("prediction"); }
                if(owner.equals(P+"WorldBuffers") && method.equals("beginView")) { if(!inViews) throw new IllegalStateException("Visibility outside views"); hit("per-eye-visibility"); }
                if(owner.equals(P+"ActorShadows") && method.equals("render")) {
                    if(inViews) throw new IllegalStateException("Duplicate common boundary");
                    super.visitMethodInsn(op,owner,method,desc,itf);
                    hit("pair-boundary"); super.visitVarInsn(ALOAD,0); hook("prepared","(Ljava/lang/Object;)V");
                    super.visitLabel(eyeLoop); super.visitVarInsn(ALOAD,0); hook("enterEye","(Ljava/lang/Object;)V"); inViews=true; return;
                }
                if(owner.equals(P+"ViewMath") && method.equals("projection") && desc.equals("(FFFFFFFF)Lorg/joml/Matrix4f;")) {
                    hit("projection"); conditional(op,owner,method,desc,itf,"projection"); return;
                }
                if(owner.equals("org/lwjgl/opengl/GL30") && method.equals("glBlitFramebuffer")) {
                    if(!inViews || !desc.equals("(IIIIIIIIII)V")) throw new IllegalStateException("Unexpected blit");
                    hit("eye-copy"); conditional(op,owner,method,desc,itf,"copy");
                    hook("nextEye","()Z"); super.visitJumpInsn(IFNE,eyeLoop); inViews=false; return;
                }
                boolean suppress=owner.equals(P+"DepthPrepass") && (method.equals("begin") || method.equals("end"))
                    || owner.equals(P+"Telemetry$Gpu")
                    || inViews && (Set.of(P+"Sky",P+"TargetOutline",P+"ObjectHighlightRenderer",P+"ContactShadows",P+"ChunkDebug",P+"DeviceCaptionLayer").contains(owner)
                        || owner.equals(P+"ShotEffects") && method.equals("snapshot")
                        || owner.equals(P+"Renderer") && method.equals("a") && (desc.equals("(FIZFZ)V") || desc.equals("(Lorg/joml/Matrix4f;Ljava/util/List;Lcom/pavelvoronin/pz3d/WorldMirror$Scene;)V")));
                if(suppress) {
                    if(op!=INVOKESTATIC) throw new IllegalStateException("Nonstatic skip unsupported");
                    hit("optional-skip:"+owner.substring(P.length())+"."+method+desc);
                    conditional(op,owner,method,desc,itf,"skip"); return;
                }
                super.visitMethodInsn(op,owner,method,desc,itf);
            }
            @Override public void visitFieldInsn(int op,String owner,String field,String desc) {
                if(inViews && op==PUTFIELD && field.equals("rendered") && desc.equals("Z")) {
                    Label write=new Label(),done=new Label(); hook("firstEye","()Z"); super.visitJumpInsn(IFEQ,write);
                    super.visitInsn(POP2); super.visitJumpInsn(GOTO,done); super.visitLabel(write);
                    super.visitFieldInsn(op,owner,field,desc); super.visitLabel(done); hit("deferred-corpse-flag");
                } else super.visitFieldInsn(op,owner,field,desc);
            }
        };
    }
    ClassReader metadata(String name) {
        try(InputStream in=resources.getResourceAsStream(name+".class")) {
            if(in==null) throw new IllegalStateException("Missing hierarchy metadata: "+name);
            return new ClassReader(in);
        } catch(IOException e) { throw new UncheckedIOException(e); }
    }
    boolean assignable(String target,String source) {
        if(target.equals(source)||target.equals("java/lang/Object")) return true;
        if(source.startsWith("[")) {
            if(target.equals("java/lang/Cloneable")||target.equals("java/io/Serializable")) return true;
            if(!target.startsWith("[")) return false;
            String a=component(target),b=component(source);
            return a!=null && b!=null && assignable(a,b);
        }
        if(target.startsWith("[")) return false;
        ClassReader c=metadata(source);
        if(c.getSuperName()!=null && assignable(target,c.getSuperName())) return true;
        for(String i:c.getInterfaces()) if(assignable(target,i)) return true;
        return false;
    }
    static String component(String array) {
        String c=array.substring(1); return c.startsWith("[")?c:c.startsWith("L")?c.substring(1,c.length()-1):null;
    }
    String common(String a,String b) {
        if(assignable(a,b)) return a; if(assignable(b,a)) return b;
        if(a.startsWith("[") && b.startsWith("[")) {
            String ac=component(a),bc=component(b);
            if(ac!=null && bc!=null) { String c=common(ac,bc); return c.startsWith("[")?"["+c:"[L"+c+";"; }
            return "java/lang/Object";
        }
        if(a.startsWith("[")||b.startsWith("[")) return "java/lang/Object";
        if((metadata(a).getAccess()&ACC_INTERFACE)!=0 || (metadata(b).getAccess()&ACC_INTERFACE)!=0) return "java/lang/Object";
        do { a=metadata(a).getSuperName(); } while(!assignable(a,b));
        return a;
    }
}
