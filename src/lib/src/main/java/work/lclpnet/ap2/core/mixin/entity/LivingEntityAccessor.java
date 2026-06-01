package work.lclpnet.ap2.core.mixin.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Invoker
    boolean invokeCheckTotemDeathProtection(DamageSource source);

    @Invoker
    void invokeDropEquipment(ServerLevel world);

    @Invoker
    void invokeDropExperience(ServerLevel world, @Nullable Entity attacker);
}
