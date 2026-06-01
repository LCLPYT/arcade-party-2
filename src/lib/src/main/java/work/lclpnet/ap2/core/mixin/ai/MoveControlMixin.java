package work.lclpnet.ap2.core.mixin.ai;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.block.state.BlockState;
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

    @Shadow @Final protected Mob mob;

    @Shadow protected MoveControl.Operation operation;

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z",
                    ordinal = 0
            )
    )
    public boolean ap2$excludeTrapdoorsFromJumping(BlockState instance, TagKey<?> tagKey, Operation<Boolean> original) {
        if (original.call(instance, tagKey)) {
            return true;
        }

        // if enabled, prevent jumping when passing open trapdoors
        if (!((ApEntity) this.mob).ap2$isPatchTrapdoorJumping()) {
            return false;
        }

        return TrapdoorJumpPatch.preventJumping(instance);
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
            )
    )
    public void ap2$customJumpBehaviour(CallbackInfo ci) {
        if (!((ApEntity) this.mob).ap2$isPatchTrapdoorJumping()) return;

        if (TrapdoorJumpPatch.shouldJump(mob)) {
            this.mob.getJumpControl().jump();
            this.operation = MoveControl.Operation.JUMPING;
        }
    }

    @Inject(
            method = "tick",
            at = @At("RETURN")
    )
    public void ap2$afterMoveTick(CallbackInfo ci) {
        EntityAfterMoveCallback.HOOK.invoker().afterMoveTick(mob);
    }

    @ModifyArg(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/control/MoveControl;rotlerp(FFF)F"
            ),
            index = 0
    )
    private float ap2$modifyMovementYaw(float yaw) {
        var handle = (ApEntity) mob;

        if (handle.ap2$isUseMovementYaw()) {
            return handle.ap2$getMovementYaw();
        }

        return yaw;
    }
}
