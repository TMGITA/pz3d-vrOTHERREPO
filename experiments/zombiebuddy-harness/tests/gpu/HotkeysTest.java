import java.util.HashSet;
import java.util.Set;
import pzvr.harness.Hotkeys;

public final class HotkeysTest {
    private static int checks;
    private static void check(boolean value,String message) {
        checks++; if(!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Set<Integer> down=new HashSet<>();
        Hotkeys keys=new Hotkeys();
        check(keys.poll(true,0,down::contains)==0,"Initially idle");
        down.add(281);
        check(keys.poll(true,3,down::contains)==1,"Default XR chord");
        check(keys.poll(true,7,down::contains)==0,"Changing modifiers cannot recenter while held");
        down.clear(); keys.poll(true,0,down::contains); down.add(281);
        check(keys.poll(true,7,down::contains)==2,"Default recenter separate from toggle");
        down.clear(); keys.poll(true,0,down::contains); down.add(281);
        check(keys.poll(true,5,down::contains)==4,"Default preview");
        down.clear(); keys.poll(true,0,down::contains); down.add(299);
        check(keys.poll(true,3,down::contains)==8,"Default capture");
        keys.poll(false,3,down::contains);
        check(keys.poll(true,3,down::contains)==0,"Focus return cannot fire held key");

        // Native LWJGL2 key codes: A=30, B=48, C=46, D=32. GLFW uses ASCII.
        Hotkeys.configure(30,0,48,1,46,2,32,4);
        down.clear(); down.add(65);
        check(keys.poll(true,0,down::contains)==0,"Rebinding onto held key requires release");
        down.clear(); keys.poll(true,0,down::contains); down.add(281);
        check(keys.poll(true,3,down::contains)==0,"Old default is no longer active");
        down.clear(); keys.poll(true,0,down::contains); down.add(65);
        check(keys.poll(true,0,down::contains)==1,"Unmodified remapped key uses native-to-GLFW conversion");
        check(keys.poll(true,0,down::contains)==0,"Held key fires only once");
        down.clear(); keys.poll(true,0,down::contains); down.add(66);
        check(keys.poll(true,3,down::contains)==0,"Extra modifier does not match");
        check(keys.poll(true,1,down::contains)==0,"Removing modifier while held cannot fire");
        down.clear(); keys.poll(true,0,down::contains); down.add(66);
        check(keys.poll(true,1,down::contains)==2,"Rebound recenter");
        Hotkeys.block(true); keys.poll(true,1,down::contains); Hotkeys.block(false);
        check(keys.poll(true,1,down::contains)==0,"Closing settings cannot fire a held key");
        down.clear(); keys.poll(true,0,down::contains); down.add(67);
        check(keys.poll(true,2,down::contains)==4,"Rebound preview");
        down.clear(); keys.poll(true,0,down::contains); down.add(68);
        check(keys.poll(true,4,down::contains)==8,"Rebound capture");
        Hotkeys.configure(30,0,30,0,0,0,0,0);
        down.clear(); keys.poll(true,0,down::contains); down.add(65);
        check(keys.poll(true,0,down::contains)==0,"Identical chords do not trigger multiple actions");
        down.clear(); down.add(281); down.add(299);
        check(keys.poll(true,3,down::contains)==0,"Cleared shortcuts do not fall back to defaults");
        Hotkeys.configure(70,3,70,7,70,5,68,3);
        down.clear(); keys.poll(true,0,down::contains); down.add(281);
        check(keys.poll(true,3,down::contains)==1,"Defaults restore using real native key codes");
        // Unchanged configuration does not spuriously reset latches every sync.
        Hotkeys.configure(70,3,70,7,70,5,68,3);
        down.clear(); keys.poll(true,0,down::contains); down.add(281);
        check(keys.poll(true,7,down::contains)==2,"Unchanged settings preserve normal input");
        System.out.println("Configurable hotkey checks passed: "+checks);
    }
}
