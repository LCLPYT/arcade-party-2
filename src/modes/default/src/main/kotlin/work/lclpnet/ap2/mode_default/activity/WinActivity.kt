package work.lclpnet.ap2.mode_default.activity

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import work.lclpnet.activity.ComponentActivity
import work.lclpnet.activity.component.ComponentBundle
import work.lclpnet.activity.component.builtin.BuiltinComponents
import work.lclpnet.ap2.api.util.action.Action
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.util.Announcer
import work.lclpnet.ap2.game.util.ResultAnnouncement
import work.lclpnet.ap2.impl.game.PlayerUtil
import work.lclpnet.ap2.impl.util.Fireworks
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.mode_default.util.ApBaseArgs
import work.lclpnet.ap2.mode_default.util.BaseActivityConfigurator
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapUtils
import work.lclpnet.game.util.ProtectorComponent
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.Scheduler
import work.lclpnet.kibu.title.Title
import java.util.*

private val WINNER_ANNOUNCE_DELAY_TICKS = Ticks.seconds(5)
private val STATS_ANNOUNCE_DELAY_TICKS = Ticks.seconds(8)
private val FIREWORKS_DURATION_TICKS = Ticks.seconds(15)
private val FINAL_DELAY_TICKS = Ticks.seconds(10)
private const val FIREWORKS_MIN_DELAY_TICKS = 3
private const val FIREWORKS_MAX_DELAY_TICKS = 8

class WinActivity(
    private val args: ApBaseArgs
) : ComponentActivity(args.miniGameArgs.server, args.miniGameArgs.logger) {

    private val activityConfigurator = BaseActivityConfigurator(this, args)
    private val announcer: Announcer
    private val translations = args.miniGameArgs.translations
    private val font = args.miniGameArgs.fontService
    private val random = Random()
    private lateinit var scheduler: Scheduler
    private lateinit var world: ServerLevel
    private lateinit var map: GameMap

    init {
        this.announcer = Announcer(translations, ::players)
    }

    override fun registerComponents(components: ComponentBundle) {
        components
            .add(BuiltinComponents.HOOKS)
            .add(BuiltinComponents.SCHEDULER)
            .add(ProtectorComponent.KEY)
    }

    override fun start() {
        super.start()

        scheduler = component(BuiltinComponents.SCHEDULER).scheduler()

        args.tablistManager.status = translations.translateText("ap2.status.game_over")
        args.tablistManager.update()

        PreparationActivity.setupMap(args.miniGameArgs).whenComplete { res, err ->
            if (err != null) {
                args.miniGameArgs.logger.error("Failed to setup win activity map", err)
            } else {
                onReady(res.world, res.map)
            }
        }
    }

    private fun onReady(world: ServerLevel, map: GameMap) {
        this.world = world
        this.map = map

        activityConfigurator.configureProtector()
        activityConfigurator.configureHooks()

        world.waypointManager.breakAllConnections()

        args.playerManager.leaveFinale()

        activityConfigurator.resetPlayers()

        scheduler.timeout(PlayerUtil.getLoadingDelayTicks(args.playerManager.count())) { ->
            afterInitialDelay()
        }
    }

    private fun afterInitialDelay() {
        announcer.withTimes(5, WINNER_ANNOUNCE_DELAY_TICKS - 40, 5)
            .announceSubtitle("ap2.awards.winner_decided")

        scheduler.timeout(WINNER_ANNOUNCE_DELAY_TICKS) { ->
            announceWinner()
        }
    }

    private fun announceWinner() {
        val winner = args.scoreManager.getFinalWinner().orElseThrow()

        for (player in players()) {
            Title.get(player).title(
                winner.getNameFor(player).copy().withStyle(ChatFormatting.AQUA),
                Component.empty(),
                5,
                50,
                0
            )
        }

        SoundHelper.playSound(world, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f)
        SoundHelper.playSound(world, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.RECORDS, 0.8f, 0f)

        beginFireworks().then { onFireworksOver() }

        scheduler.timeout(40) { ->
            for (player in players()) {
                Title.get(player).title(
                    winner.getNameFor(player).copy().withStyle(ChatFormatting.AQUA),
                    translations.translateText(player, "ap2.awards.won_party").formatted(ChatFormatting.DARK_GREEN),
                    0, 100, 5
                )
            }
        }

        scheduler.timeout(STATS_ANNOUNCE_DELAY_TICKS) { ->
            announceStats()
        }
    }

    private fun beginFireworks(): Action<Runnable> {
        val spawn = MapUtils.getSpawnPosition(map)
        val fireworks = Fireworks(world, spawn, 30.0, random)

        return fireworks.start(
            scheduler,
            FIREWORKS_DURATION_TICKS,
            FIREWORKS_MIN_DELAY_TICKS,
            FIREWORKS_MAX_DELAY_TICKS
        )
    }

    private fun announceStats() {
        SoundHelper.playSound(world, SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1f, 0.5f)

        val scoreManager = args.scoreManager

        val order = scoreManager.streamEntriesRanked()
            .flatMap { obj -> obj.stream() }
            .toList()

        val announcement = ResultAnnouncement(
            translations,
            font,
            { player -> PlayerRef.create(player) },
            order,
            { ref -> scoreManager.getEntry(ref) }
        )

        for (player in players()) {
            announcement.sendTop(5, player)
        }
    }

    private fun onFireworksOver() {
        translations.translateText("ap2.awards.thanks").formatted(ChatFormatting.GRAY).sendTo(players())

        scheduler.timeout(FINAL_DELAY_TICKS) { ->
            endGame()
        }
    }

    private fun endGame() {
        args.finisher.finishGame()
    }

    private fun players() = PlayerLookup.level(world)
}
