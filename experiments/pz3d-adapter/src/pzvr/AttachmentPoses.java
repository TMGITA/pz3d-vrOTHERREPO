package pzvr;

import java.util.IdentityHashMap;
import java.util.Map;
import org.joml.Matrix4f;

/** Owned attachment upload matrices for one render-thread stereo scope. Originals stay untouched. */
public final class AttachmentPoses {
    private static final ThreadLocal<Map<Object,Matrix4f>> CURRENT=new ThreadLocal<>();
    private AttachmentPoses() {}
    public static AutoCloseable open(Map<Object,Matrix4f> transforms) {
        if(CURRENT.get()!=null) throw new IllegalStateException("Nested attachment override");
        Map<Object,Matrix4f> owned=new IdentityHashMap<>();
        transforms.forEach((part,matrix)-> {
            if(part==null||matrix==null||!matrix.isFinite()) throw new IllegalArgumentException("Invalid attachment pose");
            owned.put(part,new Matrix4f(matrix));
        });
        CURRENT.set(owned);
        return ()->CURRENT.remove();
    }
    /** Called only at Renderer.Part's attachment uniform upload; matrices use vanilla row layout. */
    public static Matrix4f resolve(Matrix4f original,Object part) {
        Map<Object,Matrix4f> current=CURRENT.get();
        return current==null?original:current.getOrDefault(part,original);
    }
}
