package work.lclpnet.ap2.api.game;

public interface MiniGame extends GameInfo {

    /**
     * Returns whether the game ca be a finale.
     * Games which can be a finale must always determine a winner.
     * Games which allow for multiple / no winners cannot be a finale.
     * Additionally, games that are undesirable as a final can be filtered this way.
     * @return Whether the game can be a finale.
     */
    boolean canBeFinale();

    /**
     * Checks whether the game can be played right now.
     * This can be used to enforce minimum players.
     * @return Whether the game can be played right now.
     */
    boolean canBePlayed();

    MiniGameInstance createInstance(MiniGameHandle gameHandle);
}
