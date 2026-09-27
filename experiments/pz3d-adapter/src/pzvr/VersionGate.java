package pzvr;

import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** A mismatch is a hard failure. Inputs are read only. */
public final class VersionGate {
    private VersionGate() {}
    public static final String GAME="80e405a4bfc42f6072e75b3735f458a6514143da011d3226007ded305a442f44";
    public static final String PZ3D="75cf9b39851b2a8f71fd9e3e3d30620ef1f5c5006e0f1af9bf974b4a800486a5";
    public static final String ZB="6dd95cedce60f03bf8b8cefd0d19eb156230e0d54bffa07de9da5212a06c7be6";
    public static final class Verified { private Verified() {} }
    public static Verified verify(Path game,Path pz3d,Path zombieBuddy) throws Exception {
        check(game,GAME); check(pz3d,PZ3D); check(zombieBuddy,ZB); return new Verified();
    }
    public static void check(Path path,String expected) throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        try(var in=Files.newInputStream(path)) { byte[] b=new byte[65536]; int n; while((n=in.read(b))!=-1) digest.update(b,0,n); }
        String actual=HexFormat.of().formatHex(digest.digest());
        if(!actual.equals(expected)) throw new IllegalStateException("Unsupported binary: "+path+" SHA-256="+actual);
    }
}
