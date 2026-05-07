package work.lclpnet.ap2.core.mixin;

import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.CustomClickActionCallback;

@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerImplMixin {

    @Inject(
            method = "handleCustomClickAction",
            at = @At("TAIL")
    )
    public void ap2$onCustomClickAction(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl handler) {
            ServerPlayer player = handler.player;

            if (player == null) return;

            CustomClickActionCallback.HOOK.invoker().onCustomClickAction(player, packet.id(), packet.payload());
        }
    }
}
