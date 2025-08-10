package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.util.math.BlockPos;
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
