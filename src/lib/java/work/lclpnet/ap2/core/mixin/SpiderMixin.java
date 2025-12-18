package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.world.entity.monster.Spider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import work.lclpnet.ap2.core.type.ApSpider;

@Mixin(Spider.class)
public class SpiderMixin implements ApSpider {

    @Unique private boolean canClimb = true;

    @Override
    public void ap2$setCanClimb(boolean canClimb) {
        this.canClimb = canClimb;
    }

    @WrapWithCondition(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/Spider;setClimbing(Z)V"
            )
    )
    private boolean ap2$modifyClimbCondition(Spider instance, boolean climbing) {
        return canClimb;
    }
}
