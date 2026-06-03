package work.lclpnet.ap2.core.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import work.lclpnet.ap2.core.type.ApEnderDragon;

@Mixin(EnderDragon.class)
public class EnderDragonMixin implements ApEnderDragon {

    @Unique
    private boolean manuallyManaged = false;

    @Override
    public void ap2$setManuallyManaged() {
        manuallyManaged = true;
    }

    @WrapOperation(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonPhaseInstance;getFlyTargetLocation()Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 ap2$overridePhasePathTarget(DragonPhaseInstance instance, Operation<Vec3> original) {
        if (manuallyManaged) return null;

        return original.call(instance);
    }
}
