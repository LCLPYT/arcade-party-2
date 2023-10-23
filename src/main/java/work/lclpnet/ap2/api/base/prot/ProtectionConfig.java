package work.lclpnet.ap2.api.base.prot;

public interface ProtectionConfig {

    void allow(ProtectionType type);

    void disallow(ProtectionType type);

    boolean isAllowed(ProtectionType type);

    default void allow(ProtectionType... types) {
        for (ProtectionType extra : types) {
            allow(extra);
        }
    }

    default void disallow(ProtectionType... types) {
        for (ProtectionType extra : types) {
            disallow(extra);
        }
    }

    default void disallowAll() {
        disallow(ProtectionType.values());
    }
}
