import pzvr.melee.*;
import zombie.characters.IsoPlayer;
import zombie.inventory.types.*;
import com.pavelvoronin.pz3d.NativeAvatar;

/** Runs the production adapter against original fixtures, never vanilla combat code. */
public final class MeleeRuntimeTest {
    static int checks; static long sequence;
    static void check(boolean ok,String why) { checks++; if(!ok) throw new AssertionError(why); }
    static void input(boolean held) { MeleeInput.state=new MeleeInput.State(System.nanoTime(),held,true); }
    static MeleeInput.Permit ready(IsoPlayer p) { input(false); MeleeRuntime.gameTick(p); return MeleeInput.permit; }
    static void swing(IsoPlayer p) {
        MeleeInput.Permit permit=ready(p); check(permit!=null,"Eligible fixture gets permit");
        input(true); MeleeInput.pending.set(new MeleeInput.Swing(++sequence,permit,System.nanoTime())); MeleeRuntime.gameTick(p);
    }
    static void end(IsoPlayer p) { MeleeRuntime.finish(p); p.started=false; p.animation=false; }
    public static void main(String[] args) {
        MeleeRuntime.installed=true; MeleeInput.mode=2;
        IsoPlayer p=new IsoPlayer();
        swing(p); check(p.attempts==1&&p.started,"One gesture starts native attack despite false AttemptAttack return");
        check(MeleeRuntime.aimActive(),"Held trigger supplies scoped native aim");
        check(MeleeRuntime.allowImpact(p),"Native collision permitted for authorized armed attack");
        check(!MeleeRuntime.allowImpact(p),"Duplicate collision suppressed"); end(p);
        check(MeleeRuntime.allowImpact(p)&&!MeleeRuntime.skipStart(p)&&!MeleeRuntime.skipUnarmed(p,true),"Unowned native combat unchanged");
        MeleeInput.mode=1; int before=p.attempts; swing(p);
        check(p.attempts==before&&!p.started,"Diagnostics never initiates an attack"); MeleeInput.mode=2;
        p.nativeAutoShove=true; swing(p);
        check(p.attempts==before&&!p.started&&!p.shove,"Close-range auto-shove rejected before state start"); p.nativeAutoShove=false;
        p.nativeFloor=true; swing(p); check(p.attempts==before&&!p.started,"Floor attack excluded"); p.nativeFloor=false;
        p.recalcShove=true; swing(p);
        check(p.started&&!p.shove&&!MeleeRuntime.allowImpact(p),"Late native shove selection suppresses collision and unarmed flags"); end(p); p.recalcShove=false;
        swing(p); check(MeleeRuntime.skipUnarmed(p,true)&&!MeleeRuntime.allowImpact(p),"Unarmed animation transition blocked"); end(p);
        swing(p); input(false); check(!MeleeRuntime.allowImpact(p),"Releasing combat gate cancels pending collision"); end(p);
        swing(p); MeleeInput.state=new MeleeInput.State(0,false,false);
        check(!MeleeRuntime.allowImpact(p),"Tracking/session loss suppresses collision"); end(p);
        swing(p); MeleeInput.uiBlocked=true;
        check(!MeleeRuntime.allowImpact(p),"Opening settings cancels owned collision"); end(p); MeleeInput.uiBlocked=false;
        swing(p); p.weapon=new HandWeapon(); check(!MeleeRuntime.allowImpact(p),"Weapon swap cancels owned collision"); end(p);
        before=p.attempts; var permit=ready(p); input(true);
        MeleeInput.pending.set(new MeleeInput.Swing(++sequence,permit,System.nanoTime()-MeleeInput.FRESH-1)); MeleeRuntime.gameTick(p);
        check(p.attempts==before,"Expired commands never execute");
        permit=ready(p); p.weapon=new HandWeapon(); input(true); MeleeInput.pending.set(new MeleeInput.Swing(++sequence,permit,System.nanoTime())); MeleeRuntime.gameTick(p);
        check(p.attempts==before,"Mailbox equipment identity revalidated");
        for(WeaponType type:new WeaponType[]{WeaponType.KNIFE,WeaponType.SPEAR,WeaponType.CHAINSAW,WeaponType.THROWING,WeaponType.HANDGUN}) {
            p.weapon.type=type; check(ready(p)==null,"Unsupported family rejected: "+type);
        }
        p.weapon.type=WeaponType.TWO_HANDED; swing(p); check(p.started,"Two-handed inventory equipment uses native path"); end(p);
        p.weapon.ranged=true; check(ready(p)==null,"Firearms rejected"); p.weapon.ranged=false;
        p.weapon.condition=0; check(ready(p)==null,"Broken weapon rejected"); p.weapon.condition=10;
        p.weapon=p.bareHands; check(ready(p)==null,"Bare hands rejected"); p.weapon=new HandWeapon();
        p.running=true; check(ready(p)==null,"Running rejected"); p.running=false;
        p.actions.push(new Object()); check(ready(p)==null,"Timed action owns controls"); p.actions.clear();
        NativeAvatar.permitted=false; check(ready(p)==null,"PZ3D UI/traversal/vehicle ownership enforced"); NativeAvatar.permitted=true;
        zombie.GameTime.paused=true; check(ready(p)==null,"Pause rejected"); zombie.GameTime.paused=false;
        zombie.core.Core.typing=true; check(ready(p)==null,"Text entry rejected"); zombie.core.Core.typing=false;
        p.delay=1; before=p.attempts; swing(p); check(p.attempts==before,"Recovery drops swing instead of buffering it"); p.delay=0;
        p.ready=false; swing(p); check(p.attempts==before,"Unready weapon never queues attack"); p.ready=true;
        permit=ready(p); input(true); MeleeInput.pending.set(new MeleeInput.Swing(++sequence,permit,System.nanoTime()));
        MeleeRuntime.gameTick(p,2); check(p.attempts==before&&MeleeInput.permit==null,"Existing native queue owns attack input");
        MeleeInput.mode=0; check(ready(p)==null&&!MeleeRuntime.aimActive(),"Disabled prototype leaves no aim or request");
        System.out.println("Melee native-adapter fixture checks passed: "+checks);
    }
}
