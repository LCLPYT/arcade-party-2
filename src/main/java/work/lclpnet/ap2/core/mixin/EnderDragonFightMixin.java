package work.lclpnet.ap2.mixin;

import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.type.ApDragonFight;

@Mixin(EnderDragonFight.class)
public class EnderDragonFightMixin implements ApDragonFight {

    @Unique
    private boolean temporary = false;

    @Override
    public void ap2$setTemporary() {
        temporary = true;
    }

    @Inject(
            method = "generateEndPortal",
            at = @At("HEAD"),
            cancellable = true
    )
    public void ap2$generateEndPortal(boolean previouslyKilled, CallbackInfo ci) {
        if (temporary) {
            ci.cancel();
        }
    }

    @Inject(
            method = "generateEndGateway",
            at = @At("HEAD"),
            cancellable = true
    )
    public void ap2$generateEndGateway(BlockPos pos, CallbackInfo ci) {
        if (temporary) {
            ci.cancel();
        }
    }
}
