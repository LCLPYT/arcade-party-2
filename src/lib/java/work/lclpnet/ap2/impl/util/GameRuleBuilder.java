package work.lclpnet.ap2.impl.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

public class GameRuleBuilder {

    private final GameRules gameRules;
    private final MinecraftServer server;

    public GameRuleBuilder(GameRules gameRules, MinecraftServer server) {
        this.gameRules = gameRules;
        this.server = server;
    }

    /**
     * Sets a boolean game rule value.
     * @param key A boolean game rule.
     * @param value The rule boolean value.
     * @return This builder instance.
     */
    public GameRuleBuilder set(GameRule<Boolean> key, boolean value) {
        gameRules.set(key, value, server);
        return this;
    }

    /**
     * Sets an integer game rule value.
     * @param key An integer game rule.
     * @param value The rule integer value.
     * @return This builder instance.
     */
    public GameRuleBuilder set(GameRule<Integer> key, int value) {
        gameRules.set(key, value, server);
        return this;
    }
}
