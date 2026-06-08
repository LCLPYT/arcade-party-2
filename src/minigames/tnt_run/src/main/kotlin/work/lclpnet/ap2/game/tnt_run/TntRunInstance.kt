package work.lclpnet.ap2.game.tnt_run

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Relative
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.shapes.CollisionContext
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.gaco.collisions.util.GroundDetector
import work.lclpnet.kibu.hook.level.BlockBreakParticleCallback

private val MARKED_STATE = Blocks.RED_TERRACOTTA.defaultBlockState()
private const val BLOCK_MARGIN = 0.35
private const val BREAK_TICKS = 10

class TntRunInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle) {

    private val removal = Object2IntOpenHashMap<BlockPos>()
    private val groundBlocks = mutableListOf<BlockPos>()
    private lateinit var groundDetector: GroundDetector

    override fun prepare() {
        useSmoothDeath()
        useNoHealing()
        useRemainingPlayersDisplay()

        BlockBreakParticleCallback.HOOK.registerWith(gameHandle.hooks) { _, _, _ -> true }
    }

    override fun go() {
        groundDetector = GroundDetector(level, BLOCK_MARGIN)

        commons().whenBelowCriticalHeight().then(this::eliminate)

        runEveryTick {
            tick()
        }
    }

    private fun tick() {
        tickRemoval()

        groundBlocks.clear()

        for (player in gameHandle.participants) {
            groundDetector.collectBlocksBelow(player, groundBlocks)
        }

        markForRemovalBelow()
    }

    private fun tickRemoval() {
        val world: ServerLevel = level
        val flags = Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_CLIENTS or Block.UPDATE_SUPPRESS_DROPS

        val it = removal.object2IntEntrySet().iterator()

        while (it.hasNext()) {
            val entry = it.next()
            val remain = entry.intValue

            if (remain > 0) {
                entry.setValue(remain - 1)
                continue
            }

            val key = entry.key
            it.remove()
            world.setBlock(key, Blocks.AIR.defaultBlockState(), flags)
        }
    }

    private fun markForRemovalBelow() {
        val world: ServerLevel = level

        for (pos in groundBlocks) {
            if (removal.containsKey(pos)) continue

            val posUp = pos.above()
            val state = world.getBlockState(pos)

            if (state.isAir) continue

            val above = world.getBlockState(posUp)
            val aboveShape = above.getCollisionShape(world, posUp, CollisionContext.empty())

            if (!aboveShape.isEmpty) {
                val box = aboveShape.bounds()
                if (box.xsize >= 1 && box.zsize >= 1) continue
            }

            removal.put(pos, BREAK_TICKS)

            if (state.getCollisionShape(world, pos).isEmpty) continue

            val markedCollisionBox = MARKED_STATE.getCollisionShape(world, pos).bounds().move(pos)
            val colliding = world.getEntities(null as Entity?, markedCollisionBox) { !it.isSpectator }

            for (entity in colliding) {
                val dy = markedCollisionBox.maxY - entity.y
                entity.teleportTo(world, 0.0, dy, 0.0, Relative.ALL, 0f, 0f, false)
            }

            world.setBlockAndUpdate(pos, MARKED_STATE)
        }
    }
}
