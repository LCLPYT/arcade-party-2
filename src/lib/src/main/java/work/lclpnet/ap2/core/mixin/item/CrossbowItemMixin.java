package work.lclpnet.ap2.core.mixin.item;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.CanShootProjectileCallback;

@Mixin(CrossbowItem.class)
public class CrossbowItemMixin {

    @Inject(
            method = "performShooting",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/CrossbowItem;shoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;Ljava/util/List;FFZLnet/minecraft/world/entity/LivingEntity;)V"
            ),
            cancellable = true
    )
    public void ap2$canShoot(Level level, LivingEntity shooter, InteractionHand hand, ItemStack weapon, float power, float uncertainty, LivingEntity targetOverride, CallbackInfo ci,
                             @Local(name = "charged") ChargedProjectiles charged) {

        if (CanShootProjectileCallback.HOOK.invoker().canShoot(shooter, weapon, hand)) return;

        weapon.set(DataComponents.CHARGED_PROJECTILES, charged);

        ci.cancel();
    }
}
