import java.nio.file.*;
import java.util.*;
import pzvr.*;
import com.pavelvoronin.pz3d.Renderer;
import org.lwjgl.opengl.GL30;

public final class AdapterTest {
    static int checks;
    static void check(boolean condition,String message) { checks++; if(!condition) throw new AssertionError(message); }
    interface Work { void run() throws Exception; }
    static void rejected(Work work) throws Exception { try { work.run(); } catch(Exception expected) { checks++; return; } throw new AssertionError("Expected rejection"); }
    static void balanced(Renderer.Frame frame) {
        check(frame.lease.refs==1 && frame.lease.retains==1 && frame.lease.releases==1,"Lease ownership");
        check(!PairHooks.active(),"Thread-local cleanup");
        check(frame.x==11 && frame.y==20 && frame.z==1.7f,"Prepared camera restored");
    }
    public static void main(String[] args) throws Exception {
        var verified=VersionGate.verify(Path.of(args[0]),Path.of(args[1]),Path.of(args[2]));
        // Initialize the fixture render-thread identity on this thread.
        check(zombie.core.opengl.RenderThread.renderThread==Thread.currentThread(),"Fixture owner");
        Renderer.reset();
        Renderer.Frame normal=new Renderer.Frame(); normal.draw(true);
        check(Renderer.prepared==1 && Renderer.shadows==1 && Renderer.visibility==1 && GL30.blits==1 && Renderer.effects==1,"Inactive original path");
        Renderer.reset();
        Renderer.Frame frame=new Renderer.Frame();
        var result=PairHooks.render(verified,frame,true,PairHooks.synthetic(.064f),(eye,source,w,h)-> {
            check(source==42 && w==800 && h==600,"Scratch target metadata");
            check(eye!=0 || !frame.corpse.rendered,"Corpse flag deferred until second eye");
            Renderer.events.add("copy"+eye);
            Renderer.weatherChange();
        });
        check(result.preparations()==1 && result.copies()==2 && result.sceneVersion()==7,"Pair counters");
        check(Renderer.prepared==1 && Renderer.shadows==1 && Renderer.visibility==2 && Renderer.tail==1,"One prepare, two views, one tail");
        check(Renderer.effects==0 && GL30.blits==0,"Optional effect/desktop blit omitted");
        check(Renderer.events.equals(List.of("adopt","prepare","shadows","draw1","copy0","draw2","copy1")),"Copy occurs before scratch reuse");
        check(Math.abs(frame.eyeY.get(0)-19.968f)<.00001f && Math.abs(frame.eyeY.get(1)-20.032f)<.00001f,"Distinct eye origins");
        check(frame.fade.get(0).equals(frame.fade.get(1)),"Frozen shader time");
        check(frame.environments.get(0)==frame.environments.get(1),"Weather changes do not split tree environment state");
        check(frame.corpse.rendered,"Final corpse state preserved"); balanced(frame);

        for(boolean fail:new boolean[]{false,true}) {
            Renderer.reset(); Renderer.Frame posed=new Renderer.Frame();
            var data=posed.characters.getFirst().parts.getFirst().data;
            var part=posed.characters.getFirst().parts.getFirst();
            var attachment=new org.joml.Matrix4f().translation(4,5,6).rotateY(.7f).transpose();
            var original=data.matrixPalette;
            var override=java.nio.FloatBuffer.wrap(new float[]{5,6,7,8});
            int[] scopes={0,0};
            Work render=()->PairHooks.render(verified,posed,true,PairHooks.synthetic(.064f),(eye,s,w,h)-> {
                check(data.matrixPalette==override,"Both eyes see the same owned pose");
                if(fail&&eye==1) throw new IllegalStateException("Injected posed-eye failure");
            },(f,b)-> {
                scopes[0]++; data.matrixPalette=override;
                var items=AttachmentPoses.open(Map.of(part,attachment));
                return ()->{ items.close(); scopes[1]++; data.matrixPalette=original; };
            });
            if(fail) rejected(render); else render.run();
            check(scopes[0]==1&&scopes[1]==1&&data.matrixPalette==original,"Pose ownership restored once on success/failure");
            check(part.uploaded.size()==2&&part.uploaded.get(0).equals(attachment)&&part.uploaded.get(1).equals(attachment),"Transformed attachment upload uses same override in both eyes");
            check(data.xfrm.equals(new org.joml.Matrix4f())&&AttachmentPoses.resolve(data.xfrm,part)==data.xfrm,"Native attachment matrix untouched and scope released on success/failure");
            balanced(posed);
        }

        Renderer.reset(); Renderer.Frame broken=new Renderer.Frame();
        rejected(()->PairHooks.render(verified,broken,true,PairHooks.synthetic(.064f),(eye,s,w,h)-> { if(eye==1) throw new IllegalStateException("copy failed"); }));
        check(broken.caught!=null,"Original draw swallowed test failure, wrapper detected incomplete pair"); balanced(broken);

        Renderer.reset(); Renderer.Frame changed=new Renderer.Frame();
        rejected(()->PairHooks.render(verified,changed,true,PairHooks.synthetic(.064f),(eye,s,w,h)->Renderer.invalidate()));
        check(changed.eyeY.size()==1,"Generation change stops second eye"); balanced(changed);

        Renderer.reset(); Renderer.Frame mutated=new Renderer.Frame(); mutated.mutate=true;
        rejected(()->PairHooks.render(verified,mutated,true,PairHooks.synthetic(.064f),(eye,s,w,h)->{}));
        check(mutated.eyeY.size()==1,"Body mutation detected before copy"); balanced(mutated);

        Renderer.reset(); Renderer.Frame missing=new Renderer.Frame(); missing.skip=true;
        rejected(()->PairHooks.render(verified,missing,true,PairHooks.synthetic(.064f),(eye,s,w,h)->{}));
        check(missing.lease.refs==1 && !PairHooks.active(),"Early skip cleanup");

        Renderer.reset(); Renderer.Frame nested=new Renderer.Frame();
        rejected(()->PairHooks.render(verified,nested,true,PairHooks.synthetic(.064f),(eye,s,w,h)-> {
            try { PairHooks.render(verified,nested,true,PairHooks.synthetic(.064f),(i,f,w2,h2)->{}); }
            catch(Exception ex) { throw new IllegalStateException(ex); }
        })); balanced(nested);

        Renderer.reset(); Renderer.Frame wrongThread=new Renderer.Frame(); boolean[] denied={false};
        Thread t=new Thread(()-> { try { PairHooks.render(verified,wrongThread,true,PairHooks.synthetic(.064f),(i,f,w,h)->{}); } catch(Exception expected) { denied[0]=true; } });
        t.start(); t.join(); check(denied[0] && wrongThread.lease.retains==0,"Wrong thread rejected before retention");

        Path altered=Files.createTempFile(Path.of(args[3]),"wrong-version-",".bin");
        try { Files.writeString(altered,"not the supported binary"); rejected(()->VersionGate.check(altered,VersionGate.PZ3D)); }
        finally { Files.delete(altered); }

        GlEyeTargets targets=new GlEyeTargets(70,71,400,300);
        targets.copy(0,42,800,600);
        check(GL30.lastSource==42 && GL30.lastTarget==70 && GL30.read==12 && GL30.draw==13,"Left copy and FBO restoration");
        targets.copy(1,42,800,600);
        check(GL30.lastTarget==71 && GL30.read==12 && GL30.draw==13,"Right copy and FBO restoration");
        rejected(()->targets.copy(0,70,800,600));
        GL30.complete=false;
        rejected(()->targets.copy(0,42,800,600));
        check(GL30.read==12 && GL30.draw==13,"FBO restoration on copy failure");
        rejected(()->new GlEyeTargets(1,1,400,300));
        System.out.println("Adapter fixture checks passed: "+checks+". No game or OpenGL execution.");
    }
}
