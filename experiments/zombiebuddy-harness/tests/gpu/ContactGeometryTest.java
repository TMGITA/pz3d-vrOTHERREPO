import pzvr.contact.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
public final class ContactGeometryTest {
 public static class Mesh { public Vector3f minXyz=new Vector3f(-.03f,-.5f,-.04f),maxXyz=new Vector3f(.03f,.5f,.04f); }
 public static class Model { public Mesh mesh=new Mesh(); }
 public static class Script { public String getName(){return "BaseballBat";} }
 public static class Instance { public Script modelScript=new Script(); }
 public static class Data { public Model model=new Model();public Instance modelInstance=new Instance(); }
 public static class Part { public Data data=new Data();public Matrix4f world=new Matrix4f().translation(1,2,3); }
 public static class Actor { public boolean body=true;public Object character;public java.util.List<Part> parts=new java.util.ArrayList<>(); }
 public record Scene(float ox,float oy,float oz) {}
 public static class Frame { public Scene scene=new Scene(100,200,6);public java.util.List<Actor> characters=new java.util.ArrayList<>(); }
 static int checks;static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static Sweep.V v(float x,float y,float z){return new Sweep.V(x,y,z);}
 public static void main(String[] args){
  ok(Sweep.distanceSquared(v(0,0,0),v(1,0,0),v(.5f,-1,0),v(.5f,1,0))==0,"crossing segments");
  ok(Sweep.distanceSquared(v(0,0,0),v(1,0,0),v(0,1,0),v(1,1,0))==1,"parallel");
  ok(Sweep.distanceSquared(v(0,0,0),v(0,0,0),v(0,1,0),v(0,1,0))==1,"degenerate points");
  ok(Sweep.distanceSquared(v(0,0,0),v(1,0,0),v(2,0,0),v(3,0,0))==1,"endpoints");
  var a=new Sweep.Capsule(v(.5f,-.5f,1),v(1.5f,-.5f,1),.04f);var b=new Sweep.Capsule(v(.5f,.5f,1),v(1.5f,.5f,1),.04f);
  float t=Sweep.firstContact(a,b,v(1,0,.3f),v(1,0,1.5f),.22f);
  ok(t>0&&t<1,"swept contact despite endpoints missing");
  ok(Sweep.firstContact(a,b,v(3,0,.3f),v(3,0,1.5f),.22f)<0,"distant miss");
  ok(Sweep.firstContact(a,b,v(1,0,3.3f),v(1,0,4.5f),.22f)<0,"vertical miss");
  ok(Sweep.firstContact(a,new Sweep.Capsule(v(9,9,1),v(10,9,1),.04f),v(1,0,.3f),v(1,0,1.5f),.22f)<0,"teleport rejected");
  ok(Sweep.firstContact(a,new Sweep.Capsule(v(Float.NaN,0,0),v(1,1,1),.04f),v(1,0,.3f),v(1,0,1.5f),.22f)<0,"nonfinite rejected");
  var min=new Vector3f(-.03f,-.5f,-.04f);var max=new Vector3f(.03f,.5f,.04f);
  var c=ContactCapture.bounds(min,max,new Matrix4f().translation(1,2,3),new Vector3f(100,200,6));
  ok(Math.abs(c.radius()-.05f)<.00001,"cross section radius");
  ok(Math.abs(c.a().x()-101)<.0001&&Math.abs(c.a().y()-201.55f)<.0001&&c.a().z()==9,"world transform and scene origin");
  ok(Math.abs(c.b().sub(c.a()).length()-.9f)<.0001,"end inset");
  try{ContactCapture.bounds(min,max,new Matrix4f().scale(10),new Vector3f());throw new AssertionError("oversize accepted");}catch(IllegalArgumentException expected){checks++;}
  var permit=new pzvr.melee.MeleeInput.Permit(1,new Object(),new Object(),1);
  for(int i=0;i<65;i++)ContactInput.publish(new ContactInput.Pose(i,permit,a,v(0,0,1)));
  ok(ContactInput.drain().isEmpty(),"overflow discards entire path");
  ContactInput.publish(new ContactInput.Pose(66,permit,a,v(0,0,1)));ok(ContactInput.drain().size()==1,"queue recovers");
  var frame=new Frame();var actor=new Actor();actor.character=new Object();frame.characters.add(actor);
  var part=new Part();actor.parts.add(part);var overrides=new java.util.IdentityHashMap<Object,Matrix4f>();
  overrides.put(part,new Matrix4f().translation(4,0,0).transpose());
  long now=System.nanoTime();pzvr.melee.MeleeInput.mode=3;
  pzvr.melee.MeleeInput.permit=new pzvr.melee.MeleeInput.Permit(2,actor.character,new Object(),now);
  pzvr.melee.MeleeInput.state=new pzvr.melee.MeleeInput.State(now,true,true);
  var head=new pzvr.xr.XrCamera.Pose(0,0,1,0,0,0,1);
  ContactCapture.capture(frame,overrides,new Matrix4f().translation(1,2,3),head);
  var captured=ContactInput.drain();ok(captured.size()==1,"render attachment acquired");
  ok(Math.abs(captured.getFirst().weapon().a().x()-105)<.0001,"row attachment transposed into world transform");
  ok(captured.getFirst().head().equals(v(101,202,10)),"head and weapon share world origin");
  actor.character=new Object();ContactCapture.capture(frame,overrides,new Matrix4f(),head);
  ok(ContactInput.drainBatch().discontinuity(),"nonlocal actor breaks sweep history");
  actor.character=pzvr.melee.MeleeInput.permit.player();var duplicate=new Part();actor.parts.add(duplicate);overrides.put(duplicate,new Matrix4f());
  ContactCapture.capture(frame,overrides,new Matrix4f(),head);
  var rejected=ContactInput.drainBatch();ok(rejected.discontinuity()&&rejected.poses().isEmpty(),"ambiguous attachments fail closed");
  System.out.println("Contact geometry checks passed: "+checks);
 }
}
