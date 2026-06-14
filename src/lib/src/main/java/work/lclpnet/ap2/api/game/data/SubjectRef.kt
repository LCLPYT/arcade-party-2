package work.lclpnet.ap2.api.game.data;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface SubjectRef {

    /**
     * Translate the name of this subject for a given viewer (a player).
     * @param viewer The viewer (to whom the name is translated for).
     * @return The translated text.
     */
    Component getNameFor(ServerPlayer viewer);

    /**
     * Gets an item stack as icon for the subject.
     * @param registryManager The {@link RegistryAccess}.
     * @param viewer The viewer.
     * @return The icon item stack.
     */
    ItemStack getIconStackFor(RegistryAccess registryManager, ServerPlayer viewer);

    String getIdentifier();
}
