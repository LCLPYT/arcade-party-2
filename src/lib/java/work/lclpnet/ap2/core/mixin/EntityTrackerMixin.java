package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import work.lclpnet.ap2.core.hook.PlayerCanTrackCallback;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class EntityTrackerMixin {

    @Shadow
    @Final
    Entity entity;

    @WrapOperation(
            method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"
            )
    )
    public boolean ap2$canTrack(ChunkMap instance, ServerPlayer player, int chunkX, int chunkZ, Operation<Boolean> original) {
        boolean shouldTrack = original.call(instance, player, chunkX, chunkZ);

        return shouldTrack && PlayerCanTrackCallback.HOOK.invoker().canTrack(player, entity);
    }
}
