package work.lclpnet.ap2.game.splashy_dropper.data

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.gaco.ds.IndexedSet
import work.lclpnet.gaco.ds.WeightedList
import work.lclpnet.game.map.GameMap
import java.util.*
import java.util.stream.Collectors

private const val PLACEMENT_FLAGS = Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_SUPPRESS_DROPS

private class ShapeSpace(val shape: SdShape, val space: IndexedSet<BlockPos>) {

    fun randomPosition(random: Random): BlockPos? {
        if (space.isEmpty()) return null
        return space[random.nextInt(space.size)]
    }

    fun placeAt(pos: BlockPos, world: ServerLevel) {
        val water = Blocks.WATER.defaultBlockState()

        for (shapePos in shape.positions) {
            world.setBlock(pos.offset(shapePos), water, PLACEMENT_FLAGS)
        }
    }

    fun remove(shape: SdShape, pos: BlockPos) {
        val it = shape.combinedPositions()

        while (it.hasNext()) {
            val occupied = pos.offset(it.next())

            for (selfPos in this.shape.positions) {
                space.remove(occupied.subtract(selfPos))
            }
        }
    }
}

class SdGenerator(private val world: ServerLevel, private val map: GameMap, private val random: Random) {

    fun generate() {
        val blockShape: BlockShape = MapUtil.readArea(map)

        val shapes = WeightedList<SdShape>().apply {
            add(sdShapeSquare(1, 1), 0.2f)
            add(sdShapeSquare(2, 2), 0.44f)
            add(sdShapeSquare(3, 3), 0.36f)
        }

        generatePuddles(blockShape, shapes)
    }

    private fun generatePuddles(blockShape: BlockShape, shapes: WeightedList<SdShape>) {
        val pool = createPool(blockShape, shapes)

        while (!pool.isEmpty()) {
            val index = pool.getRandomIndex(random)
            val shape = pool[index]

            if (shape.space.isEmpty()) {
                pool.removeAt(index)
                continue
            }

            val pos = shape.randomPosition(random)

            if (pos == null) {
                pool.removeAt(index)
                continue
            }

            shape.placeAt(pos, world)

            for (space in pool) {
                space.remove(shape.shape, pos)
            }
        }
    }

    private fun createPool(blockShape: BlockShape, shapes: WeightedList<SdShape>): WeightedList<ShapeSpace> {
        val space = HashSet<BlockPos>()

        for (pos in blockShape) {
            space.add(pos.immutable())
        }

        return shapes.map { shape -> ShapeSpace(shape, space.stream()
            .filter { pos -> shape.hasSpace(space, pos) }
            .collect(Collectors.toCollection { IndexedSet() })) }
    }
}
