package work.lclpnet.ap2.game.util

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import work.lclpnet.ap2.ext.mc.teleport
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.impl.game.BaseGameInstance
import work.lclpnet.ap2.impl.util.world.SpawnFinder
import work.lclpnet.gaco.ds.BlockBox
import kotlin.random.Random
import kotlin.random.asJavaRandom

private const val DEBUG_SPAWNS = false

fun BaseGameInstance.teleportToRandomSpawns(scanBox: BlockBox, scanStarts: Iterable<BlockPos>, spacing: Double = 8.0) {
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
        val yaw = Random.nextFloat() * 360f
        player.teleport(pos, yaw)
    }

    if (DEBUG_SPAWNS) {
        commons().debugController().renderer().ifPresent {
            for (pos in spawns) {
                it.marker(pos, Blocks.BLUE_STAINED_GLASS.defaultBlockState(), 0x0000ff)
            }
        }
    }
}