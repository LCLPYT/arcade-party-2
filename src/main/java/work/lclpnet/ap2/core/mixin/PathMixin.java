package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.patch.NarrowMovementPatch;
import work.lclpnet.ap2.core.type.ApPath;

import java.util.List;

@Mixin(Path.class)
public class PathMixin implements ApPath {

    @Shadow @Final private List<PathNode> nodes;
    @Unique private boolean patchNarrowMovement = false;

    @Override
    public void ap2$patchNarrowMovement() {
        this.patchNarrowMovement = true;
    }

    /*
     This injection patches pathing through nodes that contain slim blocks, e.g. open trapdoors.
     Entities with big hit-boxes, like the warden, get stuck when trying to path through spaces with slim blocks on the side,
     although there should be enough space because of a nearby space.

          move right
          a tiny bit:
       ┌─┐             ┌─┐
       └─┘             └┬┘
     ──┐│  ┌───     ──┐ │ ┌───
       ││  │    ──►   │ │ │
       │▼  │          │ ▼ │
       │   │          │   │
       │   │          │   │
    */
    @Inject(
            method = "getNodePosition(Lnet/minecraft/entity/Entity;I)Lnet/minecraft/util/math/Vec3d;",
            at = @At("HEAD"),
            cancellable = true
    )
    public void ap2$patchNarrowMovement(Entity entity, int index, CallbackInfoReturnable<Vec3d> cir) {
        if (!patchNarrowMovement) return;

        PathNode node = this.nodes.get(index);

        Vec3d pos = NarrowMovementPatch.getNodePosition(entity, node);

        if (pos == null) return;

        cir.setReturnValue(pos);
    }
}
