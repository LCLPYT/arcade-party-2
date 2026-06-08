package work.lclpnet.ap2.game.fine_tuning

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.entity.SignText
import org.json.JSONArray
import org.slf4j.Logger
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.world.StackedRoomGenerator
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.structure.BlockStructure
import java.util.*
import java.util.concurrent.CompletableFuture

class FineTuningSetup(
    private val gameHandle: MiniGameHandle,
    private val map: GameMap,
    private val world: ServerLevel
) {
    val rooms = HashMap<UUID, FineTuningRoom>()

    fun createRooms(): CompletableFuture<Void> {
        val generator = StackedRoomGenerator(world, map, StackedRoomGenerator.Coordinates.RELATIVE, ::createRoom)

        return generator.generate(gameHandle.participants)
            .thenApply { it.rooms() }
            .thenAccept { rooms.putAll(it) }
            .thenCompose { world.server.submit(::setupRooms) }
            .exceptionally { throwable ->
                gameHandle.logger.error("Failed to create rooms", throwable)
                null
            }
    }

    private fun setupRooms() {
        val testSignRelPos = MapUtil.readBlockPos(map.requireProperty("test-sign"))
        val participants = gameHandle.participants
        val testMsg = gameHandle.translations.translateText("game.ap2.fine_tuning.test")

        for ((uuid, room) in rooms) {
            val player: ServerPlayer = participants.getParticipant(uuid).orElse(null) ?: continue
            val testSignPos = room.pos.offset(testSignRelPos)
            val sign = world.getBlockEntity(testSignPos, BlockEntityType.SIGN).orElse(null) ?: continue

            val lines = arrayOf(Component.empty(), testMsg.translateFor(player), Component.literal("▶"), Component.empty())
            sign.setText(SignText(lines, lines, DyeColor.BLUE, false), true)

            room.testSignPos = testSignPos
        }
    }

    private fun createRoom(pos: BlockPos, spawn: BlockPos, spawnYaw: Float, @Suppress("UNUSED_PARAMETER") structure: BlockStructure): FineTuningRoom {
        return FineTuningRoom(pos, spawn, spawnYaw)
    }

    fun teleportParticipants(noteBlockLocations: Array<out Vec3i>) {
        for (player in gameHandle.participants) {
            val room = rooms[player.uuid] ?: continue
            room.setNoteBlocks(noteBlockLocations)
            room.teleport(player, world)
        }
    }

    companion object {
        fun readNoteBlockLocations(noteBlockLocations: JSONArray, logger: Logger): Array<BlockPos> {
            if (noteBlockLocations.isEmpty) throw IllegalStateException("There must be at least one note block configured")

            val locations = mutableListOf<BlockPos>()

            for (obj in noteBlockLocations) {
                if (obj !is JSONArray) {
                    logger.warn("Invalid note block location entry of type {}", obj.javaClass.simpleName)
                    continue
                }
                locations.add(MapUtil.readBlockPos(obj))
            }

            return locations.toTypedArray()
        }
    }
}
