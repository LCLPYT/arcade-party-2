package work.lclpnet.ap2.core.mixin.ai;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
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

    @Shadow @Final private List<Node> nodes;
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
            method = "getEntityPosAtNode(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true
    )
    public void ap2$patchNarrowMovement(Entity entity, int index, CallbackInfoReturnable<Vec3> cir) {
        if (!patchNarrowMovement) return;

        Node node = this.nodes.get(index);

        Vec3 pos = NarrowMovementPatch.getNodePosition(entity, node.x, node.y, node.z);

        if (pos == null) return;

        cir.setReturnValue(pos);
    }
}
