package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface DeathMessageItemCallback {

    Hook<DeathMessageItemCallback> HOOK = HookFactory.createArrayBacked(DeathMessageItemCallback.class, hooks -> (source, killed, stack) -> {
        for (var hook : hooks) {
            stack = hook.modifyItem(source, killed, stack);
        }

        return stack;
    });

    @NotNull
    ItemStack modifyItem(DamageSource source, LivingEntity killed, @NotNull ItemStack stack);
}
