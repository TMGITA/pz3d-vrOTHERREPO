package pzvr.contact;

import java.util.*;
import pzvr.melee.MeleeInput;

/** Bounded render-to-simulation queue: immutable geometry, no world queries on the render thread. */
public final class ContactInput {
    public record Pose(long time,MeleeInput.Permit permit,Sweep.Capsule weapon,Sweep.V head) {}
    private static final ArrayDeque<Pose> queue=new ArrayDeque<>();
    private static boolean overflow;
    public static synchronized void publish(Pose pose){if(queue.size()>=64){queue.clear();overflow=true;}queue.addLast(pose);}
    public record Batch(List<Pose> poses,boolean discontinuity) {}
    public static synchronized Batch drainBatch(){if(overflow){queue.clear();overflow=false;return new Batch(List.of(),true);}var result=new ArrayList<>(queue);queue.clear();return new Batch(result,false);}
    public static synchronized List<Pose> drain(){return drainBatch().poses();}
    public static synchronized void breakPath(){queue.clear();overflow=true;}
    public static synchronized void clear(){queue.clear();overflow=false;}
}
