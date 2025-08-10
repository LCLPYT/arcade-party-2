package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.EnderPearlTeleportCallback;

@Mixin(EnderPearlEntity.class)
public class EnderPearlEntityMixin {

    @Inject(
            method = "onCollision",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/server/network/ServerPlayerEntity;"),
                    @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/entity/Entity;")
            },
            cancellable = true
    )
    public void ap2$onTeleport(HitResult hitResult, CallbackInfo ci, @Local Vec3d pos, @Local Entity owner) {
        var self = (EnderPearlEntity) (Object) this;

        if (EnderPearlTeleportCallback.HOOK.invoker().onTeleport(owner, self, pos)) {
            ci.cancel();
        }
    }
}
