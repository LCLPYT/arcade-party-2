package work.lclpnet.ap2.game.pvp_tournament

import eu.pb4.mapcanvas.api.core.DrawableCanvas
import eu.pb4.mapcanvas.api.core.PlayerCanvas
import kotlinx.coroutines.*
import kotlinx.coroutines.future.future
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Avatar
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.Mannequin
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import work.lclpnet.ap2.api.base.Participants
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.core.mixin.MannequinAccessor
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.teleport
import work.lclpnet.ap2.ext.mc.teleportTo
import work.lclpnet.ap2.game.pvp_tournament.gen.Match
import work.lclpnet.ap2.game.pvp_tournament.gen.SkinPlayerIcons
import work.lclpnet.ap2.game.pvp_tournament.gen.TournamentVisualizer
import work.lclpnet.ap2.game.pvp_tournament.util.ArenaInstance
import work.lclpnet.ap2.game.pvp_tournament.util.KITS_1V1
import work.lclpnet.ap2.game.pvp_tournament.util.Kit
import work.lclpnet.ap2.game.pvp_tournament.util.MatchKitManager
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.WinSequence
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer
import work.lclpnet.ap2.impl.game.data.Ordering
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.ap2.util.PvpBehavior
import work.lclpnet.ap2.util.mojang.SkinFetcher
import work.lclpnet.combatctl.api.CombatControl
import work.lclpnet.gaco.core.api.EntityRef
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import work.lclpnet.kibu.map.MapColorUtil
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.kibu.title.Title
import work.lclpnet.lobby.game.map.GameMap
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

enum class TournamentVariant {
    SINGLE_ELIMINATION,
    SWISS_STYLE,
}

class MatchData(
    val match: Match,
    val arena: ArenaInstance,
    val kit: Kit,
    val level: ServerLevel,
    private val allPlayers: Participants,
) {
    val tasks = mutableListOf<TaskHandle>()
    val npcs = mutableListOf<EntityRef<Mannequin>>()
    val players = mutableListOf<UUID>()
    var started = false

    val participants: List<Avatar>
        get() =
            match.players.mapNotNull { entity(it) }

    fun entity(ref: PlayerRef): Avatar? {
        val player = allPlayers.getParticipant(ref.uuid).orElse(null)

        if (player != null) {
            return if (player.uuid in players) { player } else null
        }

        return npcs.find { it.uuid == ref.uuid }?.resolve()
    }

    fun teleport(entity: Avatar) {
        val ref = match.players.find { it.uuid == entity.uuid } ?: return
        val spawn = arena.spawns[match.participant(ref)]

        entity.teleport(level, spawn)
    }

    fun ref(entity: Avatar): PlayerRef? = when (entity.uuid) {
        null -> null
        match.leftPlayer?.uuid -> match.leftPlayer
        match.rightPlayer?.uuid -> match.rightPlayer
        else -> null
    }
}

const val DEBUG_FILL_WITH_NPC = true
val SUDDEN_DEATH_DELAY = 40.seconds
val MATCH_DRAW_DELAY = 100.seconds

class PvpTournamentInstance(gameHandle: MiniGameHandle) : FFAGameInstance(gameHandle), MapBootstrap {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val data = IntScoreDataContainer(
        PlayerRef::create,
        Ordering.ASCENDING,
        "game.ap2.pvp_tournament.placed"
    )
    private val matchData = mutableMapOf<Match, MatchData>()

    val movementBlocker = SimpleMovementBlocker(gameHandle.scheduler).also {
        it.setModifySpeedAttribute(false)
        it.init(gameHandle.hooks)
    }

    val playerRefs = if (DEBUG_FILL_WITH_NPC) {
        val targetPlayerCount = 12
        val extraPlayers = (targetPlayerCount - players().count()).coerceAtLeast(0)

        buildList {
            addAll(players().map { PlayerRef.create(it) })

            repeat(extraPlayers) {
                add(PlayerRef(UUID.randomUUID(), "NPC #${it + 1}"))
            }
        }
    } else {
        players().map { PlayerRef.create(it) }
    }
    
    val playerIcons = SkinPlayerIcons(
        SkinFetcher(
            gameHandle.assetManager.httpClient,
            gameHandle.assetManager.mojangAssetCache,
            SkinFetcher.sharedSkinDirectory(),
            logger
        )
    )

    val canvas: PlayerCanvas = DrawableCanvas.create().also { canvas ->
        gameHandle.whenDone {
            allPlayers().forEach { canvas.removePlayer(it) }  // temporary fix, until https://github.com/Patbox/map-canvas-api/issues/8 is fixed
            canvas.destroy()
        }
    }

    val kitManager = MatchKitManager(KITS_1V1)
    
    var pvp: PvpBehavior? = null
    var tournamentResult: TournamentResult? = null

