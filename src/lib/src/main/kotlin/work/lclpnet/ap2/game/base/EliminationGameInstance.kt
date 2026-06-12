package work.lclpnet.ap2.game.base

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.BossEvent
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import work.lclpnet.ap2.api.game.EliminationController
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.api.stats.FFAStatsManager
import work.lclpnet.ap2.core.hook.PlayerEliminatedCallback
import work.lclpnet.ap2.core.mixin.entity.LivingEntityAccessor
import work.lclpnet.ap2.ext.allPlayers
import work.lclpnet.ap2.ext.isParticipating
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.ext.server
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.util.GameStartSequence
import work.lclpnet.ap2.impl.game.GameCommons
import work.lclpnet.ap2.impl.game.data.EliminationDataContainer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedBossBar
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.entity.EntityHealthCallback
import work.lclpnet.kibu.translate.text.FormatWrapper
import work.lclpnet.kibu.translate.text.TranslatedText
import kotlin.time.Clock
import kotlin.time.Instant

abstract class EliminationGameInstance(
    gameHandle: MiniGameHandle,
    world: ServerLevel,
    map: GameMap
) : FFAGameInstance(gameHandle, world, map), EliminationController {

    override val data = EliminationDataContainer { player: ServerPlayer ->
        PlayerRef.create(player)
    }
    private var remainingDisplay: DynamicTranslatedBossBar? = null
    private var eliminatedMessages = true
    private var teleportEliminated = true
    private var survivalStart: Instant? = null
    private var survivalStats: FFAStatsManager? = null

    override fun configureStartup(sequence: GameStartSequence) {
        sequence.beforeGo { next ->
            survivalStart = Clock.System.now()
            next.run()
        }

        super.configureStartup(sequence)
    }

    override fun participantRemoved(player: ServerPlayer) {
        // record survival time before super, which may end the game and freeze the stats
        recordSurvivalTime(player)

        // make sure the player is tracked as eliminated
        data.add(player)

        if (remainingDisplay != null) {
            val (key, args) = remainingTitle()
            remainingDisplay!!.setTranslationKey(key)
            remainingDisplay!!.setArguments(args)
        }

        super.participantRemoved(player)
    }

    protected fun useRemainingPlayersDisplay(): DynamicTranslatedBossBar {
        val gameInfo = gameHandle.gameInfo
        val id = gameInfo.identifier("remaining")

        val (key, args) = remainingTitle()

        val bossBar = gameHandle.translations.translateBossBar(id, key, *args)
            .with(gameHandle.bossBarProvider)
            .formatted(ChatFormatting.GREEN)

        remainingDisplay = DynamicTranslatedBossBar(bossBar, key, args)

        bossBar.setColor(BossEvent.BossBarColor.GREEN)

        bossBar.addPlayers(allPlayers())

        gameHandle.bossBarHandler.showOnJoin(bossBar)

        return remainingDisplay!!
    }

    private fun remainingTitle(): Pair<String, Array<Any>> {
        val remaining = gameHandle.participants.count()

        val key = if (remaining != 1) "ap2.game.remaining" else "ap2.game.remaining_single"

        val args = arrayOf<Any>(
            FormatWrapper.styled(remaining, ChatFormatting.YELLOW)
        )

        return key to args
    }

    /**
     * Instantly makes players who would have died spectators and reset them.
     */
    protected fun useSmoothDeath() {
        val hooks = gameHandle.hooks

        EntityHealthCallback.HOOK.registerWith(hooks) { entity, health ->
            if (entity !is ServerPlayer) return@registerWith false

            GameCommons.handleCustomDeath(entity, health) { player, source ->
                onDeath(player, source.entity)
                eliminate(player, source)
            }
        }
    }

    protected open fun onDeath(player: ServerPlayer, attacker: Entity?) {
        val accessor = player as LivingEntityAccessor

        accessor.invokeDropEquipment(level)
        accessor.invokeDropExperience(level, attacker)
    }

    protected fun disableEliminationMessages() {
        this.eliminatedMessages = false
    }

    protected fun disableTeleportEliminated() {
        this.teleportEliminated = false
    }

    protected fun eliminateBelowCriticalHeight() {
        commons().whenBelowCriticalHeight().then { player ->
            eliminate(player)
        }
    }

    @Synchronized
    override fun eliminateAll(players: Iterable<ServerPlayer>) {
        val toEliminate = mutableSetOf<ServerPlayer>()

        for (player in players) {
            if (!isParticipating(player)) continue

            toEliminate.add(player)
            onEliminated(player)

            if (eliminatedMessages) {
                gameHandle.deathMessages.eliminated(player).sendTo(PlayerLookup.all(server))
            }
        }

        // mark all players as eliminated at the same moment
        data.addAll(toEliminate)

        for (player in toEliminate) {
            gameHandle.participants.remove(player)

            gameHandle.playerUtil.resetPlayer(player)

            if (teleportEliminated) {
                gameHandle.worldFacade.teleport(player)
            }
        }
    }

    override fun eliminate(player: ServerPlayer, source: DamageSource?, customMsg: TranslatedText?) {
        if (isParticipating(player)) {
            if (eliminatedMessages) {
                val msg = customMsg ?: gameHandle.deathMessages.getDeathMessage(player, source)

                msg.sendTo(PlayerLookup.all(server))
            }

            gameHandle.participants.remove(player)
            onEliminated(player)
        }

        gameHandle.playerUtil.resetPlayer(player)

        if (teleportEliminated) {
            gameHandle.worldFacade.teleport(player)
        }
    }

    protected open fun onEliminated(player: ServerPlayer) {
        PlayerEliminatedCallback.HOOK.invoker().onEliminated(player)
    }

    /**
     * Enables tracking of the time each player survives, in whole seconds.
     *
     *
     * The timer starts when the game begins (right before [.go] is called) and stops for a player
     * the moment they are eliminated. Players that are still alive when the game ends are credited with the
     * full duration they survived.
     *
     * @param stats The stats manager that holds the given stat, as returned by [.createStats].
     */
    protected fun trackSurvivalTime(stats: FFAStatsManager?) {
        this.survivalStats = stats

        winManager.addListener {
            recordRemainingSurvivalTime()
        }
    }

    private fun recordRemainingSurvivalTime() {
        for (player in players()) {
            recordSurvivalTime(player)
        }
    }

    private fun recordSurvivalTime(player: ServerPlayer) {
        if (survivalStats == null || survivalStart == null) return

        val elapsedMillis = Clock.System.now().toEpochMilliseconds() - survivalStart!!.toEpochMilliseconds()

        survivalStats!!.set(player, CommonStats.TimeSurvived, (elapsedMillis / 1000L).toInt())
    }
}