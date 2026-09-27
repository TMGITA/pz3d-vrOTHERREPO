import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import net.bytebuddy.jar.asm.*;
import pzvr.VersionGate;
import static net.bytebuddy.jar.asm.Opcodes.*;

/** Rewrites workspace copies only. Does not load or initialize game/mod classes. */
public final class OfflineAdapter extends pzvr.instrument.RendererTransform {
    OfflineAdapter(ClassLoader resources) { super(resources); }
    public static void main(String[] args) throws Exception {
        if(args.length!=4) throw new IllegalArgumentException("game.jar pz3d.jar ZombieBuddy.jar output-directory");
        Path game=Path.of(args[0]),pz=Path.of(args[1]),zb=Path.of(args[2]),output=Path.of(args[3]).toAbsolutePath();
        VersionGate.verify(game,pz,zb);
        try(URLClassLoader resources=new URLClassLoader(new URL[]{game.toUri().toURL(),pz.toUri().toURL(),zb.toUri().toURL()},OfflineAdapter.class.getClassLoader()); JarFile jar=new JarFile(pz.toFile())) {
            OfflineAdapter tool=new OfflineAdapter(resources);
            Map<String,byte[]> transformed=new LinkedHashMap<>();
            for(String name:List.of(FRAME,P+"StreamFade",P+"TreeRenderer",P+"Renderer")) {
                byte[] source=jar.getInputStream(jar.getJarEntry(name+".class")).readAllBytes();
                byte[] result=tool.transform(name,source);
                var verifier=ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofResourceParsing(resources)));
                var errors=verifier.verify(result);
                if(!errors.isEmpty()) throw new IllegalStateException("Classfile verification failed: "+errors);
                transformed.put(name,result);
            }
            tool.require("pair-boundary",1); tool.require("projection",1); tool.require("eye-copy",1); tool.require("fade-clock",1);
            tool.require("scene-adoption",1); tool.require("prediction",1); tool.require("per-eye-visibility",1);
            tool.require("attachment-upload",1);
            if(tool.evidence.getOrDefault("tree-environment-read",0)==0) throw new IllegalStateException("No tree environment reads found");
            // Write only after all transforms and classfile checks succeed.
            Files.createDirectories(output);
            for(var item:transformed.entrySet()) {
                Path target=output.resolve(item.getKey()+".class"); Files.createDirectories(target.getParent()); Files.write(target,item.getValue());
            }
            Files.writeString(output.resolve("transform-report.txt"),"OFFLINE_CLASSFILE_VERIFIED\nNo game code executed. No runtime installer included.\n"+tool.evidence+"\n");
            System.out.println("Offline transformation and JDK classfile verification passed: "+tool.evidence);
        }
    }
}
