package work.lclpnet.ap2.game.dragon_escape.kit;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;

public interface Kit {

    String id();

    ItemStack createItemStack(DynamicRegistryManager manager);

    default void init() {}

    default void equip(ServerPlayerEntity player) {}

    default void unequip(ServerPlayerEntity player) {}
}
