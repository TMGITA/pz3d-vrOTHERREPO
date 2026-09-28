package zombie.characters;
import zombie.inventory.*; import zombie.inventory.types.*; import zombie.network.fields.hit.AttackVars;
public class IsoPlayer extends IsoLivingCharacter {
 public final HandWeapon bareHands=new HandWeapon(); public HandWeapon weapon=new HandWeapon();
 public boolean started,animation,dead,running,sprinting,banned,ready=true,shove,grapple,stomp,floor,nativeAutoShove,nativeFloor,recalcShove,allow=true;
 public boolean enterImmediately=true;public int attempts; public float delay; public final AttackVars vars=new AttackVars(); public final java.util.Stack<Object> actions=new java.util.Stack<>();
 public InventoryItem getPrimaryHandItem(){return weapon;} public HandWeapon getUseHandWeapon(){return weapon;} public boolean isLocalPlayer(){return true;}
 public boolean isDead(){return dead;} public boolean isRunning(){return running;} public boolean isSprinting(){return sprinting;}
 public java.util.Stack<Object> getCharacterActions(){return actions;} public boolean isBannedAttacking(){return banned;}
 public boolean isAuthorizedHandToHandAction(){return allow;} public boolean isAuthorizedHandToHand(){return allow;} public boolean canPerformHandToHandCombat(){return allow;}
 public boolean isAttackStarted(){return started;} public boolean isPerformingHostileAnimation(){return animation;} public float getMeleeDelay(){return delay;}
 public boolean isWeaponReady(){return ready;} public boolean isDoShove(){return shove;} public boolean isDoGrapple(){return grapple;} public boolean isDoStomp(){return stomp;}
 public boolean isShoving(){return shove;} public boolean CanAttack(){return allow;} public boolean isItemInBothHands(InventoryItem w){return weapon.type==WeaponType.TWO_HANDED;}
 public AttackVars getAttackVars(){return vars;} public void setDoShove(boolean v){shove=v;} public void setDoGrapple(boolean v){grapple=v;} public void setAimAtFloor(boolean v){floor=v;} public boolean isAimAtFloor(){return floor;}
 public boolean AttemptAttack(float charge){
  vars.setWeapon(weapon); vars.doShove=nativeAutoShove; vars.aimAtFloor=nativeFloor;
  pzvr.melee.MeleeRuntime.afterVars(this);
  if(pzvr.melee.MeleeRuntime.skipStart(this))return false;
  attempts++; started=true;
  if(recalcShove){vars.doShove=true;pzvr.melee.MeleeRuntime.afterVars(this);}
  if(pzvr.contact.ContactRuntime.owns(this)&&enterImmediately){state=zombie.ai.states.SwipeStatePlayer.instance();set(zombie.ai.states.SwipeStatePlayer.ATTACKED,false);pzvr.contact.ContactRuntime.resolve(this);}
  return false; // Match native return behavior even after starting.
 }
}
