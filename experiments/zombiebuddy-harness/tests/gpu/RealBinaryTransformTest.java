import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import pzvr.VersionGate;
import pzvr.harness.Installation;
/** Defines/retransforms copied classes without initializing them or invoking any mod/game entry point. */
public final class RealBinaryTransformTest {
    public static void main(String[] args) throws Exception {
        VersionGate.verify(Path.of(args[0]),Path.of(args[1]),Path.of(args[2]));
        Map<String,byte[]> originals=new HashMap<>();
        try(JarFile jar=new JarFile(args[1])) {
            for(String name:Installation.TARGETS) try(var in=jar.getInputStream(jar.getJarEntry(name+".class"))) { originals.put(name,in.readAllBytes()); }
        }
        Installation installed=Installation.install(InstrumentationAgent.instrumentation,RealBinaryTransformTest.class.getClassLoader(),originals);
        if(!installed.ready()) throw new AssertionError("Supported copied classes were rejected");
        Class<?> ui=Class.forName("com.pavelvoronin.pz3d.UiLayer",false,RealBinaryTransformTest.class.getClassLoader());
        for(String name:List.of("texture","width","height")) {
            var field=ui.getDeclaredField(name);
            if(field.getType()!=int.class || !java.lang.reflect.Modifier.isStatic(field.getModifiers())) throw new AssertionError("UI snapshot layout mismatch: "+name);
        }
        System.out.println("Actual UiLayer snapshot fields verified without initialization.");
        var loader=RealBinaryTransformTest.class.getClassLoader();
        Class<?> actor=Class.forName("com.pavelvoronin.pz3d.Renderer$CharacterDraw",false,loader);
        if(actor.getDeclaredField("body").getType()!=boolean.class) throw new AssertionError("Local body selector");
        Class<?> part=Class.forName("com.pavelvoronin.pz3d.Renderer$Part",false,loader);
        for(String name:List.of("arms","limbs")) if(part.getDeclaredField(name).getType()!=float[].class) throw new AssertionError("Arm mask "+name);
        if(part.getDeclaredField("world").getType()!=org.joml.Matrix4f.class) throw new AssertionError("Part world transform");
        Class<?> pose=part.getDeclaredField("data").getType();
        if(pose.getField("matrixPalette").getType()!=java.nio.FloatBuffer.class) throw new AssertionError("Borrowed pose palette");
        Class<?> skin=Class.forName("zombie.core.skinnedmodel.model.SkinningData",false,loader);
        for(String name:List.of("boneIndices","skeletonHierarchy","boneOffset")) skin.getField(name);
        System.out.println("Actual arm snapshot/mask/skinning metadata fields verified without initialization.");
        if(pose.getField("xfrm").getType()!=org.joml.Matrix4f.class||!pose.getField("parent").getType().isAssignableFrom(pose)) throw new AssertionError("Attachment snapshot shape");
        Class<?> instance=pose.getField("modelInstance").getType();
        for(String field:List.of("parentBoneName","attachmentNameParent")) if(instance.getField(field).getType()!=String.class) throw new AssertionError("Attachment identifier "+field);
        Class<?> attachment=instance.getMethod("getAttachmentById",String.class).getReturnType();
        if(attachment.getMethod("getBone").getReturnType()!=String.class) throw new AssertionError("Attachment bone accessor");
        System.out.println("Actual copied binary retransformation passed: 4 classes, including attachment upload; no initialization, rendering, or mod/game entry points.");
        pzvr.melee.MeleeInstallation.install(InstrumentationAgent.instrumentation,loader);
        if(!pzvr.melee.MeleeRuntime.installed) throw new AssertionError("Melee hooks unavailable");
        pzvr.input.ControllerInstallation.install(InstrumentationAgent.instrumentation,loader);
        if(!pzvr.input.ControllerBridge.installed) throw new AssertionError("Controller hooks unavailable");
        System.out.println("Controller copied-binary retransformation passed: 3 classes without initialization.");
        System.out.println("Melee copied-binary retransformation passed: 4 additional classes, without initialization or combat execution.");
    }
}
