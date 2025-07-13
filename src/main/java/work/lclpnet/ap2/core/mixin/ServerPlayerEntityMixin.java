package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
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

        var self = (ServerPlayerEntity) (Object) this;
        MinecraftServer server = self.getServer();

        if (server == null) return;

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
}
