package hu.diku.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class RequestFingerprint {
    private RequestFingerprint() {}
    static String of(Object... fields) {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            for(Object field:fields) {
                String value=field==null?"":field.toString();
                digest.update((value.length()+":"+value).getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
