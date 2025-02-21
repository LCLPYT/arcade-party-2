package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.EntitySpawnCallback;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {

    @Inject(
            method = "addEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerEntityManager;addEntity(Lnet/minecraft/world/entity/EntityLike;)Z"
            ),
            cancellable = true
    )
    public void ap2$beforeAddEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        var self = (ServerWorld) (Object) this;

        if (EntitySpawnCallback.HOOK.invoker().onSpawn(entity, self)) {
            cir.setReturnValue(false);
        }
    }
}
