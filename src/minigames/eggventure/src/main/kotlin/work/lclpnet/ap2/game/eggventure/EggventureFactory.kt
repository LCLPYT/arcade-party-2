package work.lclpnet.ap2.game.eggventure

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntityType
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.openRandomMap
import work.lclpnet.ap2.impl.game.GameCommons
import work.lclpnet.ap2.impl.map.MapUtil
import java.util.*

class EggventureFactory : MiniGameFactory {
    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = openRandomMap(handle)

        val random = Random()
        val remainingPositions = HashSet<BlockPos>()

        val shape = MapUtil.readShape(map, "egg-area")
        val positions = mutableListOf<BlockPos>()

        for (pos in shape) {
            if (isEasterEgg(level, pos)) {
                positions.add(pos.immutable())
            }
        }

        val minEggs: Int = map.requireProperty("min-eggs")
        val maxEggs: Int = map.requireProperty("max-eggs")
        val eggs = minEggs + random.nextInt(maxEggs - minEggs + 1)

        val variants = eggVariants(level.registryAccess())

        if (variants.isEmpty()) {
            throw IllegalStateException("There are no egg variants defined")
        }

        if (ApConstants.DEBUG) {
            handle.logger.info("There are {} possible egg positions and {} should be placed", positions.size, eggs)
        }

        val debugController = GameCommons(handle, map, level).debugController()

        repeat(eggs) {
            if (positions.isEmpty()) return@repeat

            val pos = positions.removeAt(random.nextInt(positions.size))

            if (DEBUG_EGG_POSITIONS) {
                debugController.renderer().ifPresent { renderer ->
                    renderer.marker(pos.center, Blocks.GREEN_TERRACOTTA.defaultBlockState(), 0x00ff00)
                }
            }

            val variant = variants[random.nextInt(variants.size)]
            level.getBlockEntity(pos, BlockEntityType.SKULL).ifPresent { variant.apply(it) }
            remainingPositions.add(pos)
        }

        for (pos in positions) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_SUPPRESS_DROPS or Block.UPDATE_KNOWN_SHAPE)

            if (DEBUG_EGG_POSITIONS) {
                debugController.renderer().ifPresent { renderer ->
                    renderer.marker(pos.center, Blocks.BLUE_TERRACOTTA.defaultBlockState(), 0x0000ff)
                }
            }
        }

        return EggventureInstance(handle, level, map, remainingPositions)
    }
}
