package work.lclpnet.ap2.impl.game.data.type;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.game.data.SubjectRef;

import java.util.Objects;
import java.util.UUID;

public record PlayerRef(@NotNull UUID uuid, @NotNull String name) implements SubjectRef {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerRef playerRef = (PlayerRef) o;
        return Objects.equals(uuid, playerRef.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public Component getNameFor(ServerPlayer viewer) {
        return Component.literal(name);
    }

    @Override
    public ItemStack getIconStackFor(RegistryAccess registryManager, ServerPlayer viewer) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);

        stack.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(uuid));

        return stack;
    }

    @Override
    public String getIdentifier() {
        return uuid.toString();
    }

    public static PlayerRef create(ServerPlayer player) {
        return new PlayerRef(player.getUUID(), player.getScoreboardName());
    }

    public static @NotNull PlayerRef createForUuid(@NotNull UUID uuid) {
        return new PlayerRef(uuid, "?");
    }
}
