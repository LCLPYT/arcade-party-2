package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.ProjectileShootCallback;

@Mixin(ProjectileWeaponItem.class)
public class ProjectileWeaponItemMixin {

    @Inject(
            method = "createProjectile",
            at = @At("RETURN")
    )
    private void ap2$createArrow(Level world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack, boolean critical, CallbackInfoReturnable<Projectile> cir,
                                 @Local AbstractArrow projectile) {
        ProjectileShootCallback.HOOK.invoker().onShoot(shooter, projectile);
    }
}
