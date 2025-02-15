package work.lclpnet.ap2.impl.game.item;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

public interface SpecialItemContext {

    void removeSpecialItem(ServerPlayerEntity player, SpecialItem item);

    boolean isSpecialItem(ItemStack stack, @Nullable SpecialItem item);
}
