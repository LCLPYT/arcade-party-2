package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.ProjectileShootCallback;

@Mixin(RangedWeaponItem.class)
public class RangedWeaponItemMixin {

    @Inject(
            method = "createArrowEntity",
            at = @At("RETURN")
    )
    private void ap2$createArrow(World world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack, boolean critical, CallbackInfoReturnable<ProjectileEntity> cir,
                                 @Local PersistentProjectileEntity projectile) {
        ProjectileShootCallback.HOOK.invoker().onShoot(shooter, projectile);
    }
}
