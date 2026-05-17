package work.lclpnet.ap2.game.mirror_hop

import org.json.JSONArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(MirrorHopChoicesTest::class.java)

class MirrorHopChoicesTest {

    @Test
    fun from() {
        val choices = mirrorHopChoicesFrom(JSONArray("""
            [
              [
                [[-3,63,8],[-1,63,10]],
                [[2,63,8],[4,63,10]]
              ],
              [
                [[-3,63,13],[-1,63,15]],
                [[2,63,13],[4,63,15]]
              ],
              [
                [[-3,63,18],[-1,63,20]],
                [[2,63,18],[4,63,20]]
              ]
            ]
            """), logger)

        val choiceList = choices.choices
        assertEquals(3, choiceList.size)

        for (i in 0 until 3) {
            assertEquals(2, choiceList[i].platforms.size)
        }
    }
}
