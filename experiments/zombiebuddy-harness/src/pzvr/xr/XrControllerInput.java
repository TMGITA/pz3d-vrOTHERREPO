package pzvr.xr;

import java.util.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import pzvr.input.*;
import static org.lwjgl.openxr.XR10.*;

/** Standard action acquisition only; no Xbox mapping or game calls. Owned by XrHands. */
public final class XrControllerInput implements AutoCloseable {
    private final XrInstance instance;
    private final XrSession session;
    private final long[] hands;
    private final Map<String,XrAction> actions=new LinkedHashMap<>();
    private final String[] profiles={"",""};
    private long sequence,lastProfile,lastLog;
    public XrControllerInput(XrInstance instance,XrSession session,XrActionSet set,long[] hands,MemoryStack s) {
        this.instance=instance; this.session=session; this.hands=hands.clone();
        try {
            for(String name:List.of("stick","trigger","squeeze","primary","secondary","stick_click","menu")) {
                int type=name.equals("stick")?XR_ACTION_TYPE_VECTOR2F_INPUT:List.of("trigger","squeeze").contains(name)?XR_ACTION_TYPE_FLOAT_INPUT:XR_ACTION_TYPE_BOOLEAN_INPUT;
                PointerBuffer p=s.mallocPointer(1);
                check(xrCreateAction(set,XrActionCreateInfo.calloc(s).type$Default().actionName(s.UTF8("input_"+name))
                    .localizedActionName(s.UTF8("VR controller "+name)).actionType(type).subactionPaths(s.longs(hands)),p),"create "+name);
                actions.put(name,new XrAction(p.get(0),set));
            }
        } catch(Throwable ex) { close(); throw ex; }
    }
    public int bindingCount(String profile) { return profile.equals("oculus/touch_controller")?13:0; }
    public void bindings(XrActionSuggestedBinding.Buffer b,int offset,String profile,MemoryStack s) {
        if(bindingCount(profile)==0) return;
        for(int hand=0;hand<2;hand++) for(String name:actions.keySet()) {
            if(name.equals("menu")&&hand==1) continue;
            String component=switch(name) {
                case "stick" -> "thumbstick"; case "stick_click" -> "thumbstick/click";
                case "trigger","squeeze" -> name+"/value";
                case "primary" -> hand==0?"x/click":"a/click";
                case "secondary" -> hand==0?"y/click":"b/click";
                default -> "menu/click";
            };
            var path=s.mallocLong(1);
            check(xrStringToPath(instance,s.UTF8("/user/hand/"+(hand==0?"left":"right")+"/input/"+component),path),"input path");
            b.get(offset++).action(actions.get(name)).binding(path.get(0));
        }
    }
    public void sample(boolean focused,MemoryStack s) {
        long now=System.nanoTime();
        if(!focused) { ControllerBridge.clear(); return; }
        if(now-lastProfile>=1_000_000_000L) {
            lastProfile=now;
            for(int i=0;i<2;i++) {
                var profile=XrInteractionProfileState.calloc(s).type$Default();
                check(xrGetCurrentInteractionProfile(session,hands[i],profile),"current profile");
                String value="";
                if(profile.interactionProfile()!=XR_NULL_PATH) {
                    var count=s.mallocInt(1); var bytes=s.malloc(256);
                    check(xrPathToString(instance,profile.interactionProfile(),count,bytes),"profile string");
                    value=org.lwjgl.system.MemoryUtil.memUTF8(bytes,Math.max(0,count.get(0)-1));
                }
                if(!value.equals(profiles[i])) { profiles[i]=value; OpenXrSession.log("Input profile "+(i==0?"left":"right")+"="+value); }
            }
        }
        var snapshot=new VrControllerState(++sequence,now,true,read(0,s),read(1,s));
        ControllerBridge.publish(snapshot);
        if(ControllerBridge.diagnostics() && now-lastLog>=500_000_000L) {
            lastLog=now; System.out.println("[PZ3D VR Input] XR seq="+sequence+" left="+snapshot.left()+" right="+snapshot.right());
        }
    }
    private VrControllerState.Hand read(int side,MemoryStack s) {
        var info=XrActionStateGetInfo.calloc(s).type$Default().subactionPath(hands[side]);
        var stick=XrActionStateVector2f.calloc(s).type$Default();
        check(xrGetActionStateVector2f(session,info.action(actions.get("stick")),stick),"stick state");
        var trigger=XrActionStateFloat.calloc(s).type$Default();
        check(xrGetActionStateFloat(session,info.action(actions.get("trigger")),trigger),"trigger state");
        var squeeze=XrActionStateFloat.calloc(s).type$Default();
        check(xrGetActionStateFloat(session,info.action(actions.get("squeeze")),squeeze),"squeeze state");
        boolean[] values=new boolean[4]; boolean active=stick.isActive()&&trigger.isActive()&&squeeze.isActive();
        String[] names={"primary","secondary","stick_click","menu"};
        for(int i=0;i<names.length;i++) {
            var b=XrActionStateBoolean.calloc(s).type$Default();
            check(xrGetActionStateBoolean(session,info.action(actions.get(names[i])),b),"button state");
            values[i]=b.isActive()&&b.currentState();
            if(i<3) active&=b.isActive(); // Menu may be intercepted by the runtime.
        }
        return new VrControllerState.Hand(profiles[side],active,stick.currentState().x(),stick.currentState().y(),
            trigger.currentState(),squeeze.currentState(),values[0],values[1],values[2],values[3]);
    }
    private static void check(int code,String message) { if(code!=XR_SUCCESS) throw new IllegalStateException(message+": OpenXR "+code); }
    @Override public void close() { for(var action:actions.values()) xrDestroyAction(action); actions.clear(); ControllerBridge.clear(); }
}
