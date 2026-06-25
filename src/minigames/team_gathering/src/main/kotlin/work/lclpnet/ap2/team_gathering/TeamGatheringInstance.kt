package work.lclpnet.ap2.team_gathering

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.ext.allPlayers
import work.lclpnet.ap2.ext.hooks
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.team.DyeTeamKey
import work.lclpnet.ap2.game.team.TeamManager
import work.lclpnet.ap2.game.util.*
import work.lclpnet.gaco.ds.WeightedList
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.util.ResetWorldModifier
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.random.asJavaRandom
import kotlin.time.Duration.Companion.minutes

val DURATION = 3.minutes

class TeamGatheringInstance(
    override val gameHandle: MiniGameHandle,
    override val level: ServerLevel,
    val teamManager: TeamManager,
    val walls: ResetWorldModifier,
) : MiniGameInstance {

    val data = useDataContainer(teamManager, ::IntScoreDataContainer)
    override val winManager = useTeamWinManager(teamManager, map = null) { data }
    override val participantListener = useLastRemainingTeamListener(teamManager, winManager)

    init {
        useSurvivalMode()
    }

    override fun start() {
        configureDefaults()

        for (player in allPlayers()) {
            gameHandle.worldFacade.teleport(player)
        }

        setupTeams()

        useStartup(::go)
    }

    private fun setupTeams() {
        val teamCount = ceil(players().count() / 2f).toInt()

        check(teamCount <= DyeTeamKey.entries.size) { "Not enough team keys available" }

        val teamKeys = DyeTeamKey.entries.shuffled().take(teamCount).toMutableList()

        for (key in teamKeys) {
            teamManager.registerTeam(key)
        }

        val players = players().toMutableSet()

        if (players.size % 2 == 1) {
            val teamless = pickTeamlessPlayer()
            val key = teamKeys.removeFirst()

            teamManager.getTeam(key)?.let {
                teamManager.joinTeam(teamless, it)
                players.remove(teamless)
            }
        }

        for (teamKey in teamKeys) {
            val team = teamManager.getTeam(teamKey) ?: continue

            repeat(2) {
                val player = players.first()
                players.remove(player)

                teamManager.joinTeam(player, team)
            }
        }

        gameHandle.playerUtil.updatePlayerListNames(players.toSet())
    }

    private fun pickTeamlessPlayer(): ServerPlayer {
        val ranked = players()
            .map { player -> player to gameHandle.rankView.rank(player) }

        val distinctRanks = ranked
            .map { (_, rank) -> rank }
            .distinct()
            .sorted()

        val rankCount = (distinctRanks.size / 2).coerceAtLeast(1)
        val eligibleRanks = distinctRanks.take(rankCount).toHashSet()

        val eligible = ranked.filter { (_, rank) -> rank in eligibleRanks }

        val worstRank = eligible.maxOf { (_, rank) -> rank }
        val disparity = 0.5f

        val weighted = WeightedList<ServerPlayer>()

        for ((player, rank) in eligible) {
            val weight = 1 + (worstRank - rank) * disparity
            weighted.add(player, weight)
        }

        return requireNotNull(weighted.getRandomElement(Random.asJavaRandom()))
    }

    private fun go() {
        walls.undo()

        useProtector {
            allowAll()

            ProtectionTypes.ALLOW_DAMAGE.disallow(this) { victim, source ->
                victim is ServerPlayer && source.entity is ServerPlayer
            }
        }

        PlayerInventoryHooks.PLAYER_PICKED_UP.registerWith(hooks) { player, itemEntity ->
            
        }

        useTaskTimer(DURATION).whenDone {
            winManager.complete()
        }
    }
}