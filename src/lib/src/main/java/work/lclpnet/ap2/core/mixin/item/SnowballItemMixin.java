package work.lclpnet.ap2.core.mixin.item;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.CanShootProjectileCallback;

@Mixin(SnowballItem.class)
public class SnowballItemMixin {

    @Inject(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileFromRotation(Lnet/minecraft/world/entity/projectile/Projectile$ProjectileFactory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;FFF)Lnet/minecraft/world/entity/projectile/Projectile;"
            ),
            cancellable = true
    )
    public void ap2$canShoot(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir,
                             @Local(name = "itemStack") ItemStack itemStack) {

        if (CanShootProjectileCallback.HOOK.invoker().canShoot(player, itemStack, hand)) return;

        cir.setReturnValue(InteractionResult.PASS);
    }
}
