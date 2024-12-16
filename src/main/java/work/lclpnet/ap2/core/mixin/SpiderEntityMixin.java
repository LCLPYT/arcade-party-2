package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.entity.mob.SpiderEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import work.lclpnet.ap2.core.type.ApSpider;

@Mixin(SpiderEntity.class)
public class SpiderEntityMixin implements ApSpider {

    @Unique private boolean canClimb = true;

    @Override
    public void ap2$setCanClimb(boolean canClimb) {
        this.canClimb = canClimb;
    }

    @WrapWithCondition(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/mob/SpiderEntity;setClimbingWall(Z)V"
            )
    )
    private boolean ap2$modifyClimbCondition(SpiderEntity instance, boolean climbing) {
        return canClimb;
    }
}
