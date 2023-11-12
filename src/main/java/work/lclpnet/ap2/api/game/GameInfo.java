package work.lclpnet.ap2.api.game;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public interface GameInfo {

    /**
     * @return A unique {@link Identifier} for the game.
     */
    Identifier getId();

    /**
     * @return The type of the game.
     */
    GameType getType();

    String getAuthor();

    ItemStack getIcon();

    default String getTitleKey() {
        Identifier id = getId();

        return "game.%s.%s".formatted(id.getNamespace(), id.getPath());
    }

    default String getDescriptionKey() {
        Identifier id = getId();

        return "game.%s.%s.description".formatted(id.getNamespace(), id.getPath());
    }

    default Object[] getDescriptionArguments() {
        return new Object[0];
    }
}
