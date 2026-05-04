package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.EnderPearlTeleportCallback;

@Mixin(ThrownEnderpearl.class)
public class ThrownEnderpearlMixin {

    @Inject(
            method = "onHit",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;"),
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;")
            },
            cancellable = true
    )
    public void ap2$onTeleport(HitResult hitResult, CallbackInfo ci, @Local(name = "teleportPos") Vec3 teleportPos, @Local(name = "owner") Entity owner) {
        var self = (ThrownEnderpearl) (Object) this;

        if (EnderPearlTeleportCallback.HOOK.invoker().onTeleport(owner, self, teleportPos)) {
            ci.cancel();
        }
    }
}
