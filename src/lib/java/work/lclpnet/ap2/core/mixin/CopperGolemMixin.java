package work.lclpnet.ap2.core.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.CopperGolemTurnIntoStatueCallback;

@Mixin(CopperGolem.class)
public class CopperGolemMixin {

    @Inject(
            method = "turnToStatue",
            at = @At("HEAD"),
            cancellable = true
    )
    public void ap2$turnIntoStatue(ServerLevel world, CallbackInfo ci) {
        var self = (CopperGolem) (Object) this;

        if (CopperGolemTurnIntoStatueCallback.HOOK.invoker().onTurnIntoStatue(self)) {
            ci.cancel();
        }
    }
}
