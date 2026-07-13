package work.lclpnet.ap2.task_rush.task

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.levelgen.Heightmap
import work.lclpnet.ap2.ext.mc.isIn
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.tags.ApItemTags
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
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
            if (isFlower(stack)) {
                types.add(stack.item)
            }
        }

        return types
    }

    private fun isFlower(stack: ItemStack): Boolean =
        stack.isIn(ApItemTags.FLOWERS)

    override fun begin(env: TaskEnv) {
        val pos = findFlowerBiome(env)

        if (pos != null) {
            // ensure the target chunk is loaded, otherwise getHeight falls back to the min height and players fall out of the world
            env.level.getChunk(pos.x shr 4, pos.z shr 4)

            val y = env.level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.x, pos.z) + 1
            env.setSpawn(BlockPos(pos.x, y, pos.z))
        }

        val startTypes = HashMap<UUID, Set<Item>>()
        val removed = mutableMapOf<UUID, List<ItemStack>>()
        val collected = HashMap<UUID, MutableSet<Item>>()

        for (player in env.players) {
            startTypes[player.uuid] = flowerTypes(player)

            removed[player.uuid] = removeItems(player)
        }

        PlayerInventoryHooks.PLAYER_PICKUP.registerWith(env.hooks) { player, itemEntity ->
            val stack = itemEntity.item

            if (player is ServerPlayer && env.players.isParticipating(player) && isFlower(stack)) {
                val start = startTypes[player.uuid] ?: emptySet()
                val item = stack.item

                if (item !in start) {
                    val types = collected.getOrPut(player.uuid) { HashSet() }

                    if (types.add(item)) {
                        env.feedback(player, "task.feedback.flower_types", types.size, sound = true)
                    }
                }
            }

            false
        }

        env.timer("task.$id.task", 30.seconds) {
            val data = IntScoreDataContainer(PlayerRef::create, Ordering.DESCENDING, "score.flower_types")

            for (player in env.players) {
                val start = startTypes[player.uuid] ?: emptySet()
                val gained = flowerTypes(player).count { it !in start }
                data.setScore(player, gained)

                removed[player.uuid]?.forEach { env.give(player, it) }
            }

            env.complete(data)
        }
    }

    private fun findFlowerBiome(env: TaskEnv): BlockPos? {
        val res = env.level.findClosestBiome3d(
            { biome ->
                biome.`is`(Biomes.FLOWER_FOREST)
                        || biome.`is`(Biomes.PLAINS)
                        || biome.`is`(Biomes.SUNFLOWER_PLAINS)
                        || biome.`is`(Biomes.BIRCH_FOREST)
                        || biome.`is`(Biomes.OLD_GROWTH_BIRCH_FOREST)
                        || biome.`is`(Biomes.MEADOW)
            },
            env.spawnPos,
            6400,
            32,
            64
        )

        val pos = res?.first
        return pos
    }

    private fun removeItems(player: ServerPlayer): List<ItemStack> {
        val removed = ArrayList<ItemStack>()
        val inventory = player.inventory

        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)

            if (isFlower(stack)) {
                removed.add(stack.copy())
                inventory.setItem(i, ItemStack.EMPTY)
            }
        }

        return removed
    }
}
