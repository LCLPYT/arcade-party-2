package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.PlayerDisplayNameCallback;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    @Inject(
            method = "getDisplayName",
            at = @At("RETURN"),
            cancellable = true
    )
    public void ap2$modifyDisplayName(CallbackInfoReturnable<Text> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        Text text = cir.getReturnValue();

        text = PlayerDisplayNameCallback.HOOK.invoker().modifyDisplayName(self, text);

        cir.setReturnValue(text);
    }
}
