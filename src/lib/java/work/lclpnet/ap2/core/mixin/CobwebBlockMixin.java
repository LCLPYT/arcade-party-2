package work.lclpnet.ap2.core.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.CobwebSlowCallback;

@Mixin(CobwebBlock.class)
public class CobwebBlockMixin {

    @Inject(
            method = "onEntityCollision",
            at = @At("HEAD"),
            cancellable = true
    )
    public void ap2$onCobwebCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler, CallbackInfo ci) {
        if (CobwebSlowCallback.HOOK.invoker().cancelSlow(entity, pos)) {
            ci.cancel();
        }
    }
}
