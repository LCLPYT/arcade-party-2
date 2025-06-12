package work.lclpnet.ap2.core.mixin;

import net.minecraft.block.BigDripleafBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.DripLeafTiltCallback;

@Mixin(BigDripleafBlock.class)
public abstract class BigDripLeafBlockMixin {

    @Inject(
            method = "onEntityCollision",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/BigDripleafBlock;changeTilt(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/enums/Tilt;Lnet/minecraft/sound/SoundEvent;)V"
            ),
            cancellable = true
    )
    private void ap2$onDripLeafCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler, CallbackInfo ci) {
        if (DripLeafTiltCallback.HOOK.invoker().onTilt(entity, pos)) {
            ci.cancel();
        }
    }
}
