package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.FrozenTickChangeCallback;
import work.lclpnet.ap2.core.type.ApEntity;

@Mixin(Entity.class)
public class EntityMixin implements ApEntity {

    @Unique private boolean patchNarrowMovement = false;
    @Unique private boolean patchTrapdoorJumping = false;
    @Unique private boolean useMovementYaw = false;
    @Unique private float movementYaw = 0f;

    @Override
    public void ap2$patchNarrowMovement() {
        this.patchNarrowMovement = true;
    }

    @Override
    public boolean ap2$isPatchNarrowMovement() {
        return patchNarrowMovement;
    }

    @Override
    public void ap2$patchTrapdoorJumping() {
        this.patchTrapdoorJumping = true;
    }

    @Override
    public boolean ap2$isPatchTrapdoorJumping() {
        return patchTrapdoorJumping;
    }

    @Override
    public void ap2$setUseMovementYaw(boolean useMovementYaw) {
        this.useMovementYaw = useMovementYaw;
    }

    @Override
    public boolean ap2$isUseMovementYaw() {
        return useMovementYaw;
    }

    @Override
    public void ap2$setMovementYaw(float yaw) {
        movementYaw = yaw;
    }

    @Override
    public float ap2$getMovementYaw() {
        return movementYaw;
    }

    @ModifyArg(
            method = "updateVelocity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;movementInputToVelocity(Lnet/minecraft/util/math/Vec3d;FF)Lnet/minecraft/util/math/Vec3d;"
            ),
            index = 2
    )
    private float ap2$modifyMovementYaw(float yaw) {
        return useMovementYaw ? movementYaw : yaw;
    }

    @Inject(
            method = "setFrozenTicks",
            at = @At("HEAD"),
            cancellable = true
    )
    public void ap2$setFrozenTicks(int frozenTicks, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        if (FrozenTickChangeCallback.HOOK.invoker().onFrozenTicksChange(self, frozenTicks)) {
            ci.cancel();
        }
    }
}
