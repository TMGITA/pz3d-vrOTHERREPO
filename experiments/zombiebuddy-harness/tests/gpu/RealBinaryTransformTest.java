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
        System.out.println("Actual copied binary retransformation passed: 3 classes; no initialization, rendering, or mod/game entry points.");
    }
}
