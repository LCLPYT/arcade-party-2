package work.lclpnet.ap2.game.fine_tuning

import net.minecraft.server.level.ServerLevel
import org.json.JSONArray
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.game.util.finaleCompatibleScoreContainer
import work.lclpnet.ap2.game.util.useAnnouncer
import work.lclpnet.ap2.game.util.useSurvivalMode
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.game.map.GameMap
import java.util.*

const val MELODY_COUNT = 2

val PitchChanges = Stat("pitch_changes", 0)
val Probes = Stat("probes", 0)
val Replays = Stat("replays", 0)
val MelodiesCompleted = Stat("melodies_completed", 0)
val CorrectNotes = Stat("correct_notes", 0)

class FineTuningInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    private val setup: FineTuningSetup,
) : FFAGameInstance(gameHandle, level, map) {

    override val data = finaleCompatibleScoreContainer(gameHandle, PlayerRef::create)
    private val stats = createStats(data, PitchChanges, Probes, Replays, MelodiesCompleted, CorrectNotes)
    private val announcer = useAnnouncer()
    private lateinit var tuningPhase: TuningPhase

    init {
        useSurvivalMode()
    }

    override fun prepare() {
        val json: JSONArray = map.requireProperty("room-note-blocks")
        val noteBlockLocations = FineTuningSetup.readNoteBlockLocations(json, gameHandle.logger)
        setup.teleportParticipants(noteBlockLocations)

        val rooms: Map<UUID, FineTuningRoom> = setup.rooms

        tuningPhase = TuningPhase(gameHandle, rooms, data, stats, ::startStagePhase, level, announcer)
        tuningPhase.init()
        tuningPhase.giveBooks()
    }

    override fun go() {
        tuningPhase.beginListen()
    }

    private fun startStagePhase() {
        tuningPhase.unload()

        val stagePhase = StagePhase(gameHandle, tuningPhase.records, map, level, winManager)
        stagePhase.beginStage()
    }
}
