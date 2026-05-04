package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import work.lclpnet.ap2.core.hook.DeathMessageItemCallback;

@Mixin(DamageSource.class)
public class DamageSourceMixin {

    @ModifyVariable(
            method = "getLocalizedDeathMessage",
            at = @At(
                    value = "LOAD",
                    ordinal = 0
            ),
            name = "held"
    )
    private ItemStack ap2$modifyWeaponStack(ItemStack held, @Local(argsOnly = true, name = "victim") LivingEntity victim) {
        var self = (DamageSource) (Object) this;

        return DeathMessageItemCallback.HOOK.invoker().modifyItem(self, victim, held);
    }
}
