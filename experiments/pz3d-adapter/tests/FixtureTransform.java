import java.nio.file.*;
import java.lang.classfile.ClassFile;
public final class FixtureTransform {
    public static void main(String[] args) throws Exception {
        OfflineAdapter adapter=new OfflineAdapter(FixtureTransform.class.getClassLoader());
        for(String name:new String[]{OfflineAdapter.FRAME,OfflineAdapter.P+"StreamFade",OfflineAdapter.P+"TreeRenderer",OfflineAdapter.P+"Renderer"}) {
            byte[] result=adapter.transform(name,Files.readAllBytes(Path.of(args[0],name+".class")));
            var errors=ClassFile.of().verify(result);
            if(!errors.isEmpty()) throw new AssertionError(errors);
            Path target=Path.of(args[1],name+".class"); Files.createDirectories(target.getParent()); Files.write(target,result);
        }
        adapter.require("pair-boundary",1); adapter.require("projection",1); adapter.require("eye-copy",1);
        adapter.require("scene-adoption",1); adapter.require("prediction",1); adapter.require("per-eye-visibility",1);
        adapter.require("attachment-upload",1);
        System.out.println("Fixtures transformed with the same adapter.");
    }
}
