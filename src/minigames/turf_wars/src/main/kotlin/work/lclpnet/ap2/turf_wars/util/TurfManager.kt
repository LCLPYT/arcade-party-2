package work.lclpnet.ap2.turf_wars.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import work.lclpnet.ap2.api.game.team.DyeTeamKey
import work.lclpnet.ap2.api.game.team.TeamKey
import work.lclpnet.ap2.ext.mc.setBlocks
import work.lclpnet.ap2.game.team.DyeBlockManager
import work.lclpnet.ap2.game.team.getStainedGlassBlock
import work.lclpnet.ap2.impl.util.debug.DebugController
import work.lclpnet.gaco.ds.BlockBox

fun validateTurf(turfs: List<BlockBox>) {
    require(turfs.size == 2) {
        "Expected exactly two initial turfs"
    }

    val enclosingVolume = BlockBox.enclosing(turfs).volume()
    val individualVolume = turfs.sumOf { it.volume() }

    require(enclosingVolume == individualVolume) {
        "Initial turfs must be directly adjacent"
    }
}

class Turf(var bounds: BlockBox?) {
    val builtBlocks = mutableListOf<BlockPos>()
}

class TurfManager(
    initialTurf: List<BlockBox>,
    val teams: List<DyeTeamKey>,
    val debugController: DebugController,
    val level: ServerLevel,
) {

    private val turfs: List<Turf>
    private val dyeManager = DyeBlockManager(level)
    val team1Direction: Direction

    init {
        validateTurf(initialTurf)

        turfs = initialTurf.map { Turf(bounds = it) }

        val team1Dir = initialTurf[1].min().subtract(initialTurf[0].min())

        team1Direction = requireNotNull(Direction.getNearest(team1Dir, null)) {
            "Could not determine turf direction"
        }

        dyeManager.init(teams)
    }

    private fun teamIndex(team: TeamKey): Int? = teams.indexOf(team).let {
        if (it == -1) null else it
    }

    fun turfOf(team: TeamKey): Turf? {
        val index = teamIndex(team) ?: return null

        return turfs[index]
    }

    fun growTurf(team: DyeTeamKey) {
        val index = teamIndex(team) ?: return
        val oppositeIndex = (index + 1) % 2

        val ownTurf = turfs[index]
        val opponentTurf = turfs[oppositeIndex]

        val ownBounds = ownTurf.bounds
        val opponentBounds = opponentTurf.bounds

        if (ownBounds == null || opponentBounds == null) return  // some turf is depleted, game over

        val direction = if (index == 0) team1Direction else team1Direction.opposite

        ownTurf.bounds = ownBounds.grow(direction)
        opponentTurf.bounds = opponentBounds.shrink(direction.opposite)

        removeOutsideBuiltBlocks(opponentTurf)
        replaceDyeBlocks(ownTurf, team)

        updateVisualizer()
    }

    private fun removeOutsideBuiltBlocks(turf: Turf) {
        val bounds = turf.bounds

        val toRemove = if (bounds != null) {
            turf.builtBlocks.filter { !bounds.contains(it) }
        } else {
            turf.builtBlocks
        }

        level.setBlocks(toRemove, Blocks.AIR)
        turf.builtBlocks.removeAll(toRemove)
    }

    private fun replaceDyeBlocks(turf: Turf, team: DyeTeamKey) {
        val bounds = turf.bounds ?: return

        for (pos in bounds) {
            dyeManager.replace(pos, team)
        }
    }

    fun updateVisualizer() {
        debugController.exclusive("turf") { controller ->
            teams.forEachIndexed { index, teamKey ->
                val box = turfs[index].bounds ?: return@forEachIndexed

                controller.renderer().ifPresent { renderer ->
                    renderer.box(box, teamKey.getStainedGlassBlock().defaultBlockState())
                }
            }
        }
    }

    fun isTurf(pos: BlockPos, team: TeamKey): Boolean {
        val index = teamIndex(team) ?: return false

        return turfs[index].bounds?.contains(pos) ?: false
    }
}

fun BlockBox.grow(direction: Direction): BlockBox = when (direction.axisDirection) {
    Direction.AxisDirection.POSITIVE -> BlockBox(min(), max().relative(direction))
    Direction.AxisDirection.NEGATIVE -> BlockBox(min().relative(direction), max())
}

fun BlockBox.shrink(direction: Direction): BlockBox? {
    val len = when (direction.axis) {
        Direction.Axis.X -> width()
        Direction.Axis.Y -> height()
        Direction.Axis.Z -> length()
    }

    if (len == 1) return null  // empty box

    return when (direction.axisDirection) {
        Direction.AxisDirection.POSITIVE -> BlockBox(min(), max().relative(direction.opposite))
        Direction.AxisDirection.NEGATIVE -> BlockBox(min().relative(direction.opposite), max())
    }
}
