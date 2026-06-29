package work.lclpnet.ap2.team_gathering

import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.core.hook.ItemCraftedCallback
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.ext.mc.teleport
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.team.DyeTeamKey
import work.lclpnet.ap2.game.team.Team
import work.lclpnet.ap2.game.team.TeamKey
import work.lclpnet.ap2.game.team.TeamManager
import work.lclpnet.ap2.game.util.*
import work.lclpnet.ap2.impl.util.TeamStorage
import work.lclpnet.ap2.impl.util.TextUtil
import work.lclpnet.gaco.ds.WeightedList
import work.lclpnet.game.impl.menu.PaginatedOptionMenu
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.util.ResetWorldModifier
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import work.lclpnet.kibu.translate.text.FormatWrapper
import java.util.*
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.random.asJavaRandom
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val DURATION = 3.minutes

val UniqueItems = Stat("unique_items", 0)
val ItemsCrafted = Stat("items_crafted", 0)
val ItemsPickedUp = Stat("items_picked_up", 0)

class TeamState {
    val items = mutableSetOf<Item>()
    val playerItems = mutableMapOf<UUID, Set<Item>>()
    val crafted = mutableSetOf<Item>()
    val pickedUp = mutableSetOf<Item>()
    val playerCrafted = mutableMapOf<UUID, MutableSet<Item>>()
    val playerPickedUp = mutableMapOf<UUID, MutableSet<Item>>()
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
    val stats = useTeamStats(
        winManager,
        data,
        CommonStats.IntScore,
        teamStats = listOf(ItemsCrafted, ItemsPickedUp),
        memberStats = listOf(UniqueItems, ItemsCrafted, ItemsPickedUp, CommonStats.DistanceMoved)
    )
    val teamStates = TeamStorage.create(::TeamState)
    var soloPlayerKey: PlayerRef? = null
    var soloTeamKey: TeamKey? = null

    init {
        useSurvivalMode()
    }

    override fun start() {
        configureDefaults()

        for (player in allPlayers()) {
            player.teleport(level.respawnData.pos(), level)
        }

        setupTeams()

        level.gameRules.set(GameRules.KEEP_INVENTORY, true, server)

        val soloPlayer = this.soloPlayer

        if (soloPlayer != null) {
            soloPlayer.addEffect(MobEffectInstance(MobEffects.HASTE, Int.MAX_VALUE, 1, false, false, false))

            translate("solo_hint").withStyle(ChatFormatting.AQUA).sendTo(soloPlayer)
        }

        trackDistanceMoved(stats.players)

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
            val teamless = pickSoloPlayer()
            val key = teamKeys.removeFirst()

            soloPlayerKey = PlayerRef.create(teamless)
            soloTeamKey = key

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

    private fun pickSoloPlayer(): ServerPlayer {
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
            if (player is ServerPlayer) {
                recordDistinct(player, itemEntity.item.item, pickedUpBucket, craftedBucket)
            }

            false
        }

        ItemCraftedCallback.HOOK.registerWith(hooks) { player, stack, _ ->
            recordDistinct(player, stack.item, craftedBucket, pickedUpBucket)
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
            winManager.complete().then {
                showWinningInventory()
            }
        }
    }

    private fun showWinningInventory() {
        val bestTeams = data.getBestSubjects { teamManager.getTeam(it) }
        val winnerTeam = bestTeams.randomOrNull() ?: return

        val items = teamStates.get(winnerTeam).items

        val menu = PaginatedOptionMenu.builder<Item>(translations, gameHandle.gameInfo.identifier("items"))
            .search(false)
            .sort(false)
            .options(items)
            .optionIcon { _, item -> ItemStack(item) }
            .closeOnSelect(false)
            .canInteract { false }
            .title { player -> translations.translateText(
                player,
                "winning_team_items",
                winnerTeam.key.getDisplayName(translations)
            ) }
            .build()

        for (player in allPlayers()) {
            menu.open(player)
        }
    }

    fun sendItemCount(team: Team) {
        val score = data.getScore(team)

        translate("count", FormatWrapper.styled(score, ChatFormatting.YELLOW))
            .withStyle(ChatFormatting.AQUA)
            .sendTo(team.players, true)
    }

