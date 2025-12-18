package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.monster.creaking.Creaking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.BrainCreationCallback;
import work.lclpnet.ap2.core.hook.CreakingLookedAtCheckCallback;

@Mixin(Creaking.class)
public class CreakingMixin {

    @WrapOperation(
            method = "makeBrain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/creaking/CreakingAi;makeBrain(Lnet/minecraft/world/entity/monster/creaking/Creaking;Lnet/minecraft/world/entity/ai/Brain;)Lnet/minecraft/world/entity/ai/Brain;"
            )
    )
    private Brain<Creaking> ap2$createBrain(Creaking creaking, Brain<Creaking> brain, Operation<Brain<Creaking>> original) {
        var self = (Creaking) (Object) this;
        var override = BrainCreationCallback.Creaking.HOOK.invoker().createBrain(self, () -> brain);

        return override != null ? override : original.call(creaking, brain);
    }

    @Inject(
            method = "checkCanMove",
            at = @At("HEAD"),
            cancellable = true
    )
    public void ap2$shouldBeUnrooted(CallbackInfoReturnable<Boolean> cir) {
        var self = (Creaking) (Object) this;

        var res = CreakingLookedAtCheckCallback.HOOK.invoker().isBeingLookedAt(self);

        if (res.isPass()) return;

        cir.setReturnValue(!res.get().orElse(false));
    }
}
