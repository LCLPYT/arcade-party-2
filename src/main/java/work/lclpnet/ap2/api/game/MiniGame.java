package work.lclpnet.ap2.api.game;

public interface MiniGame extends GameInfo {

    /**
     * Returns whether the game ca be a finale.
     * Games which can be a finale must always determine a winner.
     * Games which allow for multiple / no winners cannot be a finale.
     * Additionally, games that are undesirable as a final can be filtered this way.
     * @param context The context, which holds information like finalist count.
     * @return Whether the game can be a finale.
     */
    boolean canBeFinale(GameStartContext context);

    /**
     * Checks whether the game can be played right now.
     * This can be used to enforce minimum players.
     * @param context The context, which holds information like participant count.
     * @return Whether the game can be played right now.
     * @implNote It is recommended to only enforce a minimum player count if absolutely needed (e.g. team games).
     * If the game can function with fewer players, possibly only one, this should still return true.
     * This way, most games can easily be played in development mode with only one client.
     * Remember that the ArcadeParty game mode still enforces player count requirements in production mode.
     */
    boolean canBePlayed(GameStartContext context);

    MiniGameInstance createInstance(MiniGameHandle gameHandle);
}