    override fun getData() = data

    init {
        useSurvivalMode()
    }

    override fun createWorldBootstrap(
        world: ServerLevel,
        map: GameMap
    ): CompletableFuture<Void> {
        val skinsPreload = scope.future {
            players().map {
                launch { playerIcons.preload(it.gameProfile) }
            }.joinAll()
        }
        
        val setup = TournamentSetup(logger, map, playerRefs) { path ->
            schematicBlocking(assetPath(path))
        }

        return scope.future {
            setup.setup(TournamentVariant.SINGLE_ELIMINATION).also { tournamentResult = it }
        }.thenCompose { result ->
            gameHandle.server.submit {
                setup.placeArenas(world, result.arenas.values)
            }
        }.thenCombine(skinsPreload) { _, _ -> null }
    }

    override fun prepare() {
        playerRefs.forEach { setupPlayerForNextMatch(it) }
        players().forEach { movementBlocker.disableMovement(it) }

        preventMovingOfFilledMaps()

        scope.launch {
            updateCanvas()
        }
    }

    private fun preventMovingOfFilledMaps() {
        registerHook(PlayerInventoryHooks.SWAP_HANDS, PlayerInventoryHooks.SwapHands { player, _ ->
            player.offhandItem.`is`(Items.FILLED_MAP)
        })

        registerHook(PlayerInventoryHooks.MODIFY_INVENTORY, PlayerInventoryHooks.InventoryModify { event ->
            val stack = event.clickedStack()

            stack != null && stack.`is`(Items.FILLED_MAP)
        })

        registerHook(PlayerInventoryHooks.DROP_ITEM, PlayerInventoryHooks.DropItem { player, i, _ ->
            val slot = player.inventory.getSlot(i)

            slot != null && slot.get().`is`(Items.FILLED_MAP)
        })
    }

    private suspend fun updateCanvas() {
        val visualizer = TournamentVisualizer(playerIcons)
        val image = visualizer.generateImage(tournamentResult!!.tournament)

        val width = min(image.width, canvas.width)
        val height = min(image.height, canvas.height)

        val region = image.getSubimage(0, 0, width, height)
        val raw = MapColorUtil.toBytes(region)

        for (y in 0..<height) {
            for (x in 0..<width) {
                canvas.setRaw(x, y, raw[y * width + x])
            }
        }

        canvas.sendUpdates()
    }

    override fun go() {
        pvp = PvpBehavior(gameHandle, world).also { it.configure() }

        players().forEach {
            // disallow pvp behavior by default, enable when match is started
            pvp!!.disallow(it)

            movementBlocker.enableMovement(it)
        }

        val matches = tournamentResult!!.tournament.matches

        val minRound = matches.minOf { it.round }

        matches.filter { it.round == minRound }.forEach { startMatch(it) }

        onDeathOf<ServerPlayer> { player, _ ->
            val data = matchDataOf(player)

            if (data != null) {
                loseMatch(data, player)
            }

            makeSpectator(player)
        }

        onDeathOf<Mannequin> { npc, _ ->
            val data = matchDataOf(npc)

            if (data != null) {
                loseMatch(data, npc)
            }

            npc.discard()
        }
    }

    override fun participantRemoved(player: ServerPlayer) {
        super.participantRemoved(player)

        val data = matchDataOf(player)

        if (!winManager.isGameOver && data != null) {
            loseMatch(data, player)
        }
    }

    fun setupPlayerForNextMatch(ref: PlayerRef): Match? {
        val match = nextMatch(ref)

        if (match == null) {
            makeSpectator(ref)
            return null
        }

        val arena = tournamentResult!!.arenas[match] ?: error("No arena for match $match")
        val kit = kitManager[match]

        val data = initMatchData(match, arena, kit)

        val player = players().getParticipant(ref.uuid).orElse(null)

        if (player != null) {
            data.teleport(player)

            gameHandle.playerUtil.resetPlayer(player)

            kit.equip(player)

            CombatControl.get(server).setStyle(player, kit.combatStyle)

            player.setItemInHand(InteractionHand.OFF_HAND, canvas.asStack())

            canvas.addPlayer(player)

            data.players += player.uuid
        } else {
            val npc = Mannequin(EntityType.MANNEQUIN, world)
            npc.uuid = ref.uuid
            npc.customName = Component.literal(ref.name)
            npc.isCustomNameVisible = true

            @Suppress("KotlinConstantConditions")
            (npc as MannequinAccessor).invokeSetHideDescription(true)

            data.teleport(npc)

            world.addFreshEntity(npc)

            kit.equip(npc)

            data.npcs += EntityRef(npc)
        }

        return match
    }

    private fun makeSpectator(ref: PlayerRef) {
        val player = players().getParticipant(ref.uuid).orElse(null) ?: return

        makeSpectator(player)
    }

