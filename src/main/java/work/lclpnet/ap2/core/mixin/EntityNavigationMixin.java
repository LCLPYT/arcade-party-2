package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkCache;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import work.lclpnet.ap2.core.hook.EntityPathFindingCallback;

import java.util.Set;

@Mixin(EntityNavigation.class)
public class EntityNavigationMixin {

    @Shadow @Final protected MobEntity entity;

    @Shadow @Final protected World world;

    @WrapOperation(
            method = "findPathToAny(Ljava/util/Set;IZIF)Lnet/minecraft/entity/ai/pathing/Path;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/pathing/PathNodeNavigator;findPathToAny(Lnet/minecraft/world/chunk/ChunkCache;Lnet/minecraft/entity/mob/MobEntity;Ljava/util/Set;FIF)Lnet/minecraft/entity/ai/pathing/Path;"
            )
    )
    public @Nullable Path ap2$modifyPath(PathNodeNavigator instance, ChunkCache world, MobEntity mob, Set<BlockPos> positions, float followRange, int distance, float rangeMultiplier, Operation<Path> original) {
        Path path = original.call(instance, world, mob,  positions, followRange, distance, rangeMultiplier);

        return EntityPathFindingCallback.HOOK.invoker().modifyPath(entity, path, positions,
                (target) -> instance.findPathToAny(world, mob, Set.of(target), followRange, distance, rangeMultiplier));
    }
}
