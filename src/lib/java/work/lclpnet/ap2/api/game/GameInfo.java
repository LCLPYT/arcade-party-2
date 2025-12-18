package work.lclpnet.ap2.api.game;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public interface GameInfo {

    /**
     * @return A unique {@link ResourceLocation} for the game.
     */
    @NotNull ResourceLocation getId();

    /**
     * @return The type of the game.
     */
    @NotNull GameType getType();

    @NotNull String getAuthor();

    @NotNull ItemStack getIcon(@NotNull RegistryAccess manager);

    default @NotNull String getTitleKey() {
        ResourceLocation id = getId();

        return "game.%s.%s".formatted(id.getNamespace(), id.getPath());
    }

    default @NotNull String getDescriptionKey() {
        ResourceLocation id = getId();

        return "game.%s.%s.description".formatted(id.getNamespace(), id.getPath());
    }

    default @NotNull Object[] getDescriptionArguments() {
        return new Object[0];
    }

    default @NotNull String getTaskKey() {
        ResourceLocation id = getId();

        return "game.%s.%s.task".formatted(id.getNamespace(), id.getPath());
    }

    default @NotNull Object[] getTaskArguments() {
        return new Object[0];
    }

    default @NotNull ResourceLocation identifier(@NotNull String subPath) {
        ResourceLocation gameId = getId();

        return ResourceLocation.fromNamespaceAndPath(gameId.getNamespace(), gameId.getPath().concat("/").concat(subPath));
    }
}