    private fun makeSpectator(player: ServerPlayer) {
        player.setGameMode(GameType.SPECTATOR)

        val nearestPlayer = players()
            .filter { !it.isSpectator && it.level() == player.level() }
            .minByOrNull { it.distanceToSqr(player) } ?: return

        player.teleportTo(nearestPlayer)
    }

    private fun nextMatch(ref: PlayerRef): Match? = tournamentResult!!.tournament.matches
        .filter { it.hasPlayer(ref) && !it.completed }
        .minByOrNull { it.round }

    @Synchronized
    private fun initMatchData(
        match: Match,
        arena: ArenaInstance,
        kit: Kit
    ): MatchData = matchData.computeIfAbsent(match) {
        MatchData(it, arena, kit, world, players())
    }

    fun matchDataOf(entity: Avatar) =
        matchData.values.find { entity in it.participants }

    fun startMatch(match: Match) {
        val data = synchronized(this) {
            val data = matchData[match] ?: return

            if (data.started) return

            data.started = true

            data
        }

        data.participants.forEach {
            // tournament state is displayed on a map in the offhand, replace it with the offhand item of the kit
            it.setItemInHand(InteractionHand.OFF_HAND, data.kit[EquipmentSlot.OFFHAND])

            pvp!!.allow(it)
        }

        data.tasks.add(runAfter(SUDDEN_DEATH_DELAY) {
            var damagePerSecond = 2f
            var timer = 0

            data.tasks.add(runEvery(1.seconds) {
                // if both participants would die at the same time though sudden death, end in draw
                if (data.participants.all { it.health <= damagePerSecond }) {
                    completeMatch(match, null)
                    return@runEvery
                }

                data.participants.forEach {
                    it.hurtServer(world, it.damageSources().magic(), damagePerSecond)
                }

                if (++timer % 10 == 0) {
                    damagePerSecond += 2f
                }
            })
        })

        data.tasks.add(runAfter(MATCH_DRAW_DELAY) {
            completeMatch(match, null)
        })
    }

    fun loseMatch(data: MatchData, loser: Avatar) {
        val ref = data.ref(loser) ?: return
        val winner = data.match.other(ref) ?: return

        completeMatch(data.match, winner)
    }

    fun completeMatch(match: Match, winner: PlayerRef?) {
        val data = synchronized(this) {
            val data = matchData[match] ?: return

            data.started = false
            matchData.remove(match)

            data
        }

        data.tasks.forEach { it.cancel() }
        data.tasks.clear()

        data.participants.forEach { pvp!!.disallow(it) }

        data.participants.filterIsInstance<ServerPlayer>().forEach { player ->
            if (winner?.uuid == player.uuid) {
                WinSequence.playWinSound(player)
            } else {
                WinSequence.playLoseSound(player)
            }

            val title = if (winner != null) {
                winner.getNameFor(player).copy().withStyle(ChatFormatting.AQUA)
            } else {
                translate("ap2.nobody")
                    .formatted(ChatFormatting.AQUA)
                    .translateFor(player)
            }

            Title.get(player).title(
                title,
                translate("game.ap2.pvp_tournament.won_match")
                    .formatted(ChatFormatting.DARK_GREEN)
                    .translateFor(player)
            )
        }

        match.complete(winner)

        // TODO respect swiss style tournament
        if (match.isFinale()) {
            if (winner != null) {
                this.data.setScore(winner, 1)

                match.other(winner)?.let {
                    setPlacementLostInMatch(it, match)
                }
            }

            winManager.complete()
            return
        }

        if (winner == null) {
            match.winnerNext?.let { checkMatchStatus(it) }
            match.loserNext?.let { checkMatchStatus(it) }
        }

        runAfter(5.seconds) {
            data.npcs.mapNotNull { it.resolve() }.forEach { it.discard() }
            data.npcs.clear()

            match.players.forEach { ref ->
                val nextMatch = setupPlayerForNextMatch(ref)

                if (nextMatch == null) {
                    setPlacementLostInMatch(ref, match)
                } else {
                    checkMatchStatus(nextMatch)
                }
            }
        }
    }

    private fun checkMatchStatus(match: Match) {
        if (match.completed) {
            // the match could have been completed by a draw in one of the child matches
            completeMatch(match, match.winner)
            return
        }

        val data = synchronized(this) { matchData[match] } ?: return

        if (data.participants.size >= 2 && !data.started) {
            // if both players are present, start the match
            startMatch(match)
        }
    }

    fun setPlacementLostInMatch(ref: PlayerRef, match: Match) {
        val maxPlacement = tournamentResult!!.tournament.matches.maxOf { it.round } + 2

        data.setScore(ref, maxPlacement - match.round)
    }
}
