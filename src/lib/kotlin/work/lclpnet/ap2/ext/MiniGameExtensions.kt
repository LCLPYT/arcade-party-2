package work.lclpnet.ap2.ext

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.DisplaySlot
import org.slf4j.Logger
import work.lclpnet.ap2.api.event.IntScoreEventSource
import work.lclpnet.ap2.impl.game.BaseGameInstance
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape

fun BaseGameInstance.players() = gameHandle.participants!!

fun BaseGameInstance.allPlayers() = PlayerLookup.all(gameHandle.server)!!

private fun ticks(ticks: Int, seconds: Int): Int = ticks + seconds * 20

fun BaseGameInstance.timeout(ticks: Int = 0, seconds: Int = 0, action: () -> Unit) =
    gameHandle.scheduler.timeout(ticks(ticks, seconds), action)!!

fun BaseGameInstance.interval(ticks: Int, action: () -> Unit) =
    gameHandle.scheduler.interval(ticks, action)!!

fun BaseGameInstance.eachTick(action: () -> Unit) =
    interval(1, action)

fun BaseGameInstance.translate(key: String, vararg args: Any) =
    gameHandle.translations.translateText(key, *args)!!

val BaseGameInstance.logger: Logger
    get() = gameHandle.logger

fun FFAGameInstance.setupSidebarScoreboard(data: IntScoreEventSource<ServerPlayer>) {
    val objective = gameHandle.scoreboardManager.translateObjective("points", "ap2.score")

    useScoreboardStatsSync(data, objective)
    objective.setSlot(DisplaySlot.SIDEBAR)

    allPlayers().forEach(objective::add)
}

fun BaseGameInstance.readShape(key: String): BlockShape = MapUtil.readShape(map, key)
