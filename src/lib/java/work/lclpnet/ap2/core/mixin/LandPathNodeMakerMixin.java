package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
    private volatile List<PathFindingPredicate> customBlocked = null;
    @Unique @Nullable
    private volatile List<PathFindingPredicate> customInvalid = null;
    @Unique
    private BlockPos.Mutable from = null;

    @Unique
    private void initFrom() {
        if (from != null) return;

        synchronized (this) {
            if (from != null) return;

            from = new BlockPos.Mutable();
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
            method = "getSuccessors",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/pathing/LandPathNodeMaker;getPathNode(IIIIDLnet/minecraft/util/math/Direction;Lnet/minecraft/entity/ai/pathing/PathNodeType;)Lnet/minecraft/entity/ai/pathing/PathNode;"
            )
    )
    public void ap2$storeFromPosition(PathNode[] successors, PathNode node, CallbackInfoReturnable<Integer> cir) {
        if (customBlocked != null) {
            from.set(node.x, node.y, node.z);
        }
    }

    @WrapOperation(
            method = "getPathNode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/pathing/LandPathNodeMaker;getNodeType(III)Lnet/minecraft/entity/ai/pathing/PathNodeType;"
            )
    )
    public PathNodeType ap2$modifyNodeBlocked(LandPathNodeMaker instance, int x, int y, int z, Operation<PathNodeType> original) {
        var pred = customBlocked;

        if (pred == null || from == null) {
            return original.call(instance, x, y, z);
        }

        MobEntity entity = ((PathNodeMakerAccessor) this).getEntity();

        for (PathFindingPredicate predicate : pred) {
            if (!predicate.canReach(x, y, z, entity, from)) {
                return PathNodeType.BLOCKED;
            }
        }

        return original.call(instance, x, y, z);
    }

    @Inject(
            method = "getPathNode",
            at = @At("RETURN"),
            cancellable = true
    )
    public void ap2$modifyNodeValid(int x, int y, int z, int maxYStep, double lastFeetY, Direction direction, PathNodeType nodeType, CallbackInfoReturnable<PathNode> cir) {
        PathNode node = cir.getReturnValue();

        if (node == null) return;

        var pred = customInvalid;

        if (pred == null || from == null) {
            return;
        }

        MobEntity entity = ((PathNodeMakerAccessor) this).getEntity();

        for (PathFindingPredicate predicate : pred) {
            if (!predicate.canReach(node.x, node.y, node.z, entity, from)) {
                cir.setReturnValue(null);
                return;
            }
        }
    }
}
