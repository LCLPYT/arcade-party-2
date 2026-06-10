package work.lclpnet.ap2.game.minefield

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
import work.lclpnet.ap2.impl.game.GameCommons
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate
import work.lclpnet.gaco.ds.StructureMask
import work.lclpnet.kibu.util.BlockStateUtils
import work.lclpnet.kibu.util.math.Matrix3i
import kotlin.math.sqrt
import kotlin.random.Random

class MinefieldFactory : MiniGameFactory {
    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        val scanPositions = map.properties.getJSONArray("scan-positions")
        val mineDensity = map.properties.optNumber("mine-density", 0.55f).toFloat()
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

        val predicate = BlockPredicate.and({
            scanShape.contains(it)
                    && !spawnShape.contains(it)
                    && !goalShape.contains(it)
                    && level.getBlockState(it).getCollisionShape(level, it, CollisionContext.empty()).isEmpty
        }, WalkableBlockPredicate(level))

        val scanner = BfsWorldScanner(SimpleAdjacentBlocks(predicate, 1))
        val debugVoxelShape = if (DEBUG_PRESSURE_PLATE_POSITIONS) StructureMask.createEmpty(scanShape.bounds()) else null
        val minPos = scanShape.bounds().min()

        for (elem in scanPositions) {
            if (elem !is JSONArray) continue

            val start = MapUtil.readBlockPos(elem)

            scanner.scan(start).forEach {
                debugVoxelShape?.setVoxelAt(it.x - minPos.x, it.y - minPos.y, it.z - minPos.z, true)

                if (Random.nextFloat() > mineDensity) return@forEach

                level.setBlock(it, pressurePlates.random(), Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_SUPPRESS_DROPS)
            }
        }

        if (debugVoxelShape != null) {
            val boxes = debugVoxelShape.greedyMeshing().generateBoxes()

            GameCommons(handle, map, level).debugController().visualizeBoxes(
                boxes,
                minPos,
                Matrix3i.IDENTITY,
                Blocks.BLUE_STAINED_GLASS.defaultBlockState()
            )
        }

        level.gameRules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, level.server)

        return MinefieldInstance(handle, level, map, spawnShape, goalShape, spawnYaw, goalDistance)
    }
}
