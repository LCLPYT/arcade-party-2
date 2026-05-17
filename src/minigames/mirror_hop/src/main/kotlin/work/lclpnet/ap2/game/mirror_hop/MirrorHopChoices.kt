package work.lclpnet.ap2.game.mirror_hop

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import org.json.JSONArray
import org.slf4j.Logger
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.gaco.collisions.CollisionDetector
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.ds.Collider
import work.lclpnet.game.map.GameMap
import java.util.*

fun mirrorHopChoicesFrom(map: GameMap, logger: Logger): MirrorHopChoices {
    val array: JSONArray = map.requireProperty("choices")
    return mirrorHopChoicesFrom(array, logger)
}

fun mirrorHopChoicesFrom(json: JSONArray, logger: Logger): MirrorHopChoices {
    val choices = buildList {
        for (obj in json) {
            if (obj !is JSONArray) {
                logger.warn("Invalid choice item of type {}", obj.javaClass.simpleName)
                continue
            }

            if (obj.length() < 2) {
                logger.warn("There should be at least 2 platform for each choice")
            }

            val platforms = buildList {
                for (pObj in obj) {
                    if (pObj !is JSONArray) {
                        logger.warn("Invalid platform item of type {}", pObj.javaClass.simpleName)
                        continue
                    }

                    add(MirrorHopChoices.Platform(MapUtil.readBox(pObj)))
                }
            }

            if (platforms.isEmpty()) {
                logger.warn("No platforms, skipping entry")
                continue
            }

            add(MirrorHopChoices.Choice(platforms))
        }
    }

    return MirrorHopChoices(choices)
}

class MirrorHopChoices(val choices: List<Choice>) {

    private val correct = IntArray(choices.size)

    fun randomize(random: Random) {
        for (i in choices.indices) {
            correct[i] = random.nextInt(choices[i].platforms.size)
        }
    }

    fun addColliders(collisionDetector: CollisionDetector) {
        for (choice in choices) {
            for (platform in choice.platforms) {
                collisionDetector.add(platform)
            }
        }
    }

    fun getChoiceIndex(platform: Platform): Int {
        for (i in choices.indices) {
            if (choices[i].platforms.contains(platform)) return i
        }
        return -1
    }

    fun isCorrect(platform: Platform, choiceIndex: Int): Boolean {
        require(choiceIndex >= 0) { "Choice index must not be negative" }
        val choice = choices[choiceIndex]
        val platformIndex = choice.platforms.indexOf(platform)
        require(platformIndex != -1) { "Platform does not belong to the given choice" }
        return correct[choiceIndex] == platformIndex
    }

    data class Choice(val platforms: List<Platform>)

    class Platform(val ground: BlockBox) : Collider {
        private val bounds = BlockBox(ground.min(), ground.max().offset(1, 3, 1))

        override fun collidesWith(x: Double, y: Double, z: Double): Boolean = bounds.collidesWith(x, y, z)
        override fun collidesWith(box: AABB): Boolean = bounds.collidesWith(box)
        override fun min(): BlockPos = bounds.min()
        override fun max(): BlockPos = bounds.max()
    }
}
