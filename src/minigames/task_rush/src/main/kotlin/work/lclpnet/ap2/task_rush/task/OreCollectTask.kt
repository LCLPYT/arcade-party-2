package work.lclpnet.ap2.task_rush.task

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object OreCollectTask : InventoryCountTask("ore_collect", "score.ores_collected") {

    private val ores = setOf(
        Items.COAL,
        Items.RAW_IRON,
        Items.RAW_COPPER,
        Items.RAW_GOLD,
        Items.REDSTONE,
        Items.LAPIS_LAZULI,
        Items.DIAMOND,
        Items.EMERALD,
        Items.QUARTZ,
        Items.COAL_ORE,
        Items.DEEPSLATE_COAL_ORE,
        Items.IRON_ORE,
        Items.DEEPSLATE_IRON_ORE,
        Items.COPPER_ORE,
        Items.DEEPSLATE_COPPER_ORE,
        Items.GOLD_ORE,
        Items.DEEPSLATE_GOLD_ORE,
        Items.NETHER_GOLD_ORE,
        Items.REDSTONE_ORE,
        Items.DEEPSLATE_REDSTONE_ORE,
        Items.LAPIS_ORE,
        Items.DEEPSLATE_LAPIS_ORE,
        Items.DIAMOND_ORE,
        Items.DEEPSLATE_DIAMOND_ORE,
        Items.EMERALD_ORE,
        Items.DEEPSLATE_EMERALD_ORE,
        Items.NETHER_QUARTZ_ORE,
    )

    override fun matches(stack: ItemStack) = stack.item in ores

    override fun begin(env: TaskEnv) {
        super.begin(env)

        for (player in env.players) {
            env.give(player, ItemStack(Items.IRON_PICKAXE))
        }
    }
}
