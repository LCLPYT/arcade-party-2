package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
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

@Mixin(WalkNodeEvaluator.class)
public class WalkNodeEvaluatorMixin implements ApLandPathNodeMaker {

    @Unique @Nullable
    private volatile List<PathFindingPredicate> customBlocked = null;
    @Unique @Nullable
    private volatile List<PathFindingPredicate> customInvalid = null;
    @Unique
    private BlockPos.MutableBlockPos from = null;

    @Unique
    private void initFrom() {
        if (from != null) return;

        synchronized (this) {
            if (from != null) return;

            from = new BlockPos.MutableBlockPos();
        }
    }

    @SuppressWarnings("DuplicatedCode")
    @Unique @NotNull
    private List<PathFindingPredicate> initCustomBlocked() {
        var pred = customBlocked;

        if (pred != null) {
            return pred;
        }

        synchronized (this) {
            pred = customBlocked;

            if (pred == null) {
                pred = customBlocked = new ArrayList<>();
                initFrom();
            }
        }

        return pred;
    }

    @SuppressWarnings("DuplicatedCode")
    @Unique @NotNull
    private List<PathFindingPredicate> initCustomInvalid() {
        var pred = customInvalid;

        if (pred != null) {
            return pred;
        }

        synchronized (this) {
            pred = customInvalid;

            if (pred == null) {
                pred = customInvalid = new ArrayList<>();
                initFrom();
            }
        }

        return pred;
    }

    @Override
    public void ap2$addCustomBlockedPredicate(PathFindingPredicate predicate) {
        initCustomBlocked().add(predicate);
    }

    @Override
    public void ap2$addCustomInvalidPredicate(PathFindingPredicate predicate) {
        initCustomInvalid().add(predicate);
    }

    @Inject(
            method = "getNeighbors",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/pathfinder/WalkNodeEvaluator;findAcceptedNode(IIIIDLnet/minecraft/core/Direction;Lnet/minecraft/world/level/pathfinder/PathType;)Lnet/minecraft/world/level/pathfinder/Node;"
            )
    )
    public void ap2$storeFromPosition(Node[] successors, Node node, CallbackInfoReturnable<Integer> cir) {
        if (customBlocked != null) {
            from.set(node.x, node.y, node.z);
        }
    }

    @WrapOperation(
            method = "findAcceptedNode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/pathfinder/WalkNodeEvaluator;getCachedPathType(III)Lnet/minecraft/world/level/pathfinder/PathType;"
            )
    )
    public PathType ap2$modifyNodeBlocked(WalkNodeEvaluator instance, int x, int y, int z, Operation<PathType> original) {
        var pred = customBlocked;

        if (pred == null || from == null) {
            return original.call(instance, x, y, z);
        }

        Mob entity = ((NodeEvaluatorAccessor) this).getMob();

        for (PathFindingPredicate predicate : pred) {
            if (!predicate.canReach(x, y, z, entity, from)) {
                return PathType.BLOCKED;
            }
        }

        return original.call(instance, x, y, z);
    }

    @Inject(
            method = "findAcceptedNode",
            at = @At("RETURN"),
            cancellable = true
    )
    public void ap2$modifyNodeValid(int x, int y, int z, int maxYStep, double lastFeetY, Direction direction, PathType nodeType, CallbackInfoReturnable<Node> cir) {
        Node node = cir.getReturnValue();

        if (node == null) return;

        var pred = customInvalid;

        if (pred == null || from == null) {
            return;
        }

        Mob entity = ((NodeEvaluatorAccessor) this).getMob();

        for (PathFindingPredicate predicate : pred) {
            if (!predicate.canReach(node.x, node.y, node.z, entity, from)) {
                cir.setReturnValue(null);
                return;
            }
        }
    }
}
