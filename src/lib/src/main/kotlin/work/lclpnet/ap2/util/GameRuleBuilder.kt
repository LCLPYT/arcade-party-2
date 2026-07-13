package work.lclpnet.ap2.util

import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.game.MiniGameInstance

class GameRuleBuilder(
    private val gameRules: GameRules,
    private val server: MinecraftServer
) {

    /**
     * Sets a game rule value.
     * @param key The game rule.
     * @param value The rule value.
     * @return This builder instance.
     */
    fun <T : Any> set(key: GameRule<T>, value: T): GameRuleBuilder {
        gameRules.set(key, value, server)
        return this
    }
}

fun MiniGameInstance.useGameRules(config: GameRuleBuilder.() -> Unit) {
    config(GameRuleBuilder(level.gameRules, level.server))
}