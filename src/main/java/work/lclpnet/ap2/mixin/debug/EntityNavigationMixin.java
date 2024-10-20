package work.lclpnet.ap2.mixin.debug;

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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.type.debug.EntityNavigationDebug;

import java.util.Set;

import static java.lang.System.out;

@Mixin(EntityNavigation.class)
public abstract class EntityNavigationMixin implements EntityNavigationDebug {

    @Shadow @Nullable protected Path currentPath;
    @Shadow private @Nullable BlockPos currentTarget;
    @Shadow @Final protected MobEntity entity;
    @Shadow @Final protected World world;

    @Shadow protected abstract boolean isAtValidPosition();

    @Unique private boolean debug = false;

    @Inject(
            method = "findPathToAny(Ljava/util/Set;IZIF)Lnet/minecraft/entity/ai/pathing/Path;",
            at = @At("HEAD")
    )
    public void ap2$onFindPathToAny(Set<BlockPos> positions, int range, boolean useHeadPos, int distance, float followRange, CallbackInfoReturnable<Path> cir) {
        if (!debug) return;

        out.println("Navigation is " + this.getClass().getSimpleName());

        out.println("return1 = " + positions.isEmpty());
        out.println("return2 = " + (this.entity.getY() < (double) this.world.getBottomY()));
        out.println("return3 = " + (!this.isAtValidPosition()));
        out.println(this.currentPath);
        out.println(this.currentTarget);

        int i = (int)(followRange + (float)range);

        out.println("i=" + i);

        BlockPos pos = useHeadPos ? this.entity.getBlockPos().up() : this.entity.getBlockPos();

        out.println("area from " + pos.add(-i, -i, -i) + " to " + pos.add(i, i, i));
    }

    @WrapOperation(
            method = "findPathToAny(Ljava/util/Set;IZIF)Lnet/minecraft/entity/ai/pathing/Path;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/pathing/PathNodeNavigator;findPathToAny(Lnet/minecraft/world/chunk/ChunkCache;Lnet/minecraft/entity/mob/MobEntity;Ljava/util/Set;FIF)Lnet/minecraft/entity/ai/pathing/Path;"
            )
    )
    public @Nullable Path ap2$debugFindPath(PathNodeNavigator instance, ChunkCache world, MobEntity mob, Set<BlockPos> positions, float followRange, int distance, float rangeMultiplier, Operation<Path> original) {
        if (!debug) {
            return original.call(instance, world, mob, positions, followRange, distance, rangeMultiplier);
        }

        out.println("invoking path finder...");

        Path path = original.call(instance, world, mob, positions, followRange, distance, rangeMultiplier);

        out.println("PATH IS " + path);

        return path;
    }

    @Override
    public void ap2$setDebug(boolean debug) {
        this.debug = debug;
    }
}
