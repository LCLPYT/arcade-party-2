package work.lclpnet.ap2.api.game;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

public interface GameInfo {

    /**
     * @return A unique {@link Identifier} for the game.
     */
    @NotNull Identifier getId();

    /**
     * @return The type of the game.
     */
    @NotNull GameType getType();

    @NotNull String getAuthor();

    @NotNull ItemStack getIcon(@NotNull DynamicRegistryManager manager);

    default @NotNull String getTitleKey() {
        Identifier id = getId();

        return "game.%s.%s".formatted(id.getNamespace(), id.getPath());
    }

    default @NotNull String getDescriptionKey() {
        Identifier id = getId();

        return "game.%s.%s.description".formatted(id.getNamespace(), id.getPath());
    }

    default @NotNull Object[] getDescriptionArguments() {
        return new Object[0];
    }

    default @NotNull String getTaskKey() {
        Identifier id = getId();

        return "game.%s.%s.task".formatted(id.getNamespace(), id.getPath());
    }

    default @NotNull Object[] getTaskArguments() {
        return new Object[0];
    }

    default @NotNull Identifier identifier(@NotNull String subPath) {
        Identifier gameId = getId();

        return Identifier.of(gameId.getNamespace(), gameId.getPath().concat("/").concat(subPath));
    }
}
