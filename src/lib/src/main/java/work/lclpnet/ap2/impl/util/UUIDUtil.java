package work.lclpnet.ap2.impl.util;

import java.util.UUID;

public class UUIDUtil {

    public static UUID getUuid(int mostSig1, int mostSig2, int leastSig1, int leastSig2) {
        long mostSig = ((long) mostSig1 << 32) | mostSig2;
        long leastSig = ((long) leastSig1 << 32) | leastSig2;
        return new UUID(mostSig, leastSig);
    }

    private UUIDUtil() {}
}
