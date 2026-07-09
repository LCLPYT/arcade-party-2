package work.lclpnet.ap2.game.minefield

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.phys.shapes.CollisionContext
import org.json.JSONArray
import work.lclpnet.ap2.api.util.world.BlockPredicate
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.util.openRandomMap
import work.lclpnet.ap2.game.util.useDebugController
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner
import work.lclpnet.ap2.impl.util.world.CardinalAdjacentBlocks
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.gaco.ds.StructureMask
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.util.BlockStateUtils
import work.lclpnet.kibu.util.math.Matrix3i
import kotlin.math.sqrt
import kotlin.random.Random

enum class ScanningAlgorithm(val id: String) {
    FLOOD_FILL_WALKABLE("flood_fill_walkable"),
    FLOOD_FILL_3D("flood_fill_3d");

    companion object {
        fun parse(id: String): ScanningAlgorithm? =
            entries.find { it.id == id }
    }
}

class MinefieldFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        val scanShape = MapUtil.readShape(map, "scan-shape")
        val startAnchorPos = MapUtil.readVec3d(map.properties.getJSONArray("start-anchor-pos"))

        val spawnShape = MapUtil.readShape(map, "spawn-shape")
        val goalShape = MapUtil.readShape(map, "goal-shape")
        val spawnYaw = MapUtil.readAngle(map.properties.optNumber("spawn-yaw", 0))
        val goalDistance = sqrt(goalShape.bounds().squaredDistanceTo(startAnchorPos))

        val defaultPressurePlates = JSONArray()
        defaultPressurePlates.put(BlockStateUtils.stringify(Blocks.STONE_PRESSURE_PLATE.defaultBlockState()))

        val pressurePlatesJson = map.properties.optJSONArray("pressure-plates", defaultPressurePlates)
        val pressurePlates = mutableSetOf<BlockState>()

        MapUtil.readBlockStates(pressurePlatesJson, pressurePlates, handle.logger)

        placeMines(level, map, scanShape, pressurePlates, spawnShape, goalShape)

        level.gameRules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, level.server)

        return MinefieldInstance(handle, level, map, spawnShape, goalShape, spawnYaw, goalDistance)
    }

    private fun placeMines(
        level: ServerLevel,
        map: GameMap,
        scanShape: BlockShape,
        pressurePlates: Set<BlockState>,
        spawnShape: BlockShape,
        goalShape: BlockShape,
    ) {
        val scanPositions = map.properties.getJSONArray("scan-positions")

        val scannerStarts = scanPositions
            .filterIsInstance<JSONArray>()
            .mapNotNull { MapUtil.readBlockPos(it) }
            .toSet()

        check(scannerStarts.isNotEmpty()) { "No valid scanner starts given" }

        val minPos = scanShape.bounds().min()
        val mineDensity = map.properties.optNumber("mine-density", 0.55f).toFloat()

        val debugVoxelShape = if (DEBUG_PRESSURE_PLATE_POSITIONS) StructureMask.createEmpty(scanShape.bounds()) else null

        val replaceNoCollisionBlocks = map.properties.optBoolean("replace_no_collision_blocks", true)

        scanWorld(level, map, scanShape, scannerStarts, spawnShape, goalShape).forEach {
            if (!level.getFluidState(it).isEmpty) return@forEach

            if (!replaceNoCollisionBlocks && !level.getBlockState(it).isAir) return@forEach

            debugVoxelShape?.setVoxelAt(it.x - minPos.x, it.y - minPos.y, it.z - minPos.z, true)

            if (Random.nextFloat() > mineDensity) return@forEach

            level.setBlock(it, pressurePlates.random(), Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_SUPPRESS_DROPS)
        }

        if (debugVoxelShape != null) {
            visualizeScanLocations(debugVoxelShape, level, minPos)
        }
    }

    private fun scanWorld(
        level: ServerLevel,
        map: GameMap,
        scanShape: BlockShape,
        scannerStarts: Set<BlockPos>,
        spawnShape: BlockShape,
        goalShape: BlockShape,
    ): Iterator<BlockPos> {
        val scanningAlgoId = map.properties.optString("scanning-algo", "")
        val scanningAlgo = ScanningAlgorithm.parse(scanningAlgoId) ?: ScanningAlgorithm.FLOOD_FILL_WALKABLE

        return when (scanningAlgo) {
            ScanningAlgorithm.FLOOD_FILL_WALKABLE -> {
                val predicate = BlockPredicate.and({
                    scanShape.contains(it)
                            && !spawnShape.contains(it)
                            && !goalShape.contains(it)
                            && level.getBlockState(it).getCollisionShape(level, it, CollisionContext.empty()).isEmpty
                }, WalkableBlockPredicate(level))

                val adjacentBlocks = SimpleAdjacentBlocks(predicate, 1)

                BfsWorldScanner(adjacentBlocks).scan(scannerStarts)
            }

            ScanningAlgorithm.FLOOD_FILL_3D -> {
                val walkable = WalkableBlockPredicate(level)

                // 3D flood fill from the scan starts through open space (air / slim collision),
                // bounded by the spawn box -> the reachable play area, excluding sealed cavities.
                val adjacentBlocks = CardinalAdjacentBlocks { pos ->
                    scanShape.contains(pos) && WalkableBlockPredicate.isPassable(level, pos)
                }

                iterator {
                    for (pos in BfsWorldScanner(adjacentBlocks).scan(scannerStarts)) {
                        if (walkable.test(pos)) {
                            yield(pos)
                        }
                    }
                }
            }
        }
    }

    private fun visualizeScanLocations(
        debugVoxelShape: StructureMask,
        level: ServerLevel,
        minPos: BlockPos
    ) {
        val boxes = debugVoxelShape.greedyMeshing().generateBoxes()
        val debugController = useDebugController(level)

        debugController.visualizeBoxes(
            boxes,
            minPos,
            Matrix3i.IDENTITY,
            Blocks.STAINED_GLASS.blue.defaultBlockState()
        )
    }
}
