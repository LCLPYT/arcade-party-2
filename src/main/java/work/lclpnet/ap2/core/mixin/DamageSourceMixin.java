package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import work.lclpnet.ap2.core.hook.DeathMessageItemCallback;

@Mixin(DamageSource.class)
public class DamageSourceMixin {

    @ModifyVariable(
            method = "getDeathMessage",
            at = @At(
                    value = "LOAD",
                    ordinal = 0
            )
    )
    private ItemStack ap2$modifyWeaponStack(ItemStack stack, @Local(argsOnly = true) LivingEntity killed) {
        var self = (DamageSource) (Object) this;

        return DeathMessageItemCallback.HOOK.invoker().modifyItem(self, killed, stack);
    }
}
