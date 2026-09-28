package pzvr.input;

public final class GamepadMapperTest {
    private static int checks;
    private static void ok(boolean b,String message) { checks++; if(!b) throw new AssertionError(message); }
    private static VrControllerState.Hand h(float x,float y,float t,float g,boolean a,boolean b,boolean click,boolean menu) {
        return new VrControllerState.Hand("touch",true,x,y,t,g,a,b,click,menu);
    }
    private static final VrControllerState.Hand NEUTRAL=h(0,0,0,0,false,false,false,false);
    private static VrControllerState s(VrControllerState.Hand l,VrControllerState.Hand r) { return new VrControllerState(1,100,true,l,r); }
    public static void main(String[] args) {
        var m=new GamepadMapper(); var left=h(.6f,.7f,.25f,.8f,true,true,true,false); var right=h(-.5f,-.8f,.75f,.8f,true,true,true,false);
        var p=m.map(s(left,right),101,true); ok(p.axes()[0]==0&&!p.buttons()[0],"held startup blocked");
        m.map(s(NEUTRAL,NEUTRAL),101,true); p=m.map(s(left,right),101,true);
        float[] expected={.6f,-.7f,-.5f,.8f,-.5f,.5f};
        for(int i=0;i<6;i++) ok(p.axes()[i]==expected[i],"axis "+i);
        for(int i:new int[]{0,1,2,3,4,5,9,10}) ok(p.buttons()[i],"button "+i);
        ok(!p.buttons()[8],"system reserved");
        p=m.map(s(h(0,0,0,.5f,false,false,false,false),NEUTRAL),101,true);ok(p.buttons()[4],"squeeze hysteresis");
        p=m.map(s(h(0,0,0,.4f,false,false,false,false),NEUTRAL),101,true);ok(!p.buttons()[4],"squeeze released");
        m.map(s(h(0,0,0,0,false,false,false,true),NEUTRAL),101,true);
        p=m.map(s(NEUTRAL,NEUTRAL),101,true);ok(p.buttons()[7],"menu tap start");
        p=m.map(s(NEUTRAL,NEUTRAL),101,true);ok(!p.buttons()[7],"one start pulse");
        p=m.map(s(h(.9f,.9f,0,0,true,false,false,true),NEUTRAL),101,true);
        ok(p.hat()==3&&p.buttons()[11]&&p.buttons()[12],"dpad diagonal");
        ok(p.buttons()[6]&&!p.buttons()[2]&&p.axes()[0]==0&&p.axes()[1]==0,"modifier consumes mapped controls");
        p=m.map(s(NEUTRAL,NEUTRAL),101,true);ok(!p.buttons()[7],"alternate has no start");
        for(var invalid:new VrControllerState[]{VrControllerState.EMPTY,new VrControllerState(2,100,false,left,right),s(VrControllerState.NONE,right),s(h(Float.NaN,0,0,0,false,false,false,false),right)}) {
            p=m.map(invalid,101,true);ok(p.axes()[4]==-1&&p.axes()[0]==0&&!p.buttons()[0],"unavailable neutral");
            p=m.map(s(left,right),101,true);ok(p.axes()[0]==0,"resume requires neutral");m.map(s(NEUTRAL,NEUTRAL),101,true);
        }
        p=m.map(s(left,right),250_000_101L,true);ok(p.axes()[4]==-1,"stale neutral");
        m.map(s(NEUTRAL,NEUTRAL),101,true);p=m.map(s(left,right),101,false);ok(p.axes()[4]==-1,"off neutral");
        m.map(s(NEUTRAL,NEUTRAL),101,true);m.map(s(h(0,0,0,0,false,false,false,true),NEUTRAL),101,true);
        m.map(VrControllerState.EMPTY,101,true);p=m.map(s(NEUTRAL,NEUTRAL),101,true);ok(!p.buttons()[7],"focus loss clears menu pending");
        System.out.println("Gamepad mapping checks passed: "+checks);
    }
}
