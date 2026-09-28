import pzvr.contact.*;import pzvr.melee.*;import zombie.characters.*;import zombie.iso.*;import zombie.CombatManager;
public final class ContactRuntimeTest {
 static int checks;static IsoPlayer p;static IsoZombie z;
 static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static void tick(boolean held){MeleeInput.state=new MeleeInput.State(System.nanoTime(),held,true);MeleeRuntime.gameTick(p);}
 static void reset(int mode){if(p!=null)MeleeRuntime.finish(p);p=new IsoPlayer();p.cell=new IsoCell();z=new IsoZombie();z.x=1;z.cell=p.cell;z.id=42;p.cell.objects.add(z);MeleeInput.mode=mode;MeleeInput.permit=null;MeleeInput.uiBlocked=false;MeleeRuntime.installed=true;LosUtil.result=LosUtil.TestResults.Clear;LosUtil.blocked=false;tick(false);}
 static void swing(){
  var permit=MeleeInput.permit;long now=System.nanoTime();
  ContactInput.publish(new ContactInput.Pose(now-80_000_000L,permit,new Sweep.Capsule(new Sweep.V(.5f,-.5f,1),new Sweep.V(1.4f,-.5f,1),.04f),new Sweep.V(0,0,1.6f)));
  ContactInput.publish(new ContactInput.Pose(now,permit,new Sweep.Capsule(new Sweep.V(.5f,.5f,1),new Sweep.V(1.4f,.5f,1),.04f),new Sweep.V(0,0,1.6f)));
  tick(true);
 }
 public static void main(String[] args)throws Exception {
  var loader=ContactRuntimeTest.class.getClassLoader();
  InstrumentationAgent.instrumentation.addTransformer(new java.lang.instrument.ClassFileTransformer(){
   public byte[] transform(ClassLoader l,String name,Class<?> type,java.security.ProtectionDomain d,byte[] b){
    return name.equals("zombie/CombatManager")?MeleeInstallation.transform(name,b,loader):null;
   }
  },true);
  InstrumentationAgent.instrumentation.retransformClasses(Class.forName("zombie.CombatManager",false,loader));
  reset(3);CombatManager.getInstance().calculateHitInfoList(p);ok(CombatManager.nativeLists==1,"unowned hit list untouched");ok(CombatManager.getInstance().testObjects(p,p.weapon),"unowned scenery path untouched");int objectEffects=CombatManager.objectEffects;int hits=CombatManager.impacts;swing();ok(p.attempts==0&&CombatManager.impacts==hits,"diagnostic has no attack");
  reset(4);swing();ok(p.attempts==1&&CombatManager.impacts==hits+1,"contact starts and resolves at state readiness");
  ok(CombatManager.lastTarget==z,"contacted target selected");
  ok(CombatManager.objectEffects==objectEffects,"owned collision bypasses scenery effects");
  ok(!MeleeRuntime.allowImpact(p),"later animation event blocked");ContactRuntime.resolve(p);ok(CombatManager.impacts==hits+1,"repeat resolution blocked");
  swing();ok(p.attempts==1,"one attack per trigger hold");
  ok(MeleeRuntime.skipUnarmed(p,true)&&MeleeRuntime.skipGrapple(p),"owned unarmed callbacks blocked");
  reset(4);z.y=3;swing();ok(p.attempts==0,"miss no native attack");
  reset(4);z.prone=true;z.standing=false;swing();ok(p.attempts==0,"prone excluded");
  reset(4);z.fake=true;swing();ok(p.attempts==0,"fake dead excluded");
  reset(4);LosUtil.result=LosUtil.TestResults.ClearThroughWindow;swing();ok(p.attempts==0,"window excluded");
  reset(4);LosUtil.blocked=true;swing();ok(p.attempts==0,"solid obstruction excluded");
  reset(4);p.weapon.fullType="Base.Axe";swing();ok(p.attempts==0,"nonpilot weapon excluded");
  reset(4);p.enterImmediately=false;hits=CombatManager.impacts;swing();ok(p.attempts==1&&CombatManager.impacts==hits,"does not force unready native state");
  p.state=zombie.ai.states.SwipeStatePlayer.instance();p.set(zombie.ai.states.SwipeStatePlayer.ATTACKED,false);ContactRuntime.resolve(p);ok(CombatManager.impacts==hits+1,"state entry resolves without animation impact");
  reset(4);p.enterImmediately=false;hits=CombatManager.impacts;swing();z.x+=.5f;p.state=zombie.ai.states.SwipeStatePlayer.instance();p.set(zombie.ai.states.SwipeStatePlayer.ATTACKED,false);ContactRuntime.resolve(p);ok(CombatManager.impacts==hits,"target moved invalidates pending contact");
  reset(4);p.enterImmediately=false;hits=CombatManager.impacts;swing();MeleeInput.state=new MeleeInput.State(System.nanoTime(),false,true);p.state=zombie.ai.states.SwipeStatePlayer.instance();p.set(zombie.ai.states.SwipeStatePlayer.ATTACKED,false);ContactRuntime.resolve(p);ok(CombatManager.impacts==hits,"trigger release cancels pending resolution");
  reset(4);zombie.network.GameClient.client=true;swing();ok(p.attempts==0,"multiplayer excluded");zombie.network.GameClient.client=false;
  reset(4);p.delay=5;swing();ok(p.attempts==0,"native recovery retained");
  reset(4);MeleeInput.uiBlocked=true;swing();ok(p.attempts==0,"menus block contact");
  reset(4);p.enterImmediately=false;hits=CombatManager.impacts;swing();Thread.sleep(170);
  MeleeInput.state=new MeleeInput.State(System.nanoTime(),true,true);p.state=zombie.ai.states.SwipeStatePlayer.instance();p.set(zombie.ai.states.SwipeStatePlayer.ATTACKED,false);ContactRuntime.resolve(p);
  ok(CombatManager.impacts==hits,"expired contact cannot replay after state delay");
  reset(4);hits=CombatManager.impacts;long now=System.nanoTime();var permit=MeleeInput.permit;
  for(int i=0;i<2;i++)ContactInput.publish(new ContactInput.Pose(now-(1-i)*80_000_000L,permit,new Sweep.Capsule(new Sweep.V(.5f,0,1),new Sweep.V(1.4f,0,1),.04f),new Sweep.V(0,0,1.6f)));
  tick(true);ok(p.attempts==0,"stationary overlap never attacks");
  reset(4);now=System.nanoTime();permit=MeleeInput.permit;
  for(int i=0;i<2;i++){float y=i-.5f;ContactInput.publish(new ContactInput.Pose(now-(1-i)*80_000_000L,permit,new Sweep.Capsule(new Sweep.V(.5f,y,1),new Sweep.V(1.4f,y,1),.04f),new Sweep.V(0,y,1.6f)));}
  tick(true);ok(p.attempts==0,"whole-body translation is not a swing");
  reset(4);p.enterImmediately=false;hits=CombatManager.impacts;swing();p.weapon=new zombie.inventory.types.HandWeapon();p.state=zombie.ai.states.SwipeStatePlayer.instance();p.set(zombie.ai.states.SwipeStatePlayer.ATTACKED,false);ContactRuntime.resolve(p);
  ok(CombatManager.impacts==hits,"weapon change cancels pending contact");
  reset(4);p.enterImmediately=false;hits=CombatManager.impacts;swing();MeleeInput.state=new MeleeInput.State(System.nanoTime(),true,false);p.state=zombie.ai.states.SwipeStatePlayer.instance();p.set(zombie.ai.states.SwipeStatePlayer.ATTACKED,false);ContactRuntime.resolve(p);
  ok(CombatManager.impacts==hits,"tracking loss cancels pending contact");
  reset(4);var other=new IsoZombie();other.x=1;other.y=.45f;other.id=43;other.cell=p.cell;p.cell.objects.add(other);swing();ok(CombatManager.lastTarget==z,"first contacted target wins over later contact");
  var turnGuard=pzvr.turn.TurnRuntime.class.getDeclaredField("meleeUntil");turnGuard.setAccessible(true);
  reset(4);turnGuard.setLong(null,System.nanoTime()+1_000_000_000L);swing();ok(p.attempts==0,"artificial turning cannot start contact");
  turnGuard.setLong(null,0);swing();ok(p.attempts==0,"trigger held after turn cannot rearm contact");
  tick(false);swing();ok(p.attempts==1,"release after turn rearms contact");
  reset(4);p.enterImmediately=false;hits=CombatManager.impacts;swing();turnGuard.setLong(null,System.nanoTime()+1_000_000_000L);
  p.state=zombie.ai.states.SwipeStatePlayer.instance();p.set(zombie.ai.states.SwipeStatePlayer.ATTACKED,false);ContactRuntime.resolve(p);
  ok(CombatManager.impacts==hits,"turn cancels pending contact resolution");turnGuard.setLong(null,0);
  System.out.println("Contact native adapter fixture checks passed: "+checks);
 }
}
