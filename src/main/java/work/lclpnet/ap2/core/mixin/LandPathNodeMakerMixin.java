package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.api.ai.PathFindingPredicate;
import work.lclpnet.ap2.core.type.ApLandPathNodeMaker;

import java.util.ArrayList;
import java.util.List;

@Mixin(LandPathNodeMaker.class)
public class LandPathNodeMakerMixin implements ApLandPathNodeMaker {

    @Unique @Nullable
    private volatile List<PathFindingPredicate> predicates = null;
    @Unique
    private BlockPos.Mutable from = null;

    @Unique @NotNull
    private List<PathFindingPredicate> initPredicates() {
        var pred = predicates;

        if (pred != null) {
            return pred;
        }

        synchronized (this) {
            pred = predicates;

            if (pred == null) {
                pred = predicates = new ArrayList<>(1);
                from = new BlockPos.Mutable();
            }
        }

        return pred;
    }

    @Override
    public void ap2$addPathFindingPredicate(PathFindingPredicate predicate) {
        initPredicates().add(predicate);
    }

    @Inject(
            method = "getSuccessors",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/pathing/LandPathNodeMaker;getPathNode(IIIIDLnet/minecraft/util/math/Direction;Lnet/minecraft/entity/ai/pathing/PathNodeType;)Lnet/minecraft/entity/ai/pathing/PathNode;"
            )
    )
    public void kibu$storeFromPosition(PathNode[] successors, PathNode node, CallbackInfoReturnable<Integer> cir) {
        if (predicates != null) {
            from.set(node.x, node.y, node.z);
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
        var pred = predicates;

        if (pred == null || from == null) return;

        MobEntity entity = ((PathNodeMakerAccessor) this).getEntity();

        for (PathFindingPredicate predicate : pred) {
            if (!predicate.canReach(x, y, z, entity, from)) {
                cir.setReturnValue(null);
                break;
            }
        }
    }
}
