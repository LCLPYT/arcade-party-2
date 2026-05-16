package work.lclpnet.ap2.game.fine_tuning

import com.mojang.math.Transformation
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.json.JSONArray
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.game.fine_tuning.melody.FakeNoteBlockPlayer
import work.lclpnet.ap2.game.fine_tuning.melody.Melody
import work.lclpnet.ap2.game.fine_tuning.melody.Note
import work.lclpnet.ap2.game.fine_tuning.melody.PlayMelodyTask
import work.lclpnet.ap2.impl.game.WinManager
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.ColorUtil
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.title.Title
import java.util.*
import kotlin.math.abs
import kotlin.math.sign

class StagePhase(
    private val gameHandle: MiniGameHandle,
    private val records: MelodyRecords,
    private val map: GameMap,
    private val world: ServerLevel,
    private val winManager: WinManager<ServerPlayer, PlayerRef>
) {
    private val movementBlocker = SimpleMovementBlocker(gameHandle.rootScheduler).also {
        it.setModifySpeedAttribute(false)
    }
    private val displays = HashSet<UUID>()
    private lateinit var presenterPos: BlockPos
    private var presenterYaw = 0f
    private lateinit var nbPlayer: FakeNoteBlockPlayer
    private var melodyNumber = 0

    fun beginStage() {
        val server = gameHandle.server
        val worldFacade = gameHandle.worldFacade
        val playerUtil = gameHandle.playerUtil

        playerUtil.setDefaultGameMode(GameType.ADVENTURE)

        for (player in PlayerLookup.all(server)) {
            playerUtil.resetPlayer(player)
            worldFacade.teleport(player)
        }

        readStageProps()
        movementBlocker.init(gameHandle.hooks)
        gameHandle.scheduler.timeout(Ticks.seconds(5)) { ->
            beginSongPresentation()
        }
    }

    private fun readStageProps() {
        presenterPos = MapUtil.readBlockPos(map.requireProperty("presenter-pos"))
        presenterYaw = MapUtil.readAngle(map.requireProperty("presenter-yaw"))

        val json: JSONArray = map.requireProperty("presenter-note-blocks")
        val presenterNoteBlocks = FineTuningSetup.readNoteBlockLocations(json, gameHandle.logger)

        val notes = IntArray(presenterNoteBlocks.size) { Note.FIS3.ordinal }
        val instruments = Array(presenterNoteBlocks.size) { NoteBlockInstrument.HARP }

        nbPlayer = FakeNoteBlockPlayer(presenterNoteBlocks, notes, instruments)
    }

    private fun beginSongPresentation() {
        val server = gameHandle.server
        val translations = gameHandle.translations

        SoundHelper.playSound(server, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.RECORDS, 0.5f, 0f)

        translations.translateText("game.ap2.fine_tuning.presentation")
            .formatted(ChatFormatting.DARK_GREEN)
            .acceptEach(PlayerLookup.all(server)) { player, text ->
                Title.get(player).title(text, Component.empty(), 5, 30, 5)
            }

        gameHandle.scheduler.timeout(::presentNextMelody, 40)
    }

    private fun presentNextMelody() {
        val server = gameHandle.server
        val translations = gameHandle.translations

        SoundHelper.playSound(server, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.RECORDS, 0.5f, 0f)

        translations.translateText(
            "game.ap2.fine_tuning.present_melody",
            work.lclpnet.kibu.translate.text.FormatWrapper.styled("#${melodyNumber + 1}", ChatFormatting.YELLOW)
        ).formatted(ChatFormatting.AQUA)
            .acceptEach(PlayerLookup.all(server)) { player, text ->
                Title.get(player).title(text, Component.empty(), 5, 30, 5)
            }

        gameHandle.scheduler.timeout(::playOriginalMelody, 40)
    }

    private fun playOriginalMelody() {
        val melody = records.getMelody(melodyNumber)
        playMelody(melody, IntArray(0), ::beginBestMelody)
    }

    private fun playMelody(melody: Melody, offsets: IntArray, onDone: Runnable) {
        val server = gameHandle.server
        nbPlayer.setMelody(melody)

        val melodyPlayer = PlayMelodyTask({ note ->
            for (player in PlayerLookup.all(server)) {
                nbPlayer.playAtPlayerPos(player, note)
            }

            if (note < 0 || note >= offsets.size) return@PlayMelodyTask

            val noteOffset = offsets[note]
            val pos = nbPlayer.getNoteBlock(note).center
            val dir = snapToAxis(presenterPos.center.subtract(pos))

            val label: Component = if (noteOffset == 0) {
                Component.literal("✅").withStyle(ChatFormatting.GREEN)
            } else {
                val error = (abs(noteOffset) - 1) / (Note.entries.size - 1).toFloat() * 2.2f
                val color = ColorUtil.lerpRgb(0xb2ef09, 0x890404, error)
                Component.literal("${if (noteOffset > 0) "+" else ""}$noteOffset").withColor(color)
            }

            val display = Display.TextDisplay(EntityType.TEXT_DISPLAY, world)
            display.setPos(pos.add(dir.scale(0.6)))
            display.setText(label)
            display.setBackgroundColor(0)
            display.setTransformation(Transformation(Matrix4f()
                .scale(2f)
                .rotateTowards(dir.x().toFloat(), dir.y().toFloat(), dir.z().toFloat(), 0f, 1f, 0f)
                .translate(0f, -1 / 8f, 0f)))

            world.addFreshEntity(display)
            displays.add(display.uuid)
        }, melody.notes.size)

        val scheduler = gameHandle.scheduler

        scheduler.interval(melodyPlayer, 1).whenComplete {
            scheduler.timeout(Ticks.seconds(2)) { ->
                for (id in displays) {
                    world.getEntity(id)?.discard()
                }
                displays.clear()
                onDone.run()
            }
        }
    }

    private fun snapToAxis(dir: Vec3): Vec3 {
        val ax = abs(dir.x())
        val ay = abs(dir.y())
        val az = abs(dir.z())

        return when {
            ax > ay && ax > az -> Vec3(dir.x().sign, 0.0, 0.0)
            az > ay -> Vec3(0.0, 0.0, dir.z().sign)
            else -> Vec3(0.0, dir.y().sign, 0.0)
        }
    }

    private fun beginBestMelody() {
        val server = gameHandle.server
        val translations = gameHandle.translations

        SoundHelper.playSound(server, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.RECORDS, 0.5f, 0f)

        translations.translateText("game.ap2.fine_tuning.best_was")
            .formatted(ChatFormatting.GREEN)
            .acceptEach(PlayerLookup.all(server)) { player, text ->
                Title.get(player).title(Component.empty(), text, 5, 50, 0)
            }

        gameHandle.scheduler.timeout(::announceBest, 55)
    }

    private fun announceBest() {
        val bestMelody = records.getBestMelody(melodyNumber)
        val bestRef = bestMelody.playerRef
        val name: MutableComponent = Component.literal(bestRef.name()).withStyle(ChatFormatting.GREEN)

        val server = gameHandle.server
        SoundHelper.playSound(server, SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.NEUTRAL, 0.5f, 1f)

        val player = announcePlayerAndGet(server, name, bestRef)
        val worldFacade = gameHandle.worldFacade

        gameHandle.scheduler.timeout(40) { ->
            playMelody(bestMelody.melody, bestMelody.offsets) {
                if (player != null) {
                    movementBlocker.enableMovement(player)
                    worldFacade.teleport(player)
                }
                beginWorstMelody()
            }
        }
    }

    private fun beginWorstMelody() {
        val server = gameHandle.server
        val translations = gameHandle.translations

        SoundHelper.playSound(server, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.RECORDS, 0.5f, 0f)

        translations.translateText("game.ap2.fine_tuning.worst_was")
            .formatted(ChatFormatting.RED)
            .acceptEach(PlayerLookup.all(server)) { player, text ->
                Title.get(player).title(Component.empty(), text, 5, 30, 5)
            }

        gameHandle.scheduler.timeout(::announceWorst, 40)
    }

    private fun announceWorst() {
        val worstMelody = records.getWorstMelody(melodyNumber)
        val worstRef = worstMelody.playerRef
        val name: MutableComponent = Component.literal(worstRef.name()).withStyle(ChatFormatting.RED)

        val server = gameHandle.server
        SoundHelper.playSound(server, SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.NEUTRAL, 0.5f, 0f)

        val player = announcePlayerAndGet(server, name, worstRef)
        val worldFacade = gameHandle.worldFacade

        gameHandle.scheduler.timeout(40) { ->
            playMelody(worstMelody.melody, worstMelody.offsets) {
                if (player != null) {
                    movementBlocker.enableMovement(player)
                    worldFacade.teleport(player)
                }

                if (++melodyNumber == MELODY_COUNT) {
                    winManager.complete()
                } else {
                    presentNextMelody()
                }
            }
        }
    }

    private fun announcePlayerAndGet(server: net.minecraft.server.MinecraftServer, name: MutableComponent, ref: PlayerRef): ServerPlayer? {
        for (player in PlayerLookup.all(server)) {
            Title.get(player).title(name, Component.empty(), 5, 30, 5)
        }

        val player = server.playerList.getPlayer(ref.uuid()) ?: return null

        player.teleportTo(
            world,
            presenterPos.x + 0.5, presenterPos.y.toDouble(), presenterPos.z + 0.5,
            emptySet(), presenterYaw, 0f, true
        )
        movementBlocker.disableMovement(player)

        return player
    }
}
