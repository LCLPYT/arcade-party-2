package work.lclpnet.ap2.core.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.CobwebSlowCallback;

@Mixin(WebBlock.class)
public class WebBlockMixin {

    @Inject(
            method = "entityInside",
            at = @At("HEAD"),
            cancellable = true
    )
    public void ap2$onCobwebCollision(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise, CallbackInfo ci) {
        if (CobwebSlowCallback.HOOK.invoker().cancelSlow(entity, pos)) {
            ci.cancel();
        }
    }
}
