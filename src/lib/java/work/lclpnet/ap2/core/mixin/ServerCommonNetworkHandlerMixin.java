package work.lclpnet.ap2.core.mixin;

import net.minecraft.network.packet.c2s.common.CustomClickActionC2SPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.CustomClickActionCallback;

@Mixin(ServerCommonNetworkHandler.class)
public class ServerCommonNetworkHandlerMixin {

    @Inject(
            method = "onCustomClickAction",
            at = @At("TAIL")
    )
    public void ap2$onCustomClickAction(CustomClickActionC2SPacket packet, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayNetworkHandler handler) {
            ServerPlayerEntity player = handler.player;

            if (player == null) return;

            CustomClickActionCallback.HOOK.invoker().onCustomClickAction(player, packet.id(), packet.payload());
        }
    }
}
