package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.passive.CopperGolemEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.CopperGolemTurnIntoStatueCallback;

@Mixin(CopperGolemEntity.class)
public class CopperGolemEntityMixin {

    @Inject(
            method = "turnIntoStatue",
            at = @At("HEAD"),
            cancellable = true
    )
    public void ap2$turnIntoStatue(ServerWorld world, CallbackInfo ci) {
        var self = (CopperGolemEntity) (Object) this;

        if (CopperGolemTurnIntoStatueCallback.HOOK.invoker().onTurnIntoStatue(self)) {
            ci.cancel();
        }
    }
}
