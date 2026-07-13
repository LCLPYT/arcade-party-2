package work.lclpnet.ap2.game.util

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.ext.mc.teleport
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.game.base.MapGameInstance
import work.lclpnet.ap2.impl.util.math.MathUtil
import work.lclpnet.ap2.impl.util.world.SpawnFinder
import work.lclpnet.gaco.ds.BlockBox
import kotlin.random.Random
import kotlin.random.asJavaRandom

private const val DEBUG_SPAWNS = false

fun MapGameInstance.teleportToRandomSpawns(scanBox: BlockBox, scanStarts: Iterable<BlockPos>, spacing: Double = 8.0, lookAt: Vec3? = null) {
    val starts = scanStarts.toSet()

    check(starts.isNotEmpty()) {
        "At least one scan start position is required to generate random spawn positions"
    }

    val finder = SpawnFinder(spacing, commons().debugController())
    val pool = finder.findSpawns(level, scanBox, starts)
    val spawns = finder.generateSpacedSpawns(pool, players().count(), Random.asJavaRandom())

    var i = 0

    for (player in players()) {
        val pos = spawns[i++]

        // face the common target if given, otherwise a random direction
        val yaw = if (lookAt != null) MathUtil.yaw(lookAt.subtract(pos)) else Random.nextFloat() * 360f

        player.teleport(pos, yaw, level = level)
    }

    if (DEBUG_SPAWNS) {
        commons().debugController().renderer().ifPresent {
            for (pos in spawns) {
                it.marker(pos, Blocks.STAINED_GLASS.blue.defaultBlockState(), 0x0000ff)
            }
        }
    }
}