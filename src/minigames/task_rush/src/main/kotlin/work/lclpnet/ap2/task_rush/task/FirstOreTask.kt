package work.lclpnet.ap2.task_rush.task

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.kibu.hook.level.BlockModificationHooks
import work.lclpnet.kibu.translate.text.TranslatedText

/**
 * Be the first to mine a specific ore.
 * One of iron, copper or coal ore is chosen at random.
 */
object FirstOreTask : OrderTask("first_ore") {

    private val ores = listOf(
        listOf(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
        listOf(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
        listOf(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
    )

    private var blocks: List<Block> = emptyList()

    override fun announcement(env: TaskEnv): TranslatedText {
        blocks = ores.random()

        return env.translations.translateText("task.first_ore", blocks.first().name)
    }

    override fun begin(env: TaskEnv) {
        val progress = start(env)
        val blocks = this.blocks

        for (player in env.players) {
            env.give(player, ItemStack(Items.STONE_PICKAXE))
        }

        BlockModificationHooks.BREAK_BLOCK.registerWith(env.hooks) { world, pos, entity ->
            val breaker = entity as? ServerPlayer ?: return@registerWith false

            if (!env.players.isParticipating(breaker)) return@registerWith false

            val state = world.getBlockState(pos)

            if (blocks.any { state.isOf(it) }) {
                progress.finish(breaker)
            }

            false
        }
    }
}
