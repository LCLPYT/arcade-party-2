package work.lclpnet.ap2.game.speed_builders.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.breeze.Breeze
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.Logger
import work.lclpnet.ap2.ext.mc.setAttribute
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.game.speed_builders.data.*
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.structure.StructureUtil
import work.lclpnet.ap2.impl.util.world.CircleStructureGenerator
import work.lclpnet.gaco.math.AffineIntMatrix
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter
import work.lclpnet.kibu.schematic.SchematicFormats
import work.lclpnet.kibu.structure.BlockStructure
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt

private const val ISLAND_SPACING = 2.0
private const val SPAWN_Y = 64

class SbSetup(private val random: Random, private val logger: Logger) {

    private var modules: List<SbModule>? = null
    private var islandProtos: List<SbIslandProto>? = null
    private var centerIsland: CenterIsland? = null
    private var aelosId: UUID? = null

    fun setup(map: GameMap, world: ServerLevel): CompletableFuture<Void> {
        return loadAvailableIslands(map, world)
            .thenApply { islands ->
                islands.toList().also { islandProtos = it }
            }
            .thenComposeAsync { islands -> loadModules(world, buildAreaDimensions(islands)) }
            .thenAccept { mods -> this.modules = mods.toList() }
            .thenComposeAsync { loadCenterIsland(map, world) }
            .thenAccept { ci -> this.centerIsland = ci }
    }

    private fun buildAreaDimensions(islands: List<SbIslandProto>): Vec3i {
        if (islands.isEmpty()) return Vec3i.ZERO
        val buildArea = islands.first().data.buildArea
        return Vec3i(buildArea.width(), buildArea.height(), buildArea.length())
    }

    fun createIslands(participants: Participants, world: ServerLevel): Map<UUID, SbIsland> {
        val protos = checkNotNull(islandProtos) { "Island prototypes must be loaded" }
        checkNotNull(centerIsland) { "Center island must be loaded" }

        if (protos.isEmpty()) throw IllegalStateException("No island prototypes available")

        placeCenterIsland(world)

        val islandData = mutableListOf<SbIslandData>()
        val structures = mutableListOf<BlockStructure>()
        val players = mutableListOf<UUID>()

        for (player in participants) {
            players.add(player.uuid)
            val island = protos[random.nextInt(protos.size)]
            islandData.add(island.data)
            structures.add(island.structure)
        }

        val minRadius = getMinRadius(structures)
        val offsets = CircleStructureGenerator.generateHorizontalOffsets(structures, minRadius)

        val islandMapping = mutableMapOf<UUID, SbIsland>()

        CircleStructureGenerator.placeStructures(structures, world, offsets) { i, structure, circleOffset ->
            val data = islandData[i]
            val absSpawn = data.spawn
            val kibuOrigin = structure.origin
            val origin = BlockPos(kibuOrigin.x, kibuOrigin.y, kibuOrigin.z)
            val spawn = absSpawn.subtract(origin)

            val x = circleOffset.x()
            val y = SPAWN_Y - spawn.y
            val z = circleOffset.z()

            val pos = BlockPos(x, y, z)
            val bounds = StructureUtil.getBounds(structure).transform(AffineIntMatrix.makeTranslation(x, y, z))

            val island = SbIsland(data, origin, pos, bounds, logger)
            islandMapping[players[i]] = island

            pos
        }

        return islandMapping
    }

    private fun placeCenterIsland(world: ServerLevel) {
        val ci = centerIsland!!
        val structure = ci.structure

        val structSpawn = ci.spawn
        val origin = structure.origin

        val pos = BlockPos(
            -structure.width / 2,
            SPAWN_Y - structSpawn.y + origin.y,
            -structure.length / 2
        )

        StructureUtil.placeStructureFast(structure, world, pos)

        val spawn = structSpawn.offset(
            pos.x - origin.x,
            pos.y - origin.y,
            pos.z - origin.z
        )

        val breeze = Breeze(EntityTypes.BREEZE, world)
        breeze.setPosRaw(spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5)
        breeze.isNoAi = true
        breeze.setPersistenceRequired()
        breeze.yRot = 0f

        breeze.setAttribute(Attributes.SCALE, 10.0)

        world.addFreshEntity(breeze)

        aelosId = breeze.uuid
    }

    private fun getMinRadius(structures: List<BlockStructure>): Int {
        val structure = centerIsland!!.structure
        val centerIslandRadius = sqrt(structure.width.toDouble().pow(2) + structure.length.toDouble().pow(2)) * 0.5
        val largestIslandRadius = CircleStructureGenerator.computeLargestTangentDistance(structures) * 0.5
        return ceil(centerIslandRadius + ISLAND_SPACING + largestIslandRadius).toInt()
    }

