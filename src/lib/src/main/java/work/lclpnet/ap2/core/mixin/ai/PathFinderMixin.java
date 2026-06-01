package work.lclpnet.ap2.core.mixin.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.type.ApEntity;
import work.lclpnet.ap2.core.type.ApPath;

@Mixin(PathFinder.class)
public class PathFinderMixin {

    @Shadow @Final private NodeEvaluator nodeEvaluator;

    @Inject(
            method = "reconstructPath",
            at = @At("RETURN")
    )
    public void ap2$createPath(Node closest, BlockPos target, boolean reached, CallbackInfoReturnable<Path> cir) {
        Path path = cir.getReturnValue();
        Mob entity = ((NodeEvaluatorAccessor) this.nodeEvaluator).getMob();

        if (((ApEntity) entity).ap2$isPatchNarrowMovement()) {
            ((ApPath) (Object) path).ap2$patchNarrowMovement();
        }
    }
}
