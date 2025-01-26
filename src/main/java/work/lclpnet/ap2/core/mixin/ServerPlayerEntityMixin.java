package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import work.lclpnet.ap2.core.hook.SpectatePlayerCallback;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

    @WrapOperation(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;setCameraEntity(Lnet/minecraft/entity/Entity;)V"
            )
    )
    private void ap2$onSpectate(ServerPlayerEntity instance, Entity entity, Operation<Void> original) {
        if (SpectatePlayerCallback.HOOK.invoker().onSpectate(instance, entity)) return;

        original.call(instance, entity);
    }
}
