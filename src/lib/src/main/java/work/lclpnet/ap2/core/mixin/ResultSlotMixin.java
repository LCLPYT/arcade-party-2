package work.lclpnet.ap2.core.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.ItemCraftedCallback;

@Mixin(ResultSlot.class)
public class ResultSlotMixin {

    @Shadow @Final private Player player;
    @Shadow private int removeCount;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void ap2$onItemCrafted(ItemStack carried, CallbackInfo ci) {
        if (this.removeCount <= 0 || carried.isEmpty()) return;
        if (!(this.player instanceof ServerPlayer serverPlayer)) return;

        ItemCraftedCallback.HOOK.invoker().onCrafted(serverPlayer, carried, this.removeCount);
    }
}
