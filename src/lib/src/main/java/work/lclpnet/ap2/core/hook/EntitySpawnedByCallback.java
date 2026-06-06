package work.lclpnet.ap2.core.hook;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface EntitySpawnedByCallback {

    Hook<EntitySpawnedByCallback> HOOK = HookFactory.createArrayBacked(EntitySpawnedByCallback.class, hooks -> (level, entity, stack, user, reason) -> {
        for (var hook : hooks) {
            hook.onSpawned(level, entity, stack, user, reason);
        }
    });

    void onSpawned(ServerLevel level, Entity entity, @Nullable ItemStack stack, @Nullable LivingEntity user, EntitySpawnReason reason);
}
