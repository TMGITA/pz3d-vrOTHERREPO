package pzvr.xr;

import java.nio.LongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.openxr.XR10.*;

/** Grip poses and an explicit right-trigger melee gate. Owner render thread. */
public final class XrHands implements AutoCloseable {
    private final XrInstance instance;
    private final XrSession session;
    private XrActionSet set;
    private XrAction grip;
    private XrAction combat;
    private XrControllerInput input;
    private boolean combatHeld;
    private long poseTime;
    public boolean combatHeld() { return combatHeld; }
    public long poseTime() { return poseTime; }
    private final XrSpace[] spaces=new XrSpace[2];
    private final long[] paths=new long[2];
    private int lastMask=-1;
    public XrHands(XrInstance instance,XrSession session) {
        this.instance=instance; this.session=session;
        try(MemoryStack s=MemoryStack.stackPush()) {
            PointerBuffer p=s.mallocPointer(1);
            check(xrCreateActionSet(instance,XrActionSetCreateInfo.calloc(s).type$Default()
                .actionSetName(s.UTF8("pzvr_arms")).localizedActionSetName(s.UTF8("PZ VR Arms")).priority(0),p),"create hand action set");
            set=new XrActionSet(p.get(0),instance);
            paths[0]=path("/user/hand/left",s); paths[1]=path("/user/hand/right",s);
            check(xrCreateAction(set,XrActionCreateInfo.calloc(s).type$Default().actionName(s.UTF8("grip_pose"))
                .localizedActionName(s.UTF8("Hand grip pose")).actionType(XR_ACTION_TYPE_POSE_INPUT)
                .subactionPaths(s.longs(paths)),p),"create grip action");
            grip=new XrAction(p.get(0),set);
            check(xrCreateAction(set,XrActionCreateInfo.calloc(s).type$Default().actionName(s.UTF8("melee_enable"))
                .localizedActionName(s.UTF8("Hold to enable melee swing")).actionType(XR_ACTION_TYPE_FLOAT_INPUT)
                .subactionPaths(s.longs(paths[1])),p),"create melee enable action");
            combat=new XrAction(p.get(0),set);
            try { input=new XrControllerInput(instance,session,set,paths,s); }
            catch(RuntimeException ex) { OpenXrSession.log("Gamepad actions unavailable: "+ex); }
            for(String profile:new String[]{"khr/simple_controller","oculus/touch_controller","valve/index_controller","htc/vive_controller","microsoft/motion_controller"}) {
                XrActionSuggestedBinding.Buffer bindings=XrActionSuggestedBinding.calloc(3+(input==null?0:input.bindingCount(profile)),s);
                for(int i=0;i<2;i++) bindings.get(i).action(grip).binding(path("/user/hand/"+(i==0?"left":"right")+"/input/grip/pose",s));
                bindings.get(2).action(combat).binding(path("/user/hand/right/input/"+(profile.equals("khr/simple_controller")?"select/click":"trigger/value"),s));
                if(input!=null) input.bindings(bindings,3,profile,s);
                int result=xrSuggestInteractionProfileBindings(instance,XrInteractionProfileSuggestedBinding.calloc(s).type$Default()
                    .interactionProfile(path("/interaction_profiles/"+profile,s)).suggestedBindings(bindings));
                if(result==XR_ERROR_PATH_UNSUPPORTED) OpenXrSession.log("Grip profile unsupported: "+profile);
                else check(result,"suggest grip bindings");
            }
            for(int i=0;i<2;i++) {
                XrActionSpaceCreateInfo info=XrActionSpaceCreateInfo.calloc(s).type$Default().action(grip).subactionPath(paths[i]);
                info.poseInActionSpace().orientation().w(1);
                check(xrCreateActionSpace(session,info,p),"create grip space"); spaces[i]=new XrSpace(p.get(0),session);
            }
            check(xrAttachSessionActionSets(session,XrSessionActionSetsAttachInfo.calloc(s).type$Default().actionSets(s.pointers(set.address()))),"attach hands");
        } catch(Throwable failure) { close(); throw failure; }
    }
    public HandPoses locate(XrSpace base,long time,boolean focused,MemoryStack s) {
        combatHeld=false; poseTime=time;
        pzvr.input.ControllerBridge.clear();
        XrCamera.Pose[] poses=new XrCamera.Pose[2];
        if(focused) {
            XrActiveActionSet.Buffer active=XrActiveActionSet.calloc(1,s); active.get(0).actionSet(set).subactionPath(XR_NULL_PATH);
            int result=xrSyncActions(session,XrActionsSyncInfo.calloc(s).type$Default().activeActionSets(active));
            if(result!=XR_SESSION_NOT_FOCUSED) {
                check(result,"sync grip poses");
                if(input!=null) try { input.sample(true,s); }
                catch(RuntimeException ex) { OpenXrSession.log("Gamepad acquisition disabled: "+ex); input.close(); input=null; }
                XrActionStateFloat trigger=XrActionStateFloat.calloc(s).type$Default();
                check(xrGetActionStateFloat(session,XrActionStateGetInfo.calloc(s).type$Default().action(combat).subactionPath(paths[1]),trigger),"melee enable state");
                combatHeld=trigger.isActive() && trigger.currentState()>=.65f;
                for(int i=0;i<2;i++) {
                    XrActionStatePose state=XrActionStatePose.calloc(s).type$Default();
                    check(xrGetActionStatePose(session,XrActionStateGetInfo.calloc(s).type$Default().action(grip).subactionPath(paths[i]),state),"grip activity");
                    if(!state.isActive()) continue;
                    XrSpaceLocation location=XrSpaceLocation.calloc(s).type$Default();
                    check(xrLocateSpace(spaces[i],base,time,location),"locate grip");
                    long required=XR_SPACE_LOCATION_POSITION_VALID_BIT|XR_SPACE_LOCATION_ORIENTATION_VALID_BIT
                        |XR_SPACE_LOCATION_POSITION_TRACKED_BIT|XR_SPACE_LOCATION_ORIENTATION_TRACKED_BIT;
                    if((location.locationFlags()&required)!=required) continue;
                    var p=location.pose().position$(); var q=location.pose().orientation();
                    poses[i]=new XrCamera.Pose(p.x(),p.y(),p.z(),q.x(),q.y(),q.z(),q.w());
                }
            }
        }
        int mask=(poses[0]==null?0:1)|(poses[1]==null?0:2);
        if(mask!=lastMask) { OpenXrSession.log("Tracked arms: left="+(poses[0]!=null)+", right="+(poses[1]!=null)); lastMask=mask; }
        return new HandPoses(poses[0],poses[1]);
    }
    private long path(String name,MemoryStack s) { LongBuffer p=s.mallocLong(1); check(xrStringToPath(instance,s.UTF8(name),p),"hand path"); return p.get(0); }
    private static void check(int result,String operation) {
        if(result!=XR_SUCCESS) throw new IllegalStateException(operation+": OpenXR result "+result);
    }
    @Override public void close() {
        for(int i=0;i<2;i++) if(spaces[i]!=null) { xrDestroySpace(spaces[i]); spaces[i]=null; }
        if(input!=null) { input.close(); input=null; }
        if(grip!=null) { xrDestroyAction(grip); grip=null; }
        if(combat!=null) { xrDestroyAction(combat); combat=null; }
        if(set!=null) { xrDestroyActionSet(set); set=null; }
    }
}
