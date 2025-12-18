package work.lclpnet.ap2.core.hook;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

import java.util.Set;
import java.util.function.Function;

public interface EntityPathFindingCallback {

    Hook<EntityPathFindingCallback> HOOK = HookFactory.createArrayBacked(EntityPathFindingCallback.class, callbacks ->
            (entity, original, targets, pathFinder) -> {
                for (var cb : callbacks) {
                     original = cb.modifyPath(entity, original, targets, pathFinder);
                }

                return original;
            });

    @Nullable Path modifyPath(Entity entity, @Nullable Path original, Set<BlockPos> targets, Function<BlockPos, @Nullable Path> pathFinder);
}
