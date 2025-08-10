package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.registry.tag.BlockTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import work.lclpnet.ap2.core.type.ApMobNavigation;

@Mixin(MobNavigation.class)
public class MobNavigationMixin implements ApMobNavigation {

    @Unique
    private boolean patchTrapdoorPathFindingTarget = false;

    @Override
    public void ap2$patchTrapdoorPathFindingTarget() {
        this.patchTrapdoorPathFindingTarget = true;
    }

    @WrapOperation(
            method = "findPathTo(Lnet/minecraft/util/math/BlockPos;I)Lnet/minecraft/entity/ai/pathing/Path;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/BlockState;isSolid()Z"
            )
    )
    private boolean ap2$modifyTrapdoorSolidCondition(BlockState instance, Operation<Boolean> original) {
        if (patchTrapdoorPathFindingTarget && instance.isIn(BlockTags.TRAPDOORS)) {
            return false;
        }

        return original.call(instance);
    }

    @WrapOperation(
            method = "findPathTo(Lnet/minecraft/util/math/BlockPos;I)Lnet/minecraft/entity/ai/pathing/Path;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/BlockState;isAir()Z"
            )
    )
    private boolean ap2$modifyTrapdoorAirCondition(BlockState instance, Operation<Boolean> original) {
        if (patchTrapdoorPathFindingTarget && instance.isIn(BlockTags.TRAPDOORS)) {
            return true;
        }

        return original.call(instance);
    }
}
