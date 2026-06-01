package work.lclpnet.ap2.core.mixin.entity;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.PlayerDeathMessageCallback;
import work.lclpnet.ap2.core.type.ApServerPlayerEntity;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin implements ApServerPlayerEntity {

    @Unique @Nullable
    private Component playerListName = null;

    @Override
    public void ap2$setPlayerListName(@Nullable Component name) {
        this.playerListName = name;
    }

    @Inject(
            method = "getTabListDisplayName",
            at = @At("RETURN"),
            cancellable = true
    )
    public void ap2$modifyPlayerListName(CallbackInfoReturnable<Component> cir) {
        if (cir.getReturnValue() == null) {
            cir.setReturnValue(playerListName);
        }
    }

    @ModifyArg(
            method = "die",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundPlayerCombatKillPacket;<init>(ILnet/minecraft/network/chat/Component;)V"
            )
    )
    private Component ap2$modifyDeathMessage(Component msg, @Local(argsOnly = true, name = "source") DamageSource source) {
        var self = (ServerPlayer) (Object) this;
        return PlayerDeathMessageCallback.HOOK.invoker().modifyDeathMessage(self, source, msg);
    }
}