    fun getModules(): List<SbModule> = checkNotNull(modules) { "Modules not loaded yet" }

    fun getAelosId(): UUID = checkNotNull(aelosId) { "Aelos not created yet" }

    private fun loadAvailableIslands(map: GameMap, world: ServerLevel): CompletableFuture<Set<SbIslandProto>> {
        val islandsArray = map.requireProperty<Any>("islands") as JSONArray

        return CompletableFuture.supplyAsync {
            val dir = getWorldDirectory(world).resolve("schematics").resolve("island")

            if (!Files.isDirectory(dir)) {
                logger.error("Directory {} does not exist", dir)
                return@supplyAsync emptySet()
            }

            val islands = mutableSetOf<SbIslandProto>()
            var width = -1; var height = -1; var length = -1

            for (obj in islandsArray) {
                if (obj !is JSONObject) {
                    logger.warn("Invalid islands array element, skipping it...")
                    continue
                }

                val data = try {
                    sbIslandDataFromJson(obj)
                } catch (e: JSONException) {
                    logger.warn("Failed to read island from json", e)
                    continue
                }

                val buildArea = data.buildArea

                when {
                    width == -1 && height == -1 && length == -1 -> {
                        width = buildArea.width()
                        height = buildArea.height()
                        length = buildArea.length()
                    }
                    width != buildArea.width() || length != buildArea.length() -> {
                        logger.warn("Incompatible build area dimensions. The first island defined {}x{} but island '{}' defines {}x{}",
                            width, length, data.id, buildArea.width(), buildArea.length())
                        continue
                    }
                    buildArea.height() < height -> {
                        logger.warn("Build area height of island {} is too small: {}. The first island defined height {}", data.id, buildArea.height(), height)
                        continue
                    }
                }

                val path = dir.resolve("${data.id}.schem")
                val structure = loadSchematic(path) ?: continue

                islands.add(SbIslandProto(data, structure))
            }

            islands
        }
    }

    private fun loadCenterIsland(map: GameMap, world: ServerLevel): CompletableFuture<CenterIsland?> {
        val json = map.requireProperty<Any>("center-island") as JSONObject
        val id = json.getString("id")
        val path = getWorldDirectory(world).resolve("schematics").resolve("$id.schem")
        val spawn = MapUtil.readBlockPos(json.getJSONArray("spawn"))

        return CompletableFuture.supplyAsync {
            val structure = loadSchematic(path) ?: return@supplyAsync null
            CenterIsland(structure, spawn)
        }
    }

    private fun loadModules(world: ServerLevel, dimensions: Vec3i): CompletableFuture<Set<SbModule>> {
        val dir = getWorldDirectory(world).resolve("schematics").resolve("module")

        return CompletableFuture.supplyAsync {
            try {
                Files.list(dir).use { files ->
                    files.iterator().asSequence()
                        .filter { it.fileName.toString().endsWith(".schem") }
                        .filter { Files.isRegularFile(it) }
                        .mapNotNull { loadModule(it) }
                        .filter { module ->
                            if (!module.isCompatibleWith(dimensions)) {
                                val s = module.structure
                                logger.warn("Dimensions of module {} ({}x{}x{}) are not compatible with the island dimensions ({}x{}x{})",
                                    module.id, s.width, s.height, s.length,
                                    dimensions.x, dimensions.y, dimensions.z)
                                false
                            } else true
                        }
                        .toSet()
                }
            } catch (e: IOException) {
                logger.error("Failed to read module directory", e)
                emptySet()
            }
        }
    }

    private fun loadModule(path: Path): SbModule? {
        val structure = loadSchematic(path) ?: return null

        var id = path.fileName.toString()
        val idx = id.lastIndexOf('.')
        if (idx >= 0) id = id.substring(0, idx)

        return SbModule(id, structure)
    }

    private fun loadSchematic(path: Path): BlockStructure? {
        val reader = SchematicFormats.SPONGE_V2.reader()
        val adapter = FabricBlockStateAdapter.getInstance()

        return try {
            Files.newInputStream(path).use { reader.read(it, adapter) }
        } catch (e: IOException) {
            logger.error("Failed to load schematic {}", path, e)
            null
        }
    }

    private fun getWorldDirectory(world: ServerLevel): Path {
        val session = (world.server as MinecraftServerAccessor).storageSource
        return session.getDimensionPath(world.dimension())
    }
}
