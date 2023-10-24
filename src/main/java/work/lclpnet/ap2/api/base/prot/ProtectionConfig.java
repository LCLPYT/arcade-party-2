package work.lclpnet.ap2.api.base.prot;

import org.jetbrains.annotations.NotNull;

public interface ProtectionConfig {

    void allow(ProtectionType type, ProtectionScope scope);

    void disallow(ProtectionType type, ProtectionScope scope);

    ProtectionScope getAllowedScope(ProtectionType type);

    @NotNull
    ProtectionScope getDisallowedScope(ProtectionType type);

    default void allow(ProtectionType type) {
        allow(type, ProtectionScope.GLOBAL);
    }

    default void disallow(ProtectionType type) {
        disallow(type, ProtectionScope.GLOBAL);
    }

    default void allow(ProtectionScope scope, ProtectionType... types) {
        for (ProtectionType type : types) {
            allow(type, scope);
        }
    }

    default void allow(ProtectionType... types) {
        allow(ProtectionScope.GLOBAL, types);
    }

    default void disallow(ProtectionScope scope, ProtectionType... types) {
        for (ProtectionType type : types) {
            disallow(type, scope);
        }
    }

    default void disallow(ProtectionType... types) {
        disallow(ProtectionScope.GLOBAL, types);
    }

    default void disallowAll(ProtectionScope scope) {
        disallow(scope, ProtectionType.values());
    }

    default void disallowAll() {
        disallow(ProtectionType.values());
    }

    default boolean hasRestrictions(ProtectionType type) {
        return getDisallowedScope(type) != ProtectionScope.EMPTY;
    }
}
