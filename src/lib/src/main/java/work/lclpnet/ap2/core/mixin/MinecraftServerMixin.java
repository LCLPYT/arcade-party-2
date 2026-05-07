package work.lclpnet.ap2.core.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Shadow
    private boolean onlineMode;

    @Inject(
            method = "getProfilePermissions",
            at = @At("RETURN"),
            cancellable = true
    )
    private void ap2$offlineModePermissionLevel(NameAndId nameAndId, CallbackInfoReturnable<Integer> cir) {
        if (onlineMode) return;

        String property = System.getProperty("ap2.offline_all_operators", "false");

        if ("true".equalsIgnoreCase(property)) {
            cir.setReturnValue(4);
        }
    }
}
