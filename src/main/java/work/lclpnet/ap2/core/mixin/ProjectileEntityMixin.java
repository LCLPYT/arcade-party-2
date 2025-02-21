package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.ProjectileHitEntityCallback;
import work.lclpnet.ap2.core.hook.ProjectileShootCallback;

@Mixin(ProjectileEntity.class)
public class ProjectileEntityMixin {

    @ModifyArg(
            method = {
                    "spawnWithVelocity(Lnet/minecraft/entity/projectile/ProjectileEntity$ProjectileCreator;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/LivingEntity;FFF)Lnet/minecraft/entity/projectile/ProjectileEntity;",
                    "spawnWithVelocity(Lnet/minecraft/entity/projectile/ProjectileEntity$ProjectileCreator;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/LivingEntity;DDDFF)Lnet/minecraft/entity/projectile/ProjectileEntity;"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/projectile/ProjectileEntity;spawn(Lnet/minecraft/entity/projectile/ProjectileEntity;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ItemStack;Ljava/util/function/Consumer;)Lnet/minecraft/entity/projectile/ProjectileEntity;"
            )
    )
    private static ProjectileEntity ap2$modifyProjectile(ProjectileEntity projectile, @Local(argsOnly = true) LivingEntity shooter) {
        ProjectileShootCallback.HOOK.invoker().onShoot(shooter, projectile);
        return projectile;
    }

    @Inject(
            method = "onEntityHit",
            at = @At("TAIL")
    )
    public void ap2$onEntityHit(EntityHitResult entityHitResult, CallbackInfo ci) {
        var self = (ProjectileEntity) (Object) this;
        ProjectileHitEntityCallback.HOOK.invoker().onHitEntity(self, entityHitResult);
    }
}
