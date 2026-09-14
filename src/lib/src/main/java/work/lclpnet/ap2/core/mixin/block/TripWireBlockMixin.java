package work.lclpnet.ap2.core.mixin.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.TripWireTriggerCallback;

@Mixin(TripWireBlock.class)
public class TripWireBlockMixin {

    @Inject(
            method = "entityInside",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/TripWireBlock;checkPressed(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Ljava/util/List;)V"
            ),
            cancellable = true
    )
    public void ap2$onTripWireTriggered(BlockState state, Level level, BlockPos pos, Entity entity,
                                        InsideBlockEffectApplier effectApplier, boolean isPrecise, CallbackInfo ci) {
        if (TripWireTriggerCallback.HOOK.invoker().onTrigger(level, pos, entity)) {
            ci.cancel();
        }
    }
}
