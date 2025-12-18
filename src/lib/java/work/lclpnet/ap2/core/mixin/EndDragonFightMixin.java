package work.lclpnet.ap2.core.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.type.ApDragonFight;

@Mixin(EndDragonFight.class)
public class EndDragonFightMixin implements ApDragonFight {

    @Unique
    private boolean temporary = false;

    @Override
    public void ap2$setTemporary() {
        temporary = true;
    }

    @Inject(
            method = "spawnExitPortal",
            at = @At("HEAD"),
            cancellable = true
    )
    public void ap2$generateEndPortal(boolean previouslyKilled, CallbackInfo ci) {
        if (temporary) {
            ci.cancel();
        }
    }

    @Inject(
            method = "spawnNewGateway(Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    public void ap2$generateEndGateway(BlockPos pos, CallbackInfo ci) {
        if (temporary) {
            ci.cancel();
        }
    }
}
