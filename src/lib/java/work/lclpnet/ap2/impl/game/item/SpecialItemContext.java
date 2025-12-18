package work.lclpnet.ap2.impl.game.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;

public interface SpecialItemContext {

    void removeSpecialItem(ServerPlayer player, SpecialItem item);

    boolean isSpecialItem(ItemStack stack, @Nullable SpecialItem item);

    boolean hasSpecialItem(ServerPlayer player, @Nullable SpecialItem item);

    TaskScheduler scheduler();

    Translations translations();
}
