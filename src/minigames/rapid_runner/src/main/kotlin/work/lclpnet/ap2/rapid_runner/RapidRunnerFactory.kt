package work.lclpnet.ap2.rapid_runner

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import work.lclpnet.ap2.ext.mc.setDayTime
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.util.generateRandomLevel
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.game.util.ResetWorldModifier

class RapidRunnerFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val level = handle.generateRandomLevel()

        level.setDayTime(1000)

        val walls = ResetWorldModifier(level, handle.hooks)

        placeWalls(level, walls)

        return RapidRunnerInstance(handle, level, walls)
    }

    private fun placeWalls(level: ServerLevel, walls: ResetWorldModifier) {
        val spawn = level.respawnData.pos()
        val r = 4
        val h = 4

        val box = BlockBox(spawn.offset(-r, -1, -r), spawn.offset(r, h, r))
        val flags = Block.UPDATE_SUPPRESS_DROPS or Block.UPDATE_KNOWN_SHAPE

        for (pos in box) {
            if (box.isBorder(pos)) {
                val state = level.getBlockState(pos)

                if (state.isCollisionShapeFullBlock(level, pos)) continue

                walls.setBlockState(pos, Blocks.GLASS.defaultBlockState(), flags)
            } else {
                if (!level.getFluidState(pos).isEmpty) {
                    walls.setBlockState(pos, Blocks.AIR.defaultBlockState(), flags)
                }
            }
        }
    }
}