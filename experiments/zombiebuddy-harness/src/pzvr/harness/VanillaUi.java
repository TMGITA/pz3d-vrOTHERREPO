package pzvr.harness;

import java.lang.reflect.Field;
import pzvr.xr.OpenXrSession;

/** Borrows the version-gated PZ3D completed UI snapshot on its existing render thread. */
public final class VanillaUi {
    private static Field texture,width,height;
    private static boolean missing;
    private VanillaUi() {}
    public static void copy(OpenXrSession session) throws ReflectiveOperationException {
        if(texture==null) {
            Class<?> layer=Class.forName("com.pavelvoronin.pz3d.UiLayer",false,VanillaUi.class.getClassLoader());
            texture=field(layer,"texture"); width=field(layer,"width"); height=field(layer,"height");
        }
        int id=texture.getInt(null),w=width.getInt(null),h=height.getInt(null);
        if(id==0 || w<1 || h<1) {
            if(!missing) OpenXrSession.log("Vanilla UI texture not ready; world-only frame");
            missing=true; return;
        }
        missing=false;
        session.copyUiTexture(id,w,h);
    }
    private static Field field(Class<?> type,String name) throws NoSuchFieldException {
        Field field=type.getDeclaredField(name); field.setAccessible(true); return field;
    }
}
