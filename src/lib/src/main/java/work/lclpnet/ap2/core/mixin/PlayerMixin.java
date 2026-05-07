package work.lclpnet.ap2.core.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.PlayerDisplayNameCallback;

@Mixin(Player.class)
public class PlayerMixin {

    @Inject(
            method = "getDisplayName",
            at = @At("RETURN"),
            cancellable = true
    )
    public void ap2$modifyDisplayName(CallbackInfoReturnable<Component> cir) {
        Player self = (Player) (Object) this;
        Component text = cir.getReturnValue();

        text = PlayerDisplayNameCallback.HOOK.invoker().modifyDisplayName(self, text);

        cir.setReturnValue(text);
    }
}
