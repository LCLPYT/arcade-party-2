package work.lclpnet.ap2.game.maniac_digger.data

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MdPipePathTest {

    private val path = MdPipePath(listOf(
        Vec3(0.5, 0.0, 0.5),  // goal, bottom of lower shaft
        Vec3(0.5, 5.0, 0.5),  // top of lower shaft / start of corridor
        Vec3(5.5, 5.0, 0.5),  // end of corridor / bottom of upper shaft
        Vec3(5.5, 8.0, 0.5)   // top of upper shaft
    ))

    private val eps = 1e-6

    @Test
    fun verticalProgressFromBottom() {
        assertEquals(0.0, path.progressToGoal(Vec3(0.5, 0.0, 0.5)), eps)
        assertEquals(1.0, path.progressToGoal(Vec3(0.5, 1.0, 0.5)), eps)
        assertEquals(4.0, path.progressToGoal(Vec3(0.5, 4.0, 0.5)), eps)
        assertEquals(5.0, path.progressToGoal(Vec3(0.5, 5.0, 0.5)), eps)
    }

    @Test
    fun horizontalProgressCounts() {
        assertEquals(7.0, path.progressToGoal(Vec3(2.5, 5.0, 0.5)), eps)
        assertEquals(9.0, path.progressToGoal(Vec3(4.5, 5.0, 0.5)), eps)
        assertEquals(10.0, path.progressToGoal(Vec3(5.5, 5.0, 0.5)), eps)
    }

    @Test
    fun corridorScoreIndependentOfHeightWithinCorridor() {
        val low = path.progressToGoal(Vec3(2.5, 5.0, 0.5))
        val high = path.progressToGoal(Vec3(2.5, 6.0, 0.5))
        assertEquals(low, high, eps)
    }

    @Test
    fun upperShaftContinuesAfterCorridor() {
        assertEquals(11.0, path.progressToGoal(Vec3(5.5, 6.0, 0.5)), eps)
        assertEquals(13.0, path.progressToGoal(Vec3(5.5, 8.0, 0.5)), eps)
    }

    @Test
    fun monotonicAlongWholePath() {
        val bottom = path.progressToGoal(Vec3(0.5, 1.0, 0.5))
        val shaftTop = path.progressToGoal(Vec3(0.5, 5.0, 0.5))
        val corridorMid = path.progressToGoal(Vec3(2.5, 5.0, 0.5))
        val corridorEnd = path.progressToGoal(Vec3(5.5, 5.0, 0.5))
        val upper = path.progressToGoal(Vec3(5.5, 7.0, 0.5))

        assertTrue(bottom < shaftTop)
        assertTrue(shaftTop < corridorMid)
        assertTrue(corridorMid < corridorEnd)
        assertTrue(corridorEnd < upper)
    }

    @Test
    fun emptyPathIsZero() {
        assertEquals(0.0, MdPipePath(emptyList()).progressToGoal(Vec3(1.0, 2.0, 3.0)), eps)
    }
}
