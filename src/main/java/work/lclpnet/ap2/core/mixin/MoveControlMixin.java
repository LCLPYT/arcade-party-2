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
import work.lclpnet.ap2.core.patch.TrapdoorJumpPatch;
import work.lclpnet.ap2.core.type.ApEntity;

@Mixin(MoveControl.class)
public class MoveControlMixin {

    @Shadow @Final protected MobEntity entity;

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
}
