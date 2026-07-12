package work.lclpnet.ap2.task_rush.task

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import work.lclpnet.ap2.ext.mc.setBlock
import work.lclpnet.kibu.hook.level.BlockModificationHooks

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

        env.duplicateDrops = false

        for (player in env.players) {
            env.give(player, ItemStack(Items.IRON_PICKAXE))
        }

        // make every ore drop exactly one item, so no ore type is worth more than another
        BlockModificationHooks.BREAK_BLOCK.registerWith(env.hooks) { level, pos, entity ->
            if (level != env.level || entity !is ServerPlayer || !env.players.isParticipating(entity)) {
                return@registerWith false
            }

            val state = env.level.getBlockState(pos)
            val blockEntity = env.level.getBlockEntity(pos)
            val tool = entity.inventory.selectedItem
            val drops = Block.getDrops(state, env.level, pos, blockEntity, entity, tool)

            // only normalize ore drops, let everything else break normally
            if (drops.none { matches(it) }) return@registerWith false

            env.level.setBlock(pos, Blocks.AIR)

            for (stack in drops) {
                if (!matches(stack)) continue
                stack.count = 1
                Block.popResource(env.level, pos, stack)
            }

            true
        }
    }
}
