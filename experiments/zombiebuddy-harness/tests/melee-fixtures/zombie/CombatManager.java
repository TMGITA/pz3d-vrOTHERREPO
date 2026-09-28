package zombie;
import zombie.characters.*;
public class CombatManager {
 public static final CombatManager instance=new CombatManager();public static int impacts,objectEffects,nativeLists;public static IsoZombie lastTarget;
 private zombie.iso.IsoObject objHit;private zombie.iso.objects.IsoTree treeHit;
 public final zombie.popman.ObjectPool<zombie.network.fields.hit.HitInfo> hitInfoPool=new zombie.popman.ObjectPool<>(zombie.network.fields.hit.HitInfo::new);
 public static CombatManager getInstance(){return instance;}
 private void calculateAttackVars(IsoLivingCharacter p,zombie.network.fields.hit.AttackVars vars){}
 public void calculateHitInfoList(IsoGameCharacter p){nativeLists++;}
 private boolean processTreeHit(java.util.List<?> hits,IsoGameCharacter p,zombie.inventory.types.HandWeapon w){objectEffects++;return true;}
 public boolean testObjects(IsoGameCharacter p,zombie.inventory.types.HandWeapon w){return processTreeHit(java.util.List.of(),p,w);}
 public void attackCollisionCheck(IsoGameCharacter p,zombie.inventory.types.HandWeapon w,zombie.ai.states.SwipeStatePlayer state,AttackType type){
  calculateHitInfoList(p);if(p.getHitInfoList().isEmpty())throw new AssertionError("Missing contact hit list");
  impacts++;lastTarget=(IsoZombie)p.getHitInfoList().get(0).getObject();
  objHit=new zombie.iso.IsoObject();treeHit=new zombie.iso.objects.IsoTree();
  if(processTreeHit(java.util.List.of(),p,w)||objHit!=null||treeHit!=null)throw new AssertionError("Contact scenery effects were not suppressed");
  p.set(zombie.ai.states.SwipeStatePlayer.ATTACKED,true);
 }
}
