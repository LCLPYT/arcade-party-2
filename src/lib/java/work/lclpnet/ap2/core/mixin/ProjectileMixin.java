package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.ProjectileHitEntityCallback;
import work.lclpnet.ap2.core.hook.ProjectileShootCallback;

@Mixin(Projectile.class)
public class ProjectileMixin {

    @ModifyArg(
            method = {
                    "spawnProjectileFromRotation(Lnet/minecraft/world/entity/projectile/Projectile$ProjectileFactory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;FFF)Lnet/minecraft/world/entity/projectile/Projectile;",
                    "spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile$ProjectileFactory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectile(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Ljava/util/function/Consumer;)Lnet/minecraft/world/entity/projectile/Projectile;"
            )
    )
    private static Projectile ap2$modifyProjectile(Projectile projectile, @Local(argsOnly = true) LivingEntity shooter) {
        ProjectileShootCallback.HOOK.invoker().onShoot(shooter, projectile);
        return projectile;
    }

    @Inject(
            method = "onHitEntity",
            at = @At("TAIL")
    )
    public void ap2$onEntityHit(EntityHitResult entityHitResult, CallbackInfo ci) {
        var self = (Projectile) (Object) this;
        ProjectileHitEntityCallback.HOOK.invoker().onHitEntity(self, entityHitResult);
    }
}
