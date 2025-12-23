package work.lclpnet.ap2.util.loot

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.DoubleBlockCombiner
import work.lclpnet.ap2.api.base.Participants
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.world.BlockModificationHooks

class LazyLootContainerManager(
    val participants: Participants,
    val level: ServerLevel,
    val filler: LootFiller,
) {
    val filled = mutableSetOf<BlockPos>()

    fun setup(hooks: HookRegistrar) {
        hooks.registerHook(
            PlayerInteractionHooks.USE_BLOCK,
            UseBlockCallback { player, level, _, result ->
                if (player is ServerPlayer && participants.isParticipating(player) && this.level == level) {
                    touch(result.blockPos)
                }

                InteractionResult.PASS;
            }
        )

        hooks.registerHook(
            BlockModificationHooks.BLOCK_PLACED,
            BlockModificationHooks.BlockModifiedHook { level, pos, entity ->
                if (entity is ServerPlayer && participants.isParticipating(entity) && this.level == level) {
                    onBlockPlaced(pos)
                }
            }
        )

        hooks.registerHook(
            BlockModificationHooks.BLOCK_BROKEN,
            BlockModificationHooks.BlockModifiedHook { level, pos, _ ->
                if (this.level == level) {
                    onBlockRemoved(pos)
                }
            }
        )
    }

    fun touch(pos: BlockPos) {
        val blockEntity = level.getBlockEntity(pos)

        if (blockEntity !is Container || !filled.add(pos.immutable())) return

        // container not filled yet
        val state = level.getBlockState(pos)
        val block = state.block

        // if the block is a double chest, the other half of the chest must be marked filled as well
        if (block is ChestBlock && ChestBlock.getBlockType(state) != DoubleBlockCombiner.BlockType.SINGLE) {
            val neighborDir = ChestBlock.getConnectedDirection(state)
            val otherPos = pos.relative(neighborDir)

            if (!filled.add(otherPos)) return
        }

        // find correct container to fill
        val inventoryToFill = when {
            block is ChestBlock -> ChestBlock.getContainer(
                block,
                state,
                level,
                pos,
                false
            )
            else -> blockEntity
        }

        if (inventoryToFill == null) return

        filler.fill(pos, level, inventoryToFill)
    }

    private fun onBlockPlaced(pos: BlockPos) {
        val blockEntity = level.getBlockEntity(pos)

        // player-made blocks should not be filled, therefore mark it as filled immediately
        if (blockEntity is Container) {
            filled.add(pos)
        }
    }

    private fun onBlockRemoved(pos: BlockPos) {
        filled.remove(pos)
    }

    fun reset() {
        filled.clear()
    }
}