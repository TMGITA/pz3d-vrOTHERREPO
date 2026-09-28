package pzvr.melee;

import com.pavelvoronin.pz3d.NativeAvatar;
import com.pavelvoronin.pz3d.NativeLocomotion;
import zombie.GameTime;
import zombie.characters.IsoPlayer;
import zombie.inventory.types.HandWeapon;
import zombie.inventory.types.WeaponType;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.core.Core;

/** Game-thread native attack adapter. Never writes inventory, deals damage, or queues native input. */
public final class MeleeRuntime {
    public static volatile boolean installed;
    private static boolean faulted;
    private static long epoch;
    private static Thread gameThread;
    private static Owned owned;
    private static final class Owned {
        final IsoPlayer player; final HandWeapon weapon; final long id,time;
        boolean blocked,impact;
        Owned(IsoPlayer p,HandWeapon w,MeleeInput.Swing swing) { player=p; weapon=w; id=swing.id(); time=swing.time(); }
    }
    private MeleeRuntime() {}
    private static void log(String event,long id,String detail) { System.out.println("[PZ3D VR Melee] "+event+" id="+id+" "+detail); }
    private static boolean owns(Object player) { return owned!=null && owned.player==player && Thread.currentThread()==gameThread; }
    private static HandWeapon weapon(IsoPlayer player) {
        if(!(player.getPrimaryHandItem() instanceof HandWeapon w) || w==player.bareHands || w.isRanged() || !w.isMelee()
            || w.getCondition()<=0 || w.isUseSelf() || w.getOtherHandRequire()!=null || w.getSwingAnim()==null) return null;
        WeaponType type=WeaponType.getWeaponType(w);
        return type==WeaponType.ONE_HANDED || type==WeaponType.TWO_HANDED || type==WeaponType.HEAVY?w:null;
    }
    private static boolean access(IsoPlayer p) {
        return installed && !faulted && MeleeInput.mode!=0 && !MeleeInput.uiBlocked && !GameClient.client && !GameServer.server
            && p.isLocalPlayer() && !p.isDead() && !GameTime.isGamePaused() && !Core.getInstance().isDoingTextEntry()
            && NativeAvatar.controls(p) && !p.isRunning() && !p.isSprinting() && p.getCharacterActions().isEmpty()
            && !p.isBannedAttacking() && p.isAuthorizedHandToHandAction() && p.isAuthorizedHandToHand() && p.canPerformHandToHandCombat();
    }
    private static void revoke() { MeleeInput.permit=null; MeleeInput.pending.set(null); }
    public static boolean aimActive() {
        MeleeInput.Permit p=MeleeInput.permit; MeleeInput.State s=MeleeInput.state; long now=System.nanoTime();
        return installed && !faulted && MeleeInput.mode!=0 && !MeleeInput.uiBlocked && p!=null && now>=p.time() && now-p.time()<=MeleeInput.FRESH
            && MeleeInput.fresh(s,now) && s.held();
    }
    /** Injected before NativeAvatar.beforeAnimation, on the existing simulation update. */
    public static void gameTick(Object candidate) {
        gameTick(candidate,0);
    }
    public static void gameTick(Object candidate,int nativeBusy) {
        if(!(candidate instanceof IsoPlayer p) || !NativeAvatar.owns(p)) return;
        if(pzvr.contact.ContactRuntime.active() || (MeleeInput.mode>=3 && owned==null)) { pzvr.contact.ContactRuntime.tick(p,nativeBusy); return; }
        try {
            if(gameThread==null) gameThread=Thread.currentThread();
            if(gameThread!=Thread.currentThread()) throw new IllegalStateException("Melee simulation thread changed");
            long now=System.nanoTime();
            MeleeInput.Swing swing=MeleeInput.pending.getAndSet(null);
            MeleeInput.Permit previous=MeleeInput.permit;
            MeleeInput.State input=MeleeInput.state;
            HandWeapon w=weapon(p);
            boolean permitted=access(p) && w!=null && nativeBusy==0 && MeleeInput.fresh(input,now);
            if(owned!=null) {
                if(!permitted || owned.player!=p || owned.weapon!=w || !input.held()) owned.blocked=true;
                if(now-owned.time>500_000_000L && !owned.player.isAttackStarted() && !owned.player.isPerformingHostileAnimation()) finish(owned.player);
            }
            if(!permitted) { if(swing!=null) log("rejected",swing.id(),"controls_tracking_equipment_or_native_request"); revoke(); return; }
            boolean same=previous!=null && previous.player()==p && previous.weapon()==w;
            MeleeInput.permit=new MeleeInput.Permit(same?previous.epoch():++epoch,p,w,now);
            if(swing==null) return;
            if(!same || swing.permit().epoch()!=previous.epoch() || now<swing.time() || now-swing.time()>MeleeInput.FRESH || !input.held()) {
                log("rejected",swing.id(),"stale_or_changed_input"); return;
            }
            if(owned!=null || p.isAttackStarted() || p.isPerformingHostileAnimation() || p.getMeleeDelay()>0 || !p.isWeaponReady()
                || p.isDoShove() || p.isDoGrapple() || p.isDoStomp() || p.isShoving() || !p.CanAttack() || p.getUseHandWeapon()!=w) {
                log("rejected",swing.id(),"native_busy_or_not_ready"); return;
            }
            if(MeleeInput.mode==1) { log("diagnostic",swing.id(),"eligible_gesture_no_attack weapon="+w.getType()); return; }
            owned=new Owned(p,w,swing);
            NativeLocomotion.face(p);
            log("request",swing.id(),"weapon="+w.getType()+" bothHands="+p.isItemInBothHands(w));
            p.AttemptAttack(0); // Immediate native path; never enter PZ3D's two-second input queue.
            if(p.isAttackStarted()) log("started",swing.id(),"gestureDelayMs="+(System.nanoTime()-swing.time())/1_000_000);
            else { log("rejected",swing.id(),owned!=null&&owned.blocked?"armed_only_guard":"native_declined"); owned=null; }
        } catch(Throwable error) {
            faulted=true; if(owned!=null) owned.blocked=true; revoke();
            log("disabled",0,error.toString());
        }
    }
    /** Called after every private attack-variable calculation, including later animation recalc. */
    public static void afterVars(Object candidate) {
        if(pzvr.contact.ContactRuntime.owns(candidate)) { pzvr.contact.ContactRuntime.afterVars(candidate); return; }
        if(!owns(candidate)) return;
        var p=owned.player; var vars=p.getAttackVars();
        if(vars.doShove || vars.doGrapple || vars.aimAtFloor || vars.closeKill || vars.getWeapon(p)!=owned.weapon) owned.blocked=true;
        // Keep an owned attack armed even if native selection tries to switch to unarmed later.
        vars.doShove=false; vars.doGrapple=false; vars.aimAtFloor=false; vars.closeKill=false;
        vars.targetOnGround.set(null); vars.setWeapon(owned.weapon);
        p.setDoShove(false); p.setDoGrapple(false); p.setAimAtFloor(false);
    }
    /** Skip player DoAttack before it starts a state if selection was rejected. */
    public static boolean skipStart(Object p) { return pzvr.contact.ContactRuntime.blocked(p) || (owns(p) && owned.blocked); }
    public static boolean skipUnarmed(Object p,boolean value) {
        if(pzvr.contact.ContactRuntime.owns(p)) return pzvr.contact.ContactRuntime.unarmed(p,value);
        if(!owns(p) || !value) return false;
        owned.blocked=true; return true;
    }
    public static boolean skipGrapple(Object p) { return owns(p)||pzvr.contact.ContactRuntime.owns(p); }
    /** Injected at the animation collision callback, before native combat processing. */
    public static boolean allowImpact(Object p) {
        if(pzvr.contact.ContactRuntime.owns(p)) return false;
        if(!owns(p)) return true;
        if(owned.impact) { log("duplicate_collision_suppressed",owned.id,""); return false; }
        long now=System.nanoTime(); MeleeInput.State input=MeleeInput.state;
        boolean valid=!owned.blocked && !owned.impact && MeleeInput.mode==2 && access(owned.player)
            && weapon(owned.player)==owned.weapon && owned.player.getUseHandWeapon()==owned.weapon
            && !owned.player.isDoShove() && !owned.player.isDoGrapple() && !owned.player.isDoStomp() && !owned.player.isAimAtFloor()
            && MeleeInput.fresh(input,now) && input.held();
        owned.impact=true; if(!valid) owned.blocked=true;
        log(valid?"collision_event":"collision_suppressed",owned.id,"gestureDelayMs="+(now-owned.time)/1_000_000+" (event is not proof of damage)");
        return valid;
    }
    public static void finish(Object p) {
        if(pzvr.contact.ContactRuntime.owns(p)) { pzvr.contact.ContactRuntime.finish(p); return; }
        if(!owns(p)) return;
        log("finished",owned.id,"cancelled="+owned.blocked); owned=null;
    }
}
