package work.lclpnet.ap2.task_rush.task

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.kibu.hook.level.BlockModificationHooks

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

    override fun begin(env: TaskEnv) {
        val progress = start(env)
        val blocks: List<Block> = ores[env.level.random.nextInt(ores.size)]

        env.translations.translateText("task.first_ore.mine", blocks.first().name)
            .sendTo(env.players)

        BlockModificationHooks.BLOCK_BROKEN.registerWith(env.hooks) { world, pos, entity ->
            val breaker = entity as? ServerPlayer ?: return@registerWith

            if (!env.players.isParticipating(breaker)) return@registerWith

            val state = world.getBlockState(pos)
            if (blocks.any { state.isOf(it) }) {
                progress.finish(breaker)
            }
        }
    }
}
