package pzvr.xr;

/** Immutable per-frame grip poses in the same LOCAL space/time as the eyes. Null means unavailable. */
public record HandPoses(XrCamera.Pose left,XrCamera.Pose right) {
    public static final HandPoses NONE=new HandPoses(null,null);
    public XrCamera.Pose get(int side) { return side==0?left:right; }
}
