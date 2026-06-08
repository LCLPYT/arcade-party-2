package work.lclpnet.ap2.game.fine_tuning

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import org.json.JSONArray
import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.data.DataContainers
import work.lclpnet.ap2.impl.game.data.IntDataContainer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.game.map.GameMap
import java.util.*
import java.util.concurrent.CompletableFuture

const val MELODY_COUNT = 2

val PitchChanges = Stat("pitch_changes", 0)
val Probes = Stat("probes", 0)
val Replays = Stat("replays", 0)
val MelodiesCompleted = Stat("melodies_completed", 0)
val CorrectNotes = Stat("correct_notes", 0)

class FineTuningInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : FFAGameInstance(gameHandle, level, map) {

    private val data: IntDataContainer<ServerPlayer, PlayerRef> =
        DataContainers.finaleCompatibleScoreContainer(gameHandle, PlayerRef::create)
    private val stats = createStats(data, PitchChanges, Probes, Replays, MelodiesCompleted, CorrectNotes)
    private lateinit var setup: FineTuningSetup
    private lateinit var tuningPhase: TuningPhase

    init {
        useSurvivalMode()
    }

    override fun getData(): DataContainer<ServerPlayer, PlayerRef> = data

    // TODO: migrate world bootstrap into a dedicated MiniGameFactory

    fun createWorldBootstrap(world: ServerLevel, gameMap: GameMap): CompletableFuture<Void> {
        setup = FineTuningSetup(gameHandle, gameMap, world)
        return setup.createRooms()
    }

    override fun prepare() {
        val json: JSONArray = getMap().requireProperty("room-note-blocks")
        val noteBlockLocations = FineTuningSetup.readNoteBlockLocations(json, gameHandle.logger)
        setup.teleportParticipants(noteBlockLocations)

        val rooms: Map<UUID, FineTuningRoom> = setup.rooms

        tuningPhase = TuningPhase(gameHandle, rooms, data, stats, ::startStagePhase, commons(), level)
        tuningPhase.init()
        tuningPhase.giveBooks()
    }

    override fun go() {
        tuningPhase.beginListen()
    }

    private fun startStagePhase() {
        tuningPhase.unload()

        val stagePhase = StagePhase(gameHandle, tuningPhase.records, getMap(), level, winManager)
        stagePhase.beginStage()
    }
}
