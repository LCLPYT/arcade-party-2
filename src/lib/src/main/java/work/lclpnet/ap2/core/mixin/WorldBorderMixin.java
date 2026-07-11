package work.lclpnet.ap2.core.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.WorldBorderPhaseCallback;

@Mixin(WorldBorder.class)
public class WorldBorderMixin {

    @Inject(method = "isInsideCloseToBorder", at = @At("HEAD"), cancellable = true)
    private void ap2$phaseThroughBorder(Entity entity, AABB box, CallbackInfoReturnable<Boolean> cir) {
        var self = (WorldBorder) (Object) this;

        if (WorldBorderPhaseCallback.HOOK.invoker().shouldPhase(self, entity)) {
            cir.setReturnValue(false);
        }
        }
}
