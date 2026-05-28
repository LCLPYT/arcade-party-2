package work.lclpnet.ap2.turf_wars.util

import net.minecraft.core.Direction
import work.lclpnet.ap2.api.game.team.TeamKey
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

class TurfManager(
    initialTurf: List<BlockBox>,
    val teams: List<TeamKey>,
) {

    private val turfs: MutableList<BlockBox>
    val team1Direction: Direction

    init {
        validateTurf(initialTurf)

        turfs = initialTurf.toMutableList()

        val team1Dir = initialTurf[1].min().subtract(initialTurf[0].min())

        team1Direction = requireNotNull(Direction.getNearest(team1Dir, null)) {
            "Could not determine turf direction"
        }
    }

    private fun teamIndex(team: TeamKey): Int? = teams.indexOf(team).let {
        if (it == -1) null else it
    }

    fun growTurf(team: TeamKey) {
        val index = teamIndex(team) ?: return
        val oppositeIndex = index + 1 % 2

        val direction = if (index == 0) team1Direction else team1Direction.opposite

        turfs[index] = turfs[index].grow(direction)
        turfs[oppositeIndex] = turfs[oppositeIndex].grow(direction.opposite)
    }
}

fun BlockBox.grow(direction: Direction): BlockBox = when (direction.axisDirection) {
    Direction.AxisDirection.POSITIVE -> BlockBox(min(), max().relative(direction))
    Direction.AxisDirection.NEGATIVE -> BlockBox(min().relative(direction), max())
}