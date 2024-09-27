package work.lclpnet.ap2.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Invoker
    boolean invokeTryUseTotem(DamageSource source);

    @Invoker
    void invokeDropInventory();

    @Invoker
    void invokeDropXp(@Nullable Entity attacker);
}
