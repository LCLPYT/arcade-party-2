package work.lclpnet.ap2.core.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.EntityPushEntityCallback;
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
            method = "moveRelative",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getInputVector(Lnet/minecraft/world/phys/Vec3;FF)Lnet/minecraft/world/phys/Vec3;"
            ),
            index = 2
    )
    private float ap2$modifyMovementYaw(float yaw) {
        return useMovementYaw ? movementYaw : yaw;
    }

    @Inject(
            method = "setTicksFrozen",
            at = @At("HEAD"),
            cancellable = true
    )
    public void ap2$setFrozenTicks(int ticks, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        if (FrozenTickChangeCallback.HOOK.invoker().onFrozenTicksChange(self, ticks)) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "push(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"
            )
    )
    public void ap2$allowPush(Entity instance, double xa, double ya, double za, Operation<Void> original, @Local(argsOnly = true, name = "entity") Entity entity) {
        var self = (Entity) (Object) this;
        Entity pusher = instance == entity ? self : entity;

        if ((EntityPushEntityCallback.HOOK.invoker().onPush(instance, pusher))) {
            original.call(instance, xa, ya, za);
        }
    }
}
