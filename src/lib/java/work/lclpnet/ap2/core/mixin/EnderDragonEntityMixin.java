package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.Phase;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import work.lclpnet.ap2.core.type.ApEnderDragon;

@Mixin(EnderDragonEntity.class)
public class EnderDragonEntityMixin implements ApEnderDragon {

    @Unique
    private boolean manuallyManaged = false;

    @Override
    public void ap2$setManuallyManaged() {
        manuallyManaged = true;
    }

    @WrapOperation(
            method = "tickMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/boss/dragon/phase/Phase;getPathTarget()Lnet/minecraft/util/math/Vec3d;"
            )
    )
    private Vec3d ap2$overridePhasePathTarget(Phase instance, Operation<Vec3d> original) {
        if (manuallyManaged) return null;

        return original.call(instance);
    }
}
