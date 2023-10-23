package work.lclpnet.ap2.api.base.prot;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

public interface ProtectionScope {

    boolean isWithinScope(Entity entity);

    // builtins
    ProtectionScope GLOBAL = entity -> true;
    ProtectionScope NO_ADMIN = entity -> !(entity instanceof ServerPlayerEntity p) || !p.isCreativeLevelTwoOp();

    static ProtectionScope inWorld(World world) {
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
    static ProtectionScope union(ProtectionScope first, ProtectionScope second, ProtectionScope... rest) {
        return entity -> {
            if (first.isWithinScope(entity) || second.isWithinScope(entity)) {
                return true;
            }

            for (ProtectionScope scope : rest) {
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
    static ProtectionScope intersect(ProtectionScope first, ProtectionScope second, ProtectionScope... rest) {
        return entity -> {
            if (!first.isWithinScope(entity) || !second.isWithinScope(entity)) {
                return false;
            }

            for (ProtectionScope scope : rest) {
                if (!scope.isWithinScope(entity)) {
                    return false;
                }
            }

            return true;
        };
    }
}
