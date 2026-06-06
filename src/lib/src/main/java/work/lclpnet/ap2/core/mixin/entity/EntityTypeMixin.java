package work.lclpnet.ap2.core.mixin.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.EntitySpawnedByCallback;

@Mixin(EntityType.class)
public class EntityTypeMixin {

    @Inject(
            method = "spawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/EntitySpawnReason;ZZ)Lnet/minecraft/world/entity/Entity;",
            at = @At("RETURN")
    )
    public <T extends Entity> void ap2$onEntitySpawned(ServerLevel level, @Nullable ItemStack itemStack, @Nullable LivingEntity user, BlockPos spawnPos, EntitySpawnReason spawnReason, boolean tryMoveDown, boolean movedUp, CallbackInfoReturnable<T> cir) {
        T entity = cir.getReturnValue();

        if (entity != null) {
            EntitySpawnedByCallback.HOOK.invoker().onSpawned(level, entity, itemStack, user, spawnReason);
        }
    }
}
