package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
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
    @Unique
    private BlockPos.Mutable from = null;

    @Override
    public void ap2$enablePathfindingPatch() {
        this.pathfindingPatched = true;
        this.from = new BlockPos.Mutable();
    }

    @Inject(
            method = "getSuccessors",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/pathing/LandPathNodeMaker;getPathNode(IIIIDLnet/minecraft/util/math/Direction;Lnet/minecraft/entity/ai/pathing/PathNodeType;)Lnet/minecraft/entity/ai/pathing/PathNode;"
            )
    )
    public void kibu$storeFromPosition(PathNode[] successors, PathNode node, CallbackInfoReturnable<Integer> cir) {
        if (pathfindingPatched) {
            from.set(node.x, node.y, node.z);
        }
    }

    @Inject(
            method = "getJumpOnTopNode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/pathing/LandPathNodeMaker;getPathNode(IIIIDLnet/minecraft/util/math/Direction;Lnet/minecraft/entity/ai/pathing/PathNodeType;)Lnet/minecraft/entity/ai/pathing/PathNode;"
            )
    )
    public void kibu$storeFromJumpPosition(int x, int y, int z, int maxYStep, double prevFeetY, Direction direction, PathNodeType nodeType, BlockPos.Mutable mutablePos, CallbackInfoReturnable<PathNode> cir) {
        if (pathfindingPatched) {
            from.set(x, y, z);
        }
    }

    @Inject(
            method = "getPathNode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/pathing/LandPathNodeMaker;getNodeType(III)Lnet/minecraft/entity/ai/pathing/PathNodeType;"
            ),
            cancellable = true
    )
    public void kibu$modifyGetPathNode(int x, int y, int z, int maxYStep, double prevFeetY, Direction direction, PathNodeType nodeType, CallbackInfoReturnable<PathNode> cir) {
        if (!pathfindingPatched || from == null) return;

        MobEntity entity = ((PathNodeMakerAccessor) this).getEntity();

        if (BlockedPathfindingPatch.isBlocked(x, y, z, entity, from)) {
            cir.setReturnValue(null);
        }
    }
}
