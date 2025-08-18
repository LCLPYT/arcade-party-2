package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import work.lclpnet.ap2.core.hook.PlayerCanTrackCallback;

@Mixin(targets = "net/minecraft/server/world/ServerChunkLoadingManager$EntityTracker")
public class EntityTrackerMixin {

    @Shadow
    @Final
    Entity entity;

    @WrapOperation(
            method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerChunkLoadingManager;isTracked(Lnet/minecraft/server/network/ServerPlayerEntity;II)Z"
            )
    )
    public boolean ap2$canTrack(ServerChunkLoadingManager instance, ServerPlayerEntity player, int chunkX, int chunkZ, Operation<Boolean> original) {
        boolean shouldTrack = original.call(instance, player, chunkX, chunkZ);

        return shouldTrack && PlayerCanTrackCallback.HOOK.invoker().canTrack(player, entity);
    }
}
