package work.lclpnet.ap2.api.base.prot;

import org.jetbrains.annotations.Nullable;

public interface ProtectionConfig {

    <T> void allow(ProtectionType<T> type, T scope);

    <T> void disallow(ProtectionType<T> type, T scope);

    <T> boolean hasRestrictions(ProtectionType<T> type);

    @Nullable
    <T> T getAllowedScope(ProtectionType<T> type);

    @Nullable
    <T> T getDisallowedScope(ProtectionType<T> type);

    default <T> void allow(ProtectionType<T> type) {
        allow(type, type.getGlobalScope());
    }

    default <T> void disallow(ProtectionType<T> type) {
        disallow(type, type.getGlobalScope());
    }

    default void allow(ProtectionType<?>... types) {
        for (ProtectionType<?> type : types) {
            allow(type);
        }
    }

    default void disallow(ProtectionType<?>... types) {
        for (ProtectionType<?> type : types) {
            disallow(type);
        }
    }

    /**
     * Disallows all builtin protection types from {@link BuiltinProtectionTypes}.
     */
    default void disallowAll() {
        disallow(BuiltinProtectionTypes.getTypes()
                .toArray(ProtectionType[]::new));
    }
}
