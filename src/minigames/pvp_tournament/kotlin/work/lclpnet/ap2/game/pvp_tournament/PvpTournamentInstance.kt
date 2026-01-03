package work.lclpnet.ap2.game.pvp_tournament

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.decoration.Mannequin
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.ext.logger
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.ext.mc.teleport
import work.lclpnet.ap2.game.pvp_tournament.util.KITS_1V1
import work.lclpnet.ap2.game.pvp_tournament.util.KitManager
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.ap2.util.PvpBehavior
import work.lclpnet.lobby.game.map.GameMap
import java.util.*
import java.util.concurrent.CompletableFuture

enum class TournamentVariant {
    SINGLE_ELIMINATION,
    SWISS_STYLE,
}

const val DEBUG_FILL_WITH_NPC = true

class PvpTournamentInstance(gameHandle: MiniGameHandle) : FFAGameInstance(gameHandle), MapBootstrap {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val data = IntScoreDataContainer(PlayerRef::create)

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
                add(PlayerRef(UUID.randomUUID(), "NPC #$it"))
            }
        }
    } else {
        players().map { PlayerRef.create(it) }
    }

    val kitManager = KitManager(KITS_1V1)

    var tournamentResult: TournamentResult? = null

    override fun getData() = data

    override fun createWorldBootstrap(
        world: ServerLevel,
        map: GameMap
    ): CompletableFuture<Void> {
        val setup = TournamentSetup(logger, map, playerRefs) { path ->
            schematicBlocking(assetPath(path))
        }

        return scope.future {
            setup.setup(TournamentVariant.SINGLE_ELIMINATION).also { tournamentResult = it }
        }.thenCompose { result ->
            gameHandle.server.submit {
                setup.placeArenas(world, result.arenas.values)
            }
        }
    }

    override fun prepare() {
        playerRefs.forEach { ref ->
            val match = tournamentResult!!.tournament.matches
                .filter { it.hasPlayer(ref) }
                .minBy { it.round }

            val arena = tournamentResult!!.arenas[match] ?: error("No arena for match $match")
            val spawn = arena.spawns[match.participant(ref)]
            val kit = kitManager[match]

            val player = players().getParticipant(ref.uuid).orElse(null)

            if (player != null) {
                player.teleport(spawn)

                movementBlocker.disableMovement(player)

                kit.equip(player)
            } else {
                val npc = Mannequin(EntityType.MANNEQUIN, world)

                npc.teleport(world, spawn)

                world.addFreshEntity(npc)

                kit.equip(npc)
            }
        }
    }

    override fun go() {
        PvpBehavior(gameHandle, world).configure()

        players().forEach { movementBlocker.enableMovement(it) }
    }
}
