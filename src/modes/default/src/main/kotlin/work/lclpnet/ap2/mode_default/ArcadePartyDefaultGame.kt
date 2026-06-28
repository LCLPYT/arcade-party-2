package work.lclpnet.ap2.mode_default

import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.Items
import org.json.JSONObject
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.config.Ap2Config
import work.lclpnet.ap2.impl.bootstrap.ApDataPacks
import work.lclpnet.config.json.JsonConfigFactory
import work.lclpnet.game.api.Game
import work.lclpnet.game.api.start.GameStartScope
import work.lclpnet.game.api.start.GameStatusManager
import work.lclpnet.game.impl.MinecraftGameConfig
import work.lclpnet.game.util.GameStartUtil
import java.nio.file.Path

class ArcadePartyDefaultGame : Game {
    private val cacheDirectory: Path = Path.of(".cache", ApConstants.ID)
    private val minRequiredPlayers = if (ApConstants.DEVELOPMENT) 1 else 2

    override fun getConfig() = MinecraftGameConfig(
        ApConstants.ID,
        ItemStackTemplate(Items.GOLD_BLOCK)
    )

    override fun canBePlayed(scope: GameStartScope) =
        scope.playerCount() >= minRequiredPlayers

    override fun configureStatusManager(manager: GameStatusManager) {
        GameStartUtil.configureNotEnoughPlayersMessage(manager, minRequiredPlayers)
        GameStartUtil.configureWaitingForPlayersBossBar(manager)
    }

    override fun getBootstrapDataPacks() =
        ApDataPacks(cacheDirectory, CONFIG_FACTORY, ApConstants.logger)

    override fun createFactory() =
        ArcadePartyFactory(CONFIG_FACTORY, ApConstants.logger)
}

val CONFIG_FACTORY = object : JsonConfigFactory<Ap2Config> {
    override fun createDefaultConfig() = Ap2Config()
    override fun createConfig(json: JSONObject) = Ap2Config(json)
}
