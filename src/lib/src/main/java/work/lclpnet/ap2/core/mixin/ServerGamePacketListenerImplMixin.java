package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import work.lclpnet.ap2.core.hook.SpectatePlayerCallback;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @WrapOperation(
            method = "handleSpectateEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;setCamera(Lnet/minecraft/world/entity/Entity;)V"
            )
    )
    private void ap2$onSpectate(ServerPlayer instance, Entity newCamera, Operation<Void> original) {
        if (SpectatePlayerCallback.HOOK.invoker().onSpectate(instance, newCamera)) return;

        original.call(instance, newCamera);
    }
}
