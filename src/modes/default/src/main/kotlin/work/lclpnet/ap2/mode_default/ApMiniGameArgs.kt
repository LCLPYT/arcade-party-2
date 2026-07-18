package work.lclpnet.ap2.mode_default

import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import work.lclpnet.ap2.impl.base.MiniGameManager
import work.lclpnet.ap2.impl.data.DataManager
import work.lclpnet.ap2.api.map.MapFacade
import work.lclpnet.ap2.api.music.SongManager
import work.lclpnet.ap2.game.util.PlayerUtil
import work.lclpnet.ap2.util.FontService
import work.lclpnet.game.api.WorldFacade
import work.lclpnet.kibu.cmd.impl.CommandStack
import work.lclpnet.kibu.hook.HookStack
import work.lclpnet.kibu.scheduler.util.SchedulerStack
import work.lclpnet.kibu.translate.Translations

/**
 * A container of objects required for starting a mini-game.
 * All objects in this class should be required in the scope of a mini-game, e.g. [work.lclpnet.kibu.translate.Translations],
 * [work.lclpnet.kibu.hook.HookStack], [work.lclpnet.kibu.scheduler.util.SchedulerStack], [work.lclpnet.game.api.WorldFacade] etc.
 * Other stuff that is required for the "base" game cycle, so outside the mini-game scope,
 * should rather go into the [work.lclpnet.ap2.mode_default.util.ApBaseArgs] container.
 */
data class ApMiniGameArgs(
    val server: MinecraftServer,
    val logger: Logger,
    val translations: Translations,
    val hookStack: HookStack,
    val commandStack: CommandStack,
    val schedulerStack: SchedulerStack,
    val worldFacade: WorldFacade,
    val mapFacade: MapFacade,
    val playerUtil: PlayerUtil,
    val miniGames: MiniGameManager,
    val songManager: SongManager,
    val dataManager: DataManager,
    val fontService: FontService
)