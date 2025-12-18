package work.lclpnet.ap2.core.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import work.lclpnet.ap2.core.hook.PlayerListEntriesOnJoinCallback;

import java.util.Collection;

@Mixin(PlayerList.class)
public class PlayerListMixin {

    @ModifyArg(
            method = "placeNewPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket;createPlayerInitializing(Ljava/util/Collection;)Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket;",
                    ordinal = 0
            )
    )
    public Collection<ServerPlayer> ap2$changePlayers(Collection<ServerPlayer> players) {
        return PlayerListEntriesOnJoinCallback.HOOK.invoker().shouldBeSent(players);
    }
}
