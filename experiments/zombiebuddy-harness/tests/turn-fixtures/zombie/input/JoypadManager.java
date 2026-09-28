package zombie.input;public class JoypadManager {public static final JoypadManager instance=new JoypadManager();public Joypad[] joypadsController=new Joypad[16];public static class Joypad {
 public boolean disabled,connected=true;public int bumperLeft=4,bumperRight=5,rightStickButton=10;
 public float x,y,lt=-1;public boolean lb,rb,r3;
 public float getAimingAxisXRaw(){return x;}public float getAimingAxisYRaw(){return y;}public float getLTValue(){return lt;}
 public boolean isLTPressed(){return getLTValue()>.7;}public boolean isLBPressed(){return lb;}public boolean isRBPressed(){return rb;}public boolean isR3Pressed(){return r3;}
 public boolean isButtonPressed(int b){return b==4?lb:b==5?rb:b==10&&r3;}public boolean wasButtonPressed(int b){return isButtonPressed(b);}public boolean isButtonStartPress(int b){return isButtonPressed(b);}public boolean isButtonReleasePress(int b){return isButtonPressed(b);}
 }}