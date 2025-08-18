package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.EntityAfterMoveCallback;
import work.lclpnet.ap2.core.patch.TrapdoorJumpPatch;
import work.lclpnet.ap2.core.type.ApEntity;

@Mixin(MoveControl.class)
public class MoveControlMixin {

    @Shadow @Final protected MobEntity entity;

    @Shadow protected MoveControl.State state;

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/BlockState;isIn(Lnet/minecraft/registry/tag/TagKey;)Z",
                    ordinal = 0
            )
    )
    public boolean ap2$excludeTrapdoorsFromJumping(BlockState instance, TagKey<?> tagKey, Operation<Boolean> original) {
        if (original.call(instance, tagKey)) {
            return true;
        }

        // if enabled, prevent jumping when passing open trapdoors
        if (!((ApEntity) this.entity).ap2$isPatchTrapdoorJumping()) {
            return false;
        }

        return TrapdoorJumpPatch.preventJumping(instance);
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/BlockState;getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;"
            )
    )
    public void ap2$customJumpBehaviour(CallbackInfo ci) {
        if (!((ApEntity) this.entity).ap2$isPatchTrapdoorJumping()) return;

        if (TrapdoorJumpPatch.shouldJump(entity)) {
            this.entity.getJumpControl().setActive();
            this.state = MoveControl.State.JUMPING;
        }
    }

    @Inject(
            method = "tick",
            at = @At("RETURN")
    )
    public void ap2$afterMoveTick(CallbackInfo ci) {
        EntityAfterMoveCallback.HOOK.invoker().afterMoveTick(entity);
    }

    @ModifyArg(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/control/MoveControl;wrapDegrees(FFF)F"
            ),
            index = 0
    )
    private float ap2$modifyMovementYaw(float yaw) {
        var handle = (ApEntity) entity;

        if (handle.ap2$isUseMovementYaw()) {
            return handle.ap2$getMovementYaw();
        }

        return yaw;
    }
}
