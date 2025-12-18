package work.lclpnet.ap2.impl.game.kit;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface Kit {

    String id();

    ItemStack createItemStack(RegistryAccess manager);

    default void init(KitOptions options) {}

    default void equip(ServerPlayer player, KitOptions options) {}

    default void unequip(ServerPlayer player, KitOptions options) {}
}
