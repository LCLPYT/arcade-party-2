package work.lclpnet.ap2.impl.game.data.type;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.game.data.SubjectRef;

import java.util.Objects;
import java.util.UUID;

public record PlayerRef(UUID uuid, String name) implements SubjectRef {

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
    public Text getNameFor(ServerPlayerEntity viewer) {
        return Text.literal(name);
    }

    @Override
    public ItemStack getIconStackFor(DynamicRegistryManager registryManager, ServerPlayerEntity viewer) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);

        stack.set(DataComponentTypes.PROFILE, ProfileComponent.ofDynamic(uuid));

        return stack;
    }

    @Override
    public String getIdentifier() {
        return uuid.toString();
    }

    public static PlayerRef create(ServerPlayerEntity player) {
        return new PlayerRef(player.getUuid(), player.getNameForScoreboard());
    }

    public static @NotNull PlayerRef createForUuid(@NotNull UUID uuid) {
        return new PlayerRef(uuid, "?");
    }
}
