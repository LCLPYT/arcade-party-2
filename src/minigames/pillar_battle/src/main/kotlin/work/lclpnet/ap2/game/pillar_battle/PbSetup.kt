package work.lclpnet.ap2.game.pillar_battle

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import org.json.JSONObject
import org.slf4j.Logger
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.world.CircleStructureGenerator
import work.lclpnet.gaco.ds.IndexedSet
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.util.PositionRotation
import work.lclpnet.kibu.mc.BlockStateAdapter
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter
import work.lclpnet.kibu.schematic.SchematicFormats
import work.lclpnet.kibu.structure.BlockStructure
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors
import kotlin.math.atan2

class PbSetup(
    private val world: ServerLevel,
    private val map: GameMap,
    private val logger: Logger
) {

    private var availablePillars: IndexedSet<PillarInfo>? = null

    fun load(): CompletableFuture<Void> =
        loadAvailablePillars().thenAccept { availablePillars = it }

    private fun loadAvailablePillars(): CompletableFuture<IndexedSet<PillarInfo>> =
        CompletableFuture.supplyAsync {
            val infos = IndexedSet<PillarInfo>()
            val dir = getWorldDirectory(world).resolve("schematics")
            val pillarConfig: JSONObject = map.requireProperty("pillars")

            for (name in pillarConfig.keySet()) {
                val cfg = pillarConfig.optJSONObject(name) ?: run {
                    logger.warn("Unexpected config value for pillar {}; object expected", name)
                    continue
                }

                val path = dir.resolve("$name.schem")

                if (!Files.isRegularFile(path)) {
                    logger.error("Pillar schematic file {} not found", path)
                    continue
                }

                val info = loadPillar(path, cfg) ?: continue
                infos.add(info)
            }

            infos
        }

    private fun loadPillar(path: Path, cfg: JSONObject): PillarInfo? {
        val spawnTuple = cfg.optJSONArray("spawn") ?: run {
            logger.error("Pillar spawn is not configured for pillar {}", path)
            return null
        }

        val spawn = MapUtil.readBlockPos(spawnTuple)
        val reader = SchematicFormats.SPONGE_V2.reader()
        val adapter: BlockStateAdapter = FabricBlockStateAdapter.getInstance()

        val struct = try {
            Files.newInputStream(path).use { reader.read(it, adapter) }
        } catch (e: IOException) {
            logger.error("Failed to read pillar {}", path, e)
            return null
        }

        return PillarInfo(struct, spawn)
    }

    private fun getWorldDirectory(world: ServerLevel): Path {
        val session = (world.server as MinecraftServerAccessor).storageSource
        return session.getDimensionPath(world.dimension())
    }

    fun placePillars(participants: Participants, random: Random): PlacementResult? {
        val assignment = assignPillars(participants, random) ?: return null
        val structs = assignment.structs

        var minRadius = CircleStructureGenerator.calculateRadius(structs.size, 9.0)

        (map.getProperty("pillar-min-radius") as? Number)?.toInt()?.let {
            minRadius = maxOf(minRadius, it)
        }

        val offsetResult = CircleStructureGenerator.generateHorizontalOffsetsRadius(structs, minRadius)
        val center = MapUtil.readBlockPos(map.requireProperty("pillar-center"))

        val mapping = HashMap<UUID, PositionRotation>()
        val spawns = assignment.spawns
        val playerIds = assignment.playerIds

        CircleStructureGenerator.placeStructures(structs, world, offsetResult.offsets()) { i, _, offset ->
            val pillarSpawn = spawns[i]
            val pos = center.offset(offset.x(), -pillarSpawn.y, offset.z())
            val spawn = pos.offset(pillarSpawn)

            val yaw = Math.toDegrees(atan2(offset.x().toDouble(), -offset.z().toDouble())).toFloat()

            mapping[playerIds[i]] = PositionRotation(spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5, yaw, 0f)

            pos
        }

        return PlacementResult(mapping, offsetResult.radius(), center)
    }

    private fun assignPillars(participants: Participants, random: Random): Assignment? {
        val available = availablePillars

        if (available.isNullOrEmpty()) {
            logger.error("No pillars available")
            return null
        }

        val playerCount = participants.count()

        val structs = ArrayList<BlockStructure>(playerCount)
        val spawns = ArrayList<BlockPos>(playerCount)
        val playerIds = ArrayList<UUID>(playerCount)

        val pool = ArrayList<PillarInfo>(available.size)

        val playerOrder = participants.stream().collect(Collectors.toCollection(::ArrayList))
        Collections.shuffle(playerOrder, random)

        for (player: ServerPlayer in playerOrder) {
            playerIds.add(player.uuid)

            if (pool.isEmpty()) {
                pool.addAll(available)
            }

            val info = pool.removeAt(random.nextInt(pool.size))
            structs.add(info.struct)
            spawns.add(info.spawn)
        }

        return Assignment(structs, spawns, playerIds)
    }

    data class PillarInfo(val struct: BlockStructure, val spawn: BlockPos)
    data class Assignment(val structs: List<BlockStructure>, val spawns: List<BlockPos>, val playerIds: List<UUID>)
    data class PlacementResult(val spawns: Map<UUID, PositionRotation>, val radius: Int, val center: BlockPos)
}
