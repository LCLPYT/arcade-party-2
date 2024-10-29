package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.patch.BlockedPathfindingPatch;
import work.lclpnet.ap2.core.type.ApLandPathNodeMaker;

@Mixin(LandPathNodeMaker.class)
public class LandPathNodeMakerMixin implements ApLandPathNodeMaker {

    @Unique
    private boolean pathfindingPatched = false;

    @Inject(
            method = "getPathNode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/mob/MobEntity;getPathfindingPenalty(Lnet/minecraft/entity/ai/pathing/PathNodeType;)F"
            ),
            cancellable = true
    )
    public void kibu$modifyGetPathNode(int x, int y, int z, int maxYStep, double prevFeetY, Direction direction, PathNodeType nodeType, CallbackInfoReturnable<PathNode> cir,
                                       @Local(ordinal = 1) PathNodeType pathNodeType) {

        if (!pathfindingPatched) return;

        MobEntity entity = ((PathNodeMakerAccessor) this).getEntity();

        if (BlockedPathfindingPatch.isBlocked(x, y, z, entity, direction, pathNodeType)) {
            cir.setReturnValue(null);
        }
    }

    @Override
    public void ap2$enablePathfindingPatch() {
        this.pathfindingPatched = true;
    }
}
