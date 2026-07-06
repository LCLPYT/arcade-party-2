package work.lclpnet.ap2.task_rush.task

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import work.lclpnet.ap2.ext.mc.isIn
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.tags.ApItemTags
import java.util.*
import kotlin.time.Duration.Companion.seconds

/**
 * Collect the most distinct flower types.
 * Only flower types newly acquired during the task are counted.
 */
object FlowerTypesTask : Task {

    override val id = "flower_types"

    private fun flowerTypes(player: ServerPlayer): Set<Item> {
        val types = HashSet<Item>()
        val inventory = player.inventory

        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (!stack.isEmpty && stack.isIn(ApItemTags.FLOWERS)) {
                types.add(stack.item)
            }
        }

        return types
    }

    override fun begin(env: TaskEnv) {
        val startTypes = HashMap<UUID, Set<Item>>()

        for (player in env.players) {
            startTypes[player.uuid] = flowerTypes(player)
        }

        env.timer("task.$id.task", 45.seconds) {
            val data = IntScoreDataContainer(PlayerRef::create, Ordering.DESCENDING, "score.flower_types")

            for (player in env.players) {
                val start = startTypes[player.uuid] ?: emptySet()
                val gained = flowerTypes(player).count { it !in start }
                data.setScore(player, gained)
            }

            env.complete(data)
        }
    }
}
