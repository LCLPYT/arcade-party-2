package work.lclpnet.ap2.impl.util.world

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.math.MathUtil
import work.lclpnet.ap2.impl.util.structure.StructureUtil
import work.lclpnet.ap2.util.MinecraftDispatcher
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter
import work.lclpnet.kibu.schematic.SchematicFormats
import work.lclpnet.kibu.structure.BlockStructure
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * A generator for room structures were the rooms are stacked in a particular direction.
 * Can be used to generate rooms for game participants.
 * @param T The room data type.
 */
class StackedRoomGenerator<T>(
    private val world: ServerLevel,
    private val map: GameMap,
    private val coordinates: Coordinates,
    private val roomFactory: RoomFactory<T>
) {

    /**
     * Generates a room for each participant and returns a mapping of participant uuid -> room
     * @param participants The participants.
     * @return The room mapping.
     */
    suspend fun generate(participants: Participants): Result<T> {
        val schematicName = map.requireProperty<String>("room-schematic")

        val session = (world.server as MinecraftServerAccessor).storageSource
        val storage: Path = session.getDimensionPath(world.dimension())

        val path = storage.resolve("schematics").resolve(schematicName)

        val structure = readSchematic(path)

        val data = withContext(MinecraftDispatcher(world.server)) {
            placeStructures(participants.count(), structure)
        }

        val mapping = assignRooms(participants, data)

        return Result(mapping, data)
    }

    private suspend fun readSchematic(path: Path): BlockStructure = withContext(Dispatchers.IO) {
        if (!Files.isRegularFile(path)) {
            throw IllegalStateException("Schematic does not exist at $path")
        }

        val adapter = FabricBlockStateAdapter.getInstance()

        try {
            Files.newInputStream(path).use { input ->
                SchematicFormats.SPONGE_V2.reader().read(input, adapter)
            }
        } catch (e: IOException) {
            throw RuntimeException("Failed to read schematic", e)
        }
    }

    private fun placeStructures(roomCount: Int, structure: BlockStructure): GeneratorData {
        val roomStart = MapUtil.readBlockPos(map.requireProperty("room-start"))

        val roomDirection = MathUtil.normalize(MapUtil.readBlockPos(map.requireProperty("room-direction")))

        var spacing = -1

        val spacingProp = map.getProperty<Any?>("room-spacing")

        if (spacingProp is Number) {
            spacing = spacingProp.toInt()
        }

        val width = structure.width
        val height = structure.height
        val length = structure.length

        val roomOffset = Vec3i(
            roomDirection.x * (width + spacing),
            roomDirection.y * (height + spacing),
            roomDirection.z * (length + spacing)
        )

        val pos = BlockPos.MutableBlockPos()

        for (i in 0 until roomCount) {
            pos.set(
                roomStart.x + i * roomOffset.x,
                roomStart.y + i * roomOffset.y,
                roomStart.z + i * roomOffset.z
            )

            StructureUtil.placeStructureFast(structure, world, pos)
        }

        return GeneratorData(roomStart, roomOffset, structure)
    }

    private fun assignRooms(participants: Participants, data: GeneratorData): Map<UUID, T> {
        var spawnOffset: Vec3i = MapUtil.readBlockPos(map.requireProperty("room-player-spawn"))
        val yaw = MapUtil.readAngle(map.requireProperty("room-player-yaw"))

        if (coordinates == Coordinates.ABSOLUTE) {
            // spawnOffset is given in absolute schematic coordinates => relativize them
            val origin = data.structure.origin
            spawnOffset = spawnOffset.offset(-origin.x, -origin.y, -origin.z)
        }

        val rooms = HashMap<UUID, T>()

        val roomStart = data.roomStart
        val roomOffset = data.roomOffset

        var i = 0

        for (player in participants) {
            val roomPos = roomStart.offset(roomOffset.multiply(i++))
            val spawn = roomPos.offset(spawnOffset)

            val room = roomFactory.createRoom(roomPos, spawn, yaw, data.structure)

            rooms[player.uuid] = room
        }

        return rooms
    }

    /**
     * A factory to create room instances.
     * @param T The room data type.
     */
    fun interface RoomFactory<T> {

        /**
         * Create a room instance.
         * @param pos The room position in the world, e.g. where the minimum position of the room is inside the world.
         * @param spawn The room spawn position, in absolute world coordinates.
         * @param spawnYaw The spawn yaw of the room.
         * @param structure The structure the room consists of.
         * @return A room instance of the specified data type.
         */
        fun createRoom(pos: BlockPos, spawn: BlockPos, spawnYaw: Float, structure: BlockStructure): T
    }

    data class GeneratorData(val roomStart: BlockPos, val roomOffset: Vec3i, val structure: BlockStructure)

    data class Result<T>(val rooms: Map<UUID, T>, val data: GeneratorData)

    enum class Coordinates {
        ABSOLUTE,
        RELATIVE
    }
}
