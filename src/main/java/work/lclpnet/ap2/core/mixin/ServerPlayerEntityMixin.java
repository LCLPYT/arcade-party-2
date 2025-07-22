package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.PlayerDeathMessageCallback;
import work.lclpnet.ap2.core.hook.SpectatePlayerCallback;
import work.lclpnet.ap2.core.type.ApServerPlayerEntity;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements ApServerPlayerEntity {

    @Unique @Nullable
    private Text playerListName = null;

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

    @Override
    public void ap2$setPlayerListName(@Nullable Text name) {
        this.playerListName = name;
    }

    @Inject(
            method = "getPlayerListName",
            at = @At("RETURN"),
            cancellable = true
    )
    public void ap2$modifyPlayerListName(CallbackInfoReturnable<Text> cir) {
        if (cir.getReturnValue() == null) {
            cir.setReturnValue(playerListName);
        }
    }

    @ModifyArg(
            method = "onDeath",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/packet/s2c/play/DeathMessageS2CPacket;<init>(ILnet/minecraft/text/Text;)V"
            )
    )
    private Text ap2$modifyDeathMessage(Text msg, @Local(argsOnly = true) DamageSource source) {
        var self = (ServerPlayerEntity) (Object) this;
        return PlayerDeathMessageCallback.HOOK.invoker().modifyDeathMessage(self, source, msg);
    }
}
