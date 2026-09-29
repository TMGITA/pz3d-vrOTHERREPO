package pzvr;

import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** A mismatch is a hard failure. Inputs are read only. */
public final class VersionGate {
    private VersionGate() {}
    public static final String GAME="e1a69eb743ede60b213a0fe7f8b83d4fcab773036d256cc4543a336f3b058a33";
    public static final String PZ3D="e8ddcdb6047dfe1bffe017aceda12169497f60a19d4039a171d44199a61df57d";
    public static final String ZB="6dd95cedce60f03bf8b8cefd0d19eb156230e0d54bffa07de9da5212a06c7be6";
    public static final String ZB_4221_FIX="dd13e6e06e64be0e832a4f13508c6872de36c9c7b2290023c5884a7f74467283";
    public static final class Verified { private Verified() {} }
    public static Verified verify(Path game,Path pz3d,Path zombieBuddy) throws Exception {
        check(game,GAME); check(pz3d,PZ3D); checkAny(zombieBuddy,ZB,ZB_4221_FIX); return new Verified();
    }
    public static void check(Path path,String expected) throws Exception {
        checkAny(path,expected);
    }
    private static void checkAny(Path path,String... expected) throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        try(var in=Files.newInputStream(path)) { byte[] b=new byte[65536]; int n; while((n=in.read(b))!=-1) digest.update(b,0,n); }
        String actual=HexFormat.of().formatHex(digest.digest());
        if(!java.util.Arrays.asList(expected).contains(actual)) throw new IllegalStateException("Unsupported binary: "+path+" SHA-256="+actual);
    }
}
