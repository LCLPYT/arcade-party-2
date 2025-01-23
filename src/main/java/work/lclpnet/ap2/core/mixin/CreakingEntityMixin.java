package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.mob.CreakingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import work.lclpnet.ap2.core.hook.BrainCreationCallback;

@Mixin(CreakingEntity.class)
public class CreakingEntityMixin {

    @WrapOperation(
            method = "deserializeBrain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/mob/CreakingBrain;create(Lnet/minecraft/entity/ai/brain/Brain;)Lnet/minecraft/entity/ai/brain/Brain;"
            )
    )
    private Brain<CreakingEntity> ap2$createBrain(Brain<CreakingEntity> brain, Operation<Brain<CreakingEntity>> original) {
        var self = (CreakingEntity) (Object) this;
        var override = BrainCreationCallback.Creaking.HOOK.invoker().createBrain(self, () -> brain);

        return override != null ? brain : original.call(brain);

    }
}
