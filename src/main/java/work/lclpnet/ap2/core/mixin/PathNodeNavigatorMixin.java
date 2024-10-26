package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.type.ApEntity;
import work.lclpnet.ap2.core.type.ApPath;

@Mixin(PathNodeNavigator.class)
public class PathNodeNavigatorMixin {

    @Shadow @Final private PathNodeMaker pathNodeMaker;

    @Inject(
            method = "createPath",
            at = @At("RETURN")
    )
    public void ap2$createPath(PathNode endNode, BlockPos target, boolean reachesTarget, CallbackInfoReturnable<Path> cir) {
        Path path = cir.getReturnValue();
        MobEntity entity = ((PathNodeMakerAccessor) this.pathNodeMaker).getEntity();

        if (((ApEntity) entity).ap2$isPatchNarrowMovement()) {
            ((ApPath) path).ap2$patchNarrowMovement();
        }
    }
}
