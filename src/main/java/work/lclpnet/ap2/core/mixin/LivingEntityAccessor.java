package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Invoker
    boolean invokeTryUseDeathProtector(DamageSource source);

    @Invoker
    void invokeDropInventory(ServerWorld world);

    @Invoker
    void invokeDropExperience(ServerWorld world, @Nullable Entity attacker);
}
