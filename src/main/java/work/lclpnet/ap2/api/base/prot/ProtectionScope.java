package work.lclpnet.ap2.api.base.prot;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

public interface ProtectionScope<T> {

    boolean isWithinScope(T object);

    // builtins
    ProtectionScope<?> EMPTY = entity -> false;
    ProtectionScope<?> GLOBAL = entity -> true;
    ProtectionScope<Entity> NO_ADMIN = entity -> !(entity instanceof ServerPlayerEntity p) || !p.isCreativeLevelTwoOp();

    static ProtectionScope<Entity> inWorld(World world) {
        return entity -> entity.getWorld() == world;
    }

    /**
     * Returns a Protection scope that represents the union of the given scopes.
     * The resulting scope encompasses everything within the given scopes.
     * <br>
     * The resulting scope is equivalent to:
     * <code>first || second || ...rest</code>
     * @param first  The first scope.
     * @param second The second scope.
     * @param rest Optionally additional scopes.
     * @return A union scope of the given scopes.
     */
    @SafeVarargs
    static <T> ProtectionScope<T> union(ProtectionScope<T> first, ProtectionScope<T> second, ProtectionScope<T>... rest) {
        return entity -> {
            if (first.isWithinScope(entity) || second.isWithinScope(entity)) {
                return true;
            }

            for (ProtectionScope<T> scope : rest) {
                if (scope.isWithinScope(entity)) {
                    return true;
                }
            }

            return false;
        };
    }

    /**
     * Returns a Protection scope that represents the intersection of the given scopes.
     * The resulting scope encompasses everything within each of the given scopes.
     * <br>
     * The resulting scope is equivalent to:
     * <code>first && second && ...rest</code>
     * @param first  The first scope.
     * @param second The second scope.
     * @param rest Optionally additional scopes.
     * @return A intersection scope of the given scopes.
     */
    @SafeVarargs
    static <T> ProtectionScope<T> intersect(ProtectionScope<T> first, ProtectionScope<T> second, ProtectionScope<T>... rest) {
        return entity -> {
            if (!first.isWithinScope(entity) || !second.isWithinScope(entity)) {
                return false;
            }

            for (ProtectionScope<T> scope : rest) {
                if (!scope.isWithinScope(entity)) {
                    return false;
                }
            }

            return true;
        };
    }

    static <T> ProtectionScope<T> minus(ProtectionScope<T> first, ProtectionScope<T> second) {
        return entity -> first.isWithinScope(entity) && !second.isWithinScope(entity);
    }

    static <T> ProtectionScope<T> not(ProtectionScope<T> scope) {
        return entity -> !scope.isWithinScope(entity);
    }
}