    private fun updateItemCount(team: Team) {
        val teamState = this.teamStates.get(team)
        val teamItems = teamState.items
        val previousItems = teamState.playerItems

        // the solo team accumulates found items and doesn't have to keep them in the inventory
        val tracksDrops = team.key != soloTeamKey

        val currentItems = HashMap<UUID, Set<Item>>()
        val teamHeldItems = HashSet<Item>()

        for (player in team.players) {
            val held = HashSet<Item>()

            for (slot in player.inventoryMenu.slots) {
                if (slot == player.inventoryMenu.resultSlot) continue

                val stack = slot.item

                if (stack.isEmpty) continue

                held.add(stack.item)
            }

            player.containerMenu.carried.takeIf { !it.isEmpty }?.let { held.add(it.item) }

            currentItems[player.uuid] = held
            teamHeldItems.addAll(held)
        }

        // detect items that newly appeared in a player's inventory
        for (player in team.players) {
            val current = currentItems[player.uuid] ?: continue
            val previous = previousItems[player.uuid] ?: emptySet()

            for (item in current) {
                if (item in previous) continue
                if (!teamItems.add(item)) continue

                notifyFound(team, player, item)
            }
        }

        // detect items that were held last tick but are no longer held by anyone on the team
        if (tracksDrops) {
            for (player in team.players) {
                val currentPlayerItems = currentItems[player.uuid] ?: continue
                val previous = previousItems[player.uuid] ?: continue

                for (item in previous) {
                    if (item in currentPlayerItems) continue
                    if (item in teamHeldItems) continue
                    if (!teamItems.remove(item)) continue

                    notifyDropped(team, player, item)
                }
            }
        }

        previousItems.keys.retainAll(currentItems.keys)
        previousItems.putAll(currentItems)

        data.setScore(team, teamItems.size)

        if (team.key == soloTeamKey) {
            soloPlayer?.let { stats.players.set(it, UniqueItems, teamItems.size) }
        } else {
            for (player in team.players) {
                val held = currentItems[player.uuid] ?: continue
                stats.players.set(player, UniqueItems, held.size)
            }
        }

        sendItemCount(team)
    }

    private class Bucket(
        val teamSet: (TeamState) -> MutableSet<Item>,
        val playerSets: (TeamState) -> MutableMap<UUID, MutableSet<Item>>,
        val stat: Stat<Int>,
    )

    private val craftedBucket = Bucket(TeamState::crafted, TeamState::playerCrafted, ItemsCrafted)
    private val pickedUpBucket = Bucket(TeamState::pickedUp, TeamState::playerPickedUp, ItemsPickedUp)

    // counts a distinct item type for [own], unless [other] already claimed it (the first
    // acquisition method wins, so an item is never counted toward both crafted and pickup)
    private fun recordDistinct(player: ServerPlayer, item: Item, own: Bucket, other: Bucket) {
        val team = teamManager.getTeam(player) ?: return
        val state = teamStates.get(team)

        val ownPlayerSet = own.playerSets(state).getOrPut(player.uuid) { mutableSetOf() }
        val otherPlayerSet = other.playerSets(state).getOrPut(player.uuid) { mutableSetOf() }

        if (item !in otherPlayerSet && ownPlayerSet.add(item)) {
            stats.players.set(player, own.stat, ownPlayerSet.size)
        }

        val ownTeamSet = own.teamSet(state)

        if (item !in other.teamSet(state) && ownTeamSet.add(item)) {
            stats.teams.set(team, own.stat, ownTeamSet.size)
        }
    }

    private fun notifyFound(team: Team, player: ServerPlayer, item: Item) {
        data.setScore(team, teamStates.get(team).items.size)

        translate(
            "found",
            FormatWrapper.styled(player.scoreboardName, ChatFormatting.YELLOW),
            TextUtil.getVanillaName(item).withStyle(ChatFormatting.AQUA)
        ).withStyle(ChatFormatting.GREEN)
            .sendTo(team.players)

        for (p in team.players) {
            p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 2f)
        }
    }

    private fun notifyDropped(team: Team, player: ServerPlayer, item: Item) {
        data.setScore(team, teamStates.get(team).items.size)

        translate(
            "dropped",
            FormatWrapper.styled(player.scoreboardName, ChatFormatting.YELLOW),
            TextUtil.getVanillaName(item).withStyle(ChatFormatting.AQUA)
        ).withStyle(ChatFormatting.RED)
            .sendTo(team.players)

        for (p in team.players) {
            p.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.5f, 0.5f)
        }
    }

    private val soloPlayer: ServerPlayer?
        get() = soloPlayerKey?.let { players().getParticipant(it.uuid) }
}