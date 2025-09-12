package work.lclpnet.ap2.impl.game.data.type;

import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.data.SubjectRef;

import java.util.Objects;
import java.util.Optional;
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

        stack.set(DataComponentTypes.PROFILE, new ProfileComponent(Optional.empty(), Optional.of(viewer.getUuid()), new PropertyMap()));

        return stack;
    }

    public static PlayerRef create(ServerPlayerEntity player) {
        return new PlayerRef(player.getUuid(), player.getNameForScoreboard());
    }
}
