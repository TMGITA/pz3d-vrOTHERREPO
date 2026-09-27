package pzvr.harness;

import java.lang.instrument.*;
import java.lang.classfile.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import static net.bytebuddy.jar.asm.Opcodes.*;
import pzvr.instrument.RendererTransform;

/** Schema-preserving, all-target activation. An incompatible later transform disables requests. */
public final class Installation implements ClassFileTransformer {
    public static final List<String> TARGETS=List.of(RendererTransform.FRAME,RendererTransform.P+"StreamFade",RendererTransform.P+"TreeRenderer");
    private final ClassLoader loader;
    private final Map<String,byte[]> expected;
    private final Set<String> accepted=new HashSet<>();
    private final AtomicBoolean ready=new AtomicBoolean();
    private volatile String failure;
    private Installation(ClassLoader loader,Map<String,byte[]> originals) {
        this.loader=loader; expected=new HashMap<>();
        for(String name:TARGETS) expected.put(name,canonical(Objects.requireNonNull(originals.get(name))));
    }
    public boolean ready() { return ready.get() && failure==null; }
    public String failure() { return failure; }
    public static Installation install(Instrumentation instrumentation,ClassLoader loader,Map<String,byte[]> originals) throws Exception {
        if(!instrumentation.isRetransformClassesSupported()) throw new IllegalStateException("Retransformation unavailable");
        Installation install=new Installation(loader,originals);
        Class<?>[] targets=new Class<?>[TARGETS.size()];
        for(int i=0;i<targets.length;i++) {
            targets[i]=Class.forName(TARGETS.get(i).replace('/','.'),false,loader);
            if(targets[i].getClassLoader()!=loader || !instrumentation.isModifiableClass(targets[i])) throw new IllegalStateException("Unsupported target loader/modifiability");
        }
        instrumentation.addTransformer(install,true);
        try {
            instrumentation.retransformClasses(targets);
            if(install.failure!=null || install.accepted.size()!=targets.length) throw new IllegalStateException("Incomplete install: "+install.failure);
            install.ready.set(true);
            return install;
        } catch(Throwable error) {
            install.ready.set(false); instrumentation.removeTransformer(install);
            try { instrumentation.retransformClasses(targets); } catch(Throwable rollback) { error.addSuppressed(rollback); }
            throw new IllegalStateException("Harness installation rejected; capture remains disabled",error);
        }
    }
    @Override public synchronized byte[] transform(ClassLoader classLoader,String name,Class<?> redefining,ProtectionDomain domain,byte[] bytes) {
        if(!expected.containsKey(name)) return null;
        try {
            if(classLoader!=loader) throw new IllegalStateException("Wrong target classloader");
            if(!Arrays.equals(expected.get(name),canonical(bytes))) throw new IllegalStateException("Loaded bytecode differs from supported original: "+name);
            RendererTransform transform=new RendererTransform(loader,true);
            byte[] result=transform.transform(name,bytes);
            if(name.equals(RendererTransform.FRAME)) {
                transform.require("pair-boundary",1); transform.require("eye-copy",1); transform.require("projection",1);
                transform.require("harness-entry",1); transform.require("capture-failure-isolation",1);
            }
            var verifier=ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofResourceParsing(loader)));
            var errors=verifier.verify(result);
            if(!errors.isEmpty()) throw new IllegalStateException("Verification failed: "+errors);
            accepted.add(name); return result;
        } catch(Throwable error) {
            failure=error.toString(); ready.set(false);
            System.err.println("[PZ3D VR Test] "+failure); return null;
        }
    }
    // JVM retransformation may reorder the constant pool. Compare a normalized encoding,
    // omitting debug/stack-map attributes while retaining executable instructions and schema.
    public static byte[] canonical(byte[] bytes) {
        ClassWriter writer=new ClassWriter(0);
        Map<String,ClassWriter> methods=new TreeMap<>();
        new ClassReader(bytes).accept(new ClassVisitor(ASM9,writer) {
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                // The JVM may reorder methods too. Normalize each independently, then sort.
                ClassWriter method=new ClassWriter(0);
                method.visit(V17,ACC_PUBLIC,"Canonical",null,"java/lang/Object",null);
                methods.put(name+descriptor,method);
                return method.visitMethod(access,name,descriptor,signature,exceptions);
            }
        },ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        try {
            var output=new java.io.ByteArrayOutputStream();
            var data=new java.io.DataOutputStream(output);
            byte[] metadata=writer.toByteArray(); data.writeInt(metadata.length); data.write(metadata);
            for(ClassWriter method:methods.values()) {
                method.visitEnd(); byte[] encoded=method.toByteArray(); data.writeInt(encoded.length); data.write(encoded);
            }
            return output.toByteArray();
        } catch(java.io.IOException impossible) { throw new java.io.UncheckedIOException(impossible); }
    }
}
