package work.lclpnet.ap2.impl.game.item;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

public interface SpecialItemContext {

    void removeSpecialItem(ServerPlayerEntity player, SpecialItem item);

    boolean isSpecialItem(ItemStack stack, @Nullable SpecialItem item);

    boolean hasSpecialItem(ServerPlayerEntity player, @Nullable SpecialItem item);

    TaskScheduler scheduler();
}
