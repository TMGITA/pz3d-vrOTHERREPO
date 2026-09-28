package pzvr.contact;

import com.pavelvoronin.pz3d.NativeAvatar;
import pzvr.melee.*;
import zombie.*;
import zombie.ai.states.SwipeStatePlayer;
import zombie.characters.*;
import zombie.inventory.types.HandWeapon;
import zombie.iso.LosUtil;
import zombie.network.GameClient;
import zombie.network.GameServer;

/** Single-player contact-owned native attack. All world access and combat calls stay on the game thread. */
public final class ContactRuntime {
    private static Thread ownerThread;
    private static long epoch=1_000_000,sequence;
    private static ContactInput.Pose previous;
    private static boolean released,spent,faulted;
    private static float travel;
    private static Owned owned;
    private static final class Owned {
        final IsoPlayer player;final HandWeapon weapon;final IsoZombie target;final long time,id;final float tx,ty;
        boolean blocked,processed;
        Owned(IsoPlayer p,HandWeapon w,IsoZombie z,long time){player=p;weapon=w;target=z;this.time=time;id=++sequence;tx=z.getX();ty=z.getY();}
    }
    public static boolean active(){return owned!=null;}
    public static boolean owns(Object p){return owned!=null&&owned.player==p&&Thread.currentThread()==ownerThread;}
    private static void log(String s){System.out.println("[PZ3D VR Contact] "+s);}
    private static void resetMotion(){previous=null;released=false;spent=false;travel=0;ContactInput.clear();}
    private static HandWeapon bat(IsoPlayer p){
        return p.getPrimaryHandItem() instanceof HandWeapon w&&"Base.BaseballBat".equals(w.getFullType())&&!w.isRanged()&&w.getCondition()>0?w:null;
    }
    private static boolean access(IsoPlayer p){
        return !pzvr.turn.TurnRuntime.meleeBlocked()&&MeleeRuntime.installed&&!faulted&&MeleeInput.mode>=3&&!MeleeInput.uiBlocked&&!GameClient.client&&!GameServer.server
            &&p.isLocalPlayer()&&!p.isDead()&&!GameTime.isGamePaused()&&!zombie.core.Core.getInstance().isDoingTextEntry()
            &&NativeAvatar.controls(p)&&!p.isRunning()&&!p.isSprinting()&&p.getCharacterActions().isEmpty()&&!p.isBannedAttacking()
            &&p.isAuthorizedHandToHand()&&p.isAuthorizedHandToHandAction()&&p.canPerformHandToHandCombat();
    }
    public static void tick(IsoPlayer p,int nativeBusy){
        try {
            if(ownerThread==null)ownerThread=Thread.currentThread();
            if(ownerThread!=Thread.currentThread())throw new IllegalStateException("Contact simulation thread changed");
            long now=System.nanoTime();var input=MeleeInput.state;var weapon=bat(p);
            boolean valid=access(p)&&weapon!=null&&nativeBusy==0&&MeleeInput.fresh(input,now);
            if(owned!=null){
                if(!valid||owned.player!=p||weapon!=owned.weapon||!input.held())owned.blocked=true;
                resolve(p);
                if(owned!=null&&now-owned.time>500_000_000L&&!p.isAttackStarted()&&!p.isPerformingHostileAnimation())finish(p);
            }
            if(!valid){MeleeInput.permit=null;resetMotion();return;}
            var old=MeleeInput.permit;
            boolean same=old!=null&&old.player()==p&&old.weapon()==weapon;
            if(!same)resetMotion();
            MeleeInput.permit=new MeleeInput.Permit(same?old.epoch():++epoch,p,weapon,now);
            MeleeInput.pending.set(null);
            if(!input.held()){released=true;spent=false;previous=null;travel=0;ContactInput.clear();return;}
            if(!released||spent||owned!=null){ContactInput.clear();return;}
            var batch=ContactInput.drainBatch();
            if(batch.discontinuity()){resetMotion();return;}
            for(var pose:batch.poses()){
                if(pose.permit()==null||pose.weapon()==null||!pose.weapon().valid()||pose.head()==null||!pose.head().finite()){resetMotion();return;}
                if(pose.permit().epoch()!=MeleeInput.permit.epoch()||now<pose.time()||now-pose.time()>MeleeInput.FRESH){previous=null;travel=0;continue;}
                var before=previous;previous=pose;if(before==null)continue;
                double dt=(pose.time()-before.time())*1e-9;
                float distance=Sweep.movement(before.weapon(),pose.weapon());
                Sweep.V headDelta=pose.head().sub(before.head());
                float relative=Math.max(pose.weapon().a().sub(before.weapon().a()).sub(headDelta).length(),pose.weapon().b().sub(before.weapon().b()).sub(headDelta).length());
                if(dt<=0||dt>.12||distance>1.5f||distance/dt>20){resetMotion();return;}
                if(relative/dt<.4||distance/dt<.4){travel=0;continue;}
                travel+=Math.min(relative,distance);
                if(relative/dt<1.2||distance/dt<1.2||travel<.10f)continue;
                IsoZombie hit=null;float earliest=2;
                for(var object:p.getCell().getObjectList())if(object instanceof IsoZombie z&&eligible(p,z,weapon)){
                    float base=(float)Math.floor(z.getZ())*3;
                    float t=Sweep.firstContact(before.weapon(),pose.weapon(),new Sweep.V(z.getX(),z.getY(),base+.35f),new Sweep.V(z.getX(),z.getY(),base+1.45f),.22f);
                    if(t>=0&&t<earliest&&pathClear(p,before.weapon().a(),pose.weapon().a())&&pathClear(p,before.weapon().b(),pose.weapon().b())
                        &&pathClear(p,pose.weapon().a(),pose.weapon().b())){hit=z;earliest=t;}
                }
                if(hit==null)continue;
                spent=true;
                if(p.isAttackStarted()||p.isPerformingHostileAnimation()||p.getMeleeDelay()>0||!p.isWeaponReady()||p.isDoShove()||p.isDoGrapple()||p.isDoStomp()||!p.CanAttack()||p.getUseHandWeapon()!=weapon){log("contact rejected: native busy/not ready");return;}
                if(MeleeInput.mode==3){log("diagnostic contact target="+hit.getID()+"; no attack");return;}
                long contactTime=before.time()+(long)((pose.time()-before.time())*earliest);
                owned=new Owned(p,weapon,hit,contactTime);log("contact id="+owned.id+" target="+hit.getID());
                p.AttemptAttack(0);
                if(owned==null)return;
                if(!p.isAttackStarted()&&!owned.processed){log("native start declined id="+owned.id);owned=null;return;}
                resolve(p);return;
            }
        }catch(Throwable error){faulted=true;MeleeInput.permit=null;resetMotion();if(owned!=null)owned.blocked=true;log("disabled: "+error);}
    }
    private static boolean eligible(IsoPlayer p,IsoZombie z,HandWeapon w){
        return !z.isDead()&&z.isStanding()&&!z.isProne()&&!z.isFakeDead()&&!z.isReanimatedForGrappleOnly()&&z.isShootable()
            &&z.getCurrentSquare()!=null&&p.getCurrentSquare()!=null&&Math.floor(p.getZ())==Math.floor(z.getZ())
            &&p.DistToSquared(z)<=Math.pow(Math.min(2.5f,w.getMaxRange(p)+.5f),2)
            &&clear(p,(int)Math.floor(p.getX()),(int)Math.floor(p.getY()),(int)Math.floor(z.getX()),(int)Math.floor(z.getY()));
    }
    private static boolean clear(IsoPlayer p,int ax,int ay,int bx,int by){
        int z=(int)Math.floor(p.getZ());var result=LosUtil.lineClear(p.getCell(),ax,ay,z,bx,by,z,false);
        return (result==LosUtil.TestResults.Clear||result==LosUtil.TestResults.ClearThroughOpenDoor)&&!LosUtil.lineClearCollide(ax,ay,z,bx,by,z,false);
    }
    private static boolean pathClear(IsoPlayer p,Sweep.V a,Sweep.V b){
        int level=(int)Math.floor(p.getZ());return Math.floor(a.z()/3)==level&&Math.floor(b.z()/3)==level
            &&clear(p,(int)Math.floor(a.x()),(int)Math.floor(a.y()),(int)Math.floor(b.x()),(int)Math.floor(b.y()));
    }
    public static void afterVars(Object p){if(!owns(p))return;var a=owned.player.getAttackVars();a.doShove=false;a.doGrapple=false;a.aimAtFloor=false;a.closeKill=false;a.targetOnGround.set(null);a.targetStanding.set(owned.target);a.setWeapon(owned.weapon);owned.player.setDoShove(false);owned.player.setDoGrapple(false);owned.player.setAimAtFloor(false);}
    public static boolean blocked(Object p){return owns(p)&&owned.blocked;}
    public static boolean unarmed(Object p,boolean value){if(!owns(p)||!value)return false;owned.blocked=true;return true;}
    /** Own all native hit-list requests during this attack, including attack selection. */
    public static boolean hitList(Object candidate){
        if(!owns(candidate))return false;
        var p=owned.player;p.clearHitInfo();
        if(!owned.blocked&&eligible(p,owned.target,owned.weapon)){
            var z=owned.target;float dx=z.getX()-p.getX(),dy=z.getY()-p.getY(),length=(float)Math.sqrt(dx*dx+dy*dy);
            float dot=length>0?(dx*p.getForwardDirection().x+dy*p.getForwardDirection().y)/length:1;
            var hit=CombatManager.getInstance().hitInfoPool.alloc().init(z,dot,dx*dx+dy*dy,z.getX(),z.getY(),z.getZ());hit.chance=100;
            p.getHitInfoList().add(hit);
        }
        p.updateHasTargetFlag();return true;
    }
    /** Called after native state entry and on simulation updates, never from render callbacks. */
    public static void resolve(Object candidate){
        try { resolveNow(candidate); }
        catch(Throwable error){faulted=true;if(owned!=null)owned.blocked=true;log("resolution disabled: "+error);}
    }
    private static void resolveNow(Object candidate){
        if(!owns(candidate)||owned.processed)return;
        var p=owned.player;long now=System.nanoTime();
        if(owned.blocked||MeleeInput.mode!=4||!access(p)||bat(p)!=owned.weapon||!MeleeInput.fresh(MeleeInput.state,now)||!MeleeInput.state.held()
            ||now-owned.time>MeleeInput.FRESH||!eligible(p,owned.target,owned.weapon)
            ||Math.hypot(owned.target.getX()-owned.tx,owned.target.getY()-owned.ty)>.25){owned.blocked=true;return;}
        if(p.getCurrentState()!=SwipeStatePlayer.instance()||p.get(SwipeStatePlayer.ATTACKED))return;
        Owned current=owned;
        owned.processed=true; // Before entering native code, preventing re-entrant or later duplicate hits.
        afterVars(p);
        try {CombatManager.getInstance().attackCollisionCheck(p,owned.weapon,SwipeStatePlayer.instance(),AttackType.NONE);
            log("resolved id="+current.id+" contactDelayMs="+(System.nanoTime()-current.time)/1_000_000+" nativeHitCount="+p.getLastHitCount());}
        catch(Throwable error){current.blocked=true;faulted=true;log("resolution disabled: "+error);}
    }
    public static void finish(Object p){if(owns(p)){log("finished id="+owned.id+" resolved="+owned.processed+" cancelled="+owned.blocked);owned=null;resetMotion();}}
}
