package work.lclpnet.ap2.core.mixin.item;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.CanShootProjectileCallback;

@Mixin(TridentItem.class)
public class TridentItemMixin {

    @Inject(
            method = "releaseUsing",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/stats/Stat;)V"
            ),
            cancellable = true
    )
    public void ap2$canShoot(ItemStack itemStack, Level level, LivingEntity entity, int remainingTime, CallbackInfoReturnable<Boolean> cir,
                             @Local(name = "riptideStrength") float riptideStrength) {

        if (riptideStrength != 0.0f) return;

        if (CanShootProjectileCallback.HOOK.invoker().canShoot(entity, itemStack, entity.getUsedItemHand())) return;

        cir.setReturnValue(false);
    }
}
