import java.nio.file.*;
import pzvr.VersionGate;
public final class VersionGateVariantsTest {
 public static void main(String[] args)throws Exception{
  Path game=Path.of(args[0]),pz=Path.of(args[1]),original=Path.of(args[2]),fix=Path.of(args[3]);
  VersionGate.verify(game,pz,original);VersionGate.verify(game,pz,fix);
  Path bad=Files.createTempFile("pzvr-unsupported-", ".bin");
  try{
   Files.writeString(bad,"Unsupported binary fixture");
   try{VersionGate.verify(game,pz,bad);throw new AssertionError("Unknown loader accepted");}catch(IllegalStateException expected){}
   try{VersionGate.verify(bad,pz,fix);throw new AssertionError("Unknown game accepted");}catch(IllegalStateException expected){}
  }finally{Files.deleteIfExists(bad);}
  System.out.println("Version gate variant checks passed: 4; both pinned loaders accepted, unknown loader/game rejected.");
 }
}
