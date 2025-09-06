package work.lclpnet.ap2

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.scoreboard.ScoreboardDisplaySlot
import net.minecraft.server.network.ServerPlayerEntity
import work.lclpnet.ap2.api.event.IntScoreEventSource
import work.lclpnet.ap2.impl.game.BaseGameInstance
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape

fun BaseGameInstance.players() = gameHandle.participants!!

fun BaseGameInstance.allPlayers() = PlayerLookup.all(gameHandle.server)!!

private fun ticks(ticks: Int, seconds: Int): Int = ticks + seconds * 20

fun BaseGameInstance.timeout(ticks: Int = 0, seconds: Int = 0, action: () -> Unit) =
    gameHandle.gameScheduler.timeout(ticks(ticks, seconds), action)!!

fun BaseGameInstance.interval(ticks: Int, action: () -> Unit) =
    gameHandle.gameScheduler.interval(ticks, action)!!

fun BaseGameInstance.translate(key: String, vararg args: Any) =
    gameHandle.translations.translateText(key, *args)!!

fun FFAGameInstance.setupSidebarScoreboard(data: IntScoreEventSource<ServerPlayerEntity>) {
    val objective = gameHandle.scoreboardManager.translateObjective("points", "ap2.score")

    useScoreboardStatsSync(data, objective)
    objective.setSlot(ScoreboardDisplaySlot.SIDEBAR)

    allPlayers().forEach(objective::add)
}

fun BaseGameInstance.readShape(key: String): BlockShape = MapUtil.readShape(map, key)
