package work.lclpnet.ap2.api.game;

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
}
