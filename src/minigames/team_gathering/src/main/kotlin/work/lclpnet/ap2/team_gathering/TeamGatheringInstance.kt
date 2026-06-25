package work.lclpnet.ap2.team_gathering

import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.team.DyeTeamKey
import work.lclpnet.ap2.game.team.Team
import work.lclpnet.ap2.game.team.TeamManager
import work.lclpnet.ap2.game.util.*
import work.lclpnet.ap2.impl.util.TeamStorage
import work.lclpnet.ap2.impl.util.TextUtil
import work.lclpnet.gaco.ds.WeightedList
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.util.ResetWorldModifier
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import work.lclpnet.kibu.translate.text.FormatWrapper
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.random.asJavaRandom
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val DURATION = 3.minutes

class TeamItems {
    val items = mutableSetOf<Item>()
}

class TeamGatheringInstance(
    override val gameHandle: MiniGameHandle,
    override val level: ServerLevel,
    val teamManager: TeamManager,
    val walls: ResetWorldModifier,
) : MiniGameInstance {

    val data = useDataContainer(teamManager, ::IntScoreDataContainer)
    override val winManager = useTeamWinManager(teamManager, map = null) { data }
    override val participantListener = useLastRemainingTeamListener(teamManager, winManager)
    val teamItems = TeamStorage.create(::TeamItems)

    init {
        useSurvivalMode()
    }

    override fun start() {
        configureDefaults()

        for (player in allPlayers()) {
            gameHandle.worldFacade.teleport(player)
        }

        setupTeams()

        level.gameRules.set(GameRules.KEEP_INVENTORY, true, server)

        useStartup(::go)
    }

    private fun setupTeams() {
        val playersPerTeam = when {
            players().count() <= 2 -> 1
            else -> 2
        }

        val teamCount = ceil(players().count() / playersPerTeam.toFloat()).toInt()

        check(teamCount <= DyeTeamKey.entries.size) { "Not enough team keys available" }

        val teamKeys = DyeTeamKey.entries.shuffled().take(teamCount).toMutableList()

        teamManager.setUseColorCodes(true)

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

            repeat(playersPerTeam) {
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
                victim is ServerPlayer && !source.isOf(DamageTypes.FELL_OUT_OF_WORLD)
            }
        }

        PlayerInventoryHooks.PLAYER_PICKUP.registerWith(hooks) { player, itemEntity ->
            if (player is ServerPlayer && isParticipating(player)) {
                onItemPickedUp(player, itemEntity.item)
            }

            false
        }

        PlayerInventoryHooks.DROPPED_ITEM_ENTITY.registerWith(hooks) { player, itemEntity ->
            if (player is ServerPlayer && isParticipating(player)) {
                onItemDropped(player, itemEntity.item)
            }
        }

        runEveryTick {
            for (team in teamManager.teams) {
                updateItemCount(team)
            }
        }

        runEvery(2.seconds) {
            for (team in teamManager.teams) {
                sendItemCount(team)
            }
        }

        useTaskTimer(DURATION).whenDone {
            winManager.complete()
        }
    }

    fun sendItemCount(team: Team) {
        val score = data.getScore(team)

        translate("count", FormatWrapper.styled(score, ChatFormatting.YELLOW))
            .withStyle(ChatFormatting.AQUA)
            .sendTo(team.players, true)
    }

    private fun updateItemCount(team: Team) {
        val items = teamItems.get(team).items

        for (player in team.players) {
            for (stack in player.inventory) {
                if (stack.isEmpty) continue

                items.add(stack.item)
            }
        }

        data.setScore(team, items.size)
    }

    private fun onItemPickedUp(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) return

        val team = teamManager.getTeam(player) ?: return

        val items = teamItems.get(team).items
        val newItem = items.add(stack.item)

        updateItemCount(team)
        sendItemCount(team)

        if (!newItem) return

        translate(
            "found",
            FormatWrapper.styled(player.scoreboardName, ChatFormatting.YELLOW),
            TextUtil.getVanillaName(stack).withStyle(ChatFormatting.AQUA)
        ).withStyle(ChatFormatting.GREEN)
            .sendTo(team.players)

        for (p in team.players) {
            p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 2f)
        }
    }

    private fun onItemDropped(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) return

        val team = teamManager.getTeam(player) ?: return

        updateItemCount(team)
        sendItemCount(team)

        val items = teamItems.get(team).items

        // check if a team member still has the item
        if (team.players.any { player -> player.inventory.contains { it.isOf(stack.item) } }) return

        items.remove(stack.item)

        translate(
            "dropped",
            FormatWrapper.styled(player.scoreboardName, ChatFormatting.YELLOW),
            TextUtil.getVanillaName(stack).withStyle(ChatFormatting.AQUA)
        ).withStyle(ChatFormatting.RED)
            .sendTo(team.players)

        for (p in team.players) {
            p.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.5f, 0.5f)
        }
    }
}