package work.lclpnet.ap2.core.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import work.lclpnet.ap2.core.hook.PlayerListEntriesOnJoinCallback;

import java.util.Collection;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @ModifyArg(
            method = "onPlayerConnect",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/packet/s2c/play/PlayerListS2CPacket;entryFromPlayer(Ljava/util/Collection;)Lnet/minecraft/network/packet/s2c/play/PlayerListS2CPacket;",
                    ordinal = 0
            )
    )
    public Collection<ServerPlayerEntity> ap2$changePlayers(Collection<ServerPlayerEntity> players) {
        return PlayerListEntriesOnJoinCallback.HOOK.invoker().shouldBeSent(players);
    }
}
