package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.mob.CreakingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.BrainCreationCallback;
import work.lclpnet.ap2.core.hook.CreakingLookedAtCheckCallback;

@Mixin(CreakingEntity.class)
public class CreakingEntityMixin {

    @WrapOperation(
            method = "deserializeBrain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/mob/CreakingBrain;create(Lnet/minecraft/entity/mob/CreakingEntity;Lnet/minecraft/entity/ai/brain/Brain;)Lnet/minecraft/entity/ai/brain/Brain;"
            )
    )
    private Brain<CreakingEntity> ap2$createBrain(CreakingEntity creaking, Brain<CreakingEntity> brain, Operation<Brain<CreakingEntity>> original) {
        var self = (CreakingEntity) (Object) this;
        var override = BrainCreationCallback.Creaking.HOOK.invoker().createBrain(self, () -> brain);

        return override != null ? override : original.call(creaking, brain);
    }

    @Inject(
            method = "shouldBeUnrooted",
            at = @At("HEAD"),
            cancellable = true
    )
    public void ap2$shouldBeUnrooted(CallbackInfoReturnable<Boolean> cir) {
        var self = (CreakingEntity) (Object) this;

        var res = CreakingLookedAtCheckCallback.HOOK.invoker().isBeingLookedAt(self);

        if (res.isPass()) return;

        cir.setReturnValue(!res.get().orElse(false));
    }
}
