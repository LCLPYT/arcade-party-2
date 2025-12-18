package work.lclpnet.ap2.impl.game.data;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import work.lclpnet.ap2.api.game.data.SubjectRef;

public record StringRef(String name) implements SubjectRef {

    @Override
    public Component getNameFor(ServerPlayer player) {
        return Component.literal(name);
    }

    @Override
    public ItemStack getIconStackFor(RegistryAccess registryManager, ServerPlayer viewer) {
        return ItemStack.EMPTY;
    }

    @Override
    public String getIdentifier() {
        return name;
    }
}
