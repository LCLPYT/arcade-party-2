package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import work.lclpnet.ap2.core.type.ApMobNavigation;

@Mixin(GroundPathNavigation.class)
public class GroundPathNavigationMixin implements ApMobNavigation {

    @Unique
    private boolean patchTrapdoorPathFindingTarget = false;

    @Override
    public void ap2$patchTrapdoorPathFindingTarget() {
        this.patchTrapdoorPathFindingTarget = true;
    }

    @WrapOperation(
            method = "findSurfacePosition",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;isSolid()Z"
            )
    )
    private boolean ap2$modifyTrapdoorSolidCondition(BlockState instance, Operation<Boolean> original) {
        if (patchTrapdoorPathFindingTarget && instance.is(BlockTags.TRAPDOORS)) {
            return false;
        }

        return original.call(instance);
    }

    @WrapOperation(
            method = "findSurfacePosition",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z"
            )
    )
    private boolean ap2$modifyTrapdoorAirCondition(BlockState instance, Operation<Boolean> original) {
        if (patchTrapdoorPathFindingTarget && instance.is(BlockTags.TRAPDOORS)) {
            return true;
        }

        return original.call(instance);
    }
}
