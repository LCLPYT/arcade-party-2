package work.lclpnet.ap2.core.mixin;

import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.BrainCreationCallback;

@Mixin(Warden.class)
public abstract class WardenAiMixin {

    @Shadow @Final private static Brain.Provider<Warden> BRAIN_PROVIDER;

    @Inject(
            method = "makeBrain",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ap2$overrideCreate(Brain.Packed packedBrain, CallbackInfoReturnable<Brain<Warden>> cir) {
        var warden = (Warden) (Object) this;
        var override = BrainCreationCallback.Warden.HOOK.invoker().createBrain(warden, () ->
                BRAIN_PROVIDER.makeBrain(warden, packedBrain));

        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
