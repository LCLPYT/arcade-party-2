package work.lclpnet.ap2.game.speed_builders.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.GlowItemFrame
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.block.state.properties.SlabType
import work.lclpnet.ap2.game.speed_builders.data.SbModule
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter
import work.lclpnet.kibu.structure.BlockStructure

class SbItems {

    private var module: SbModule? = null

    fun setModule(currentModule: SbModule?) {
        this.module = currentModule
    }

    fun getBuildingMaterials(entities: List<Entity>): List<ItemStack> {
        val module = this.module ?: return emptyList()

        val structure = module.structure
        val adapter = FabricBlockStateAdapter.getInstance()

        val stacks = mutableListOf<ItemStack>()
        collectBlockMaterials(structure, adapter, stacks)
        collectEntityItems(entities, stacks)

        return stacks
    }

    private fun collectEntityItems(entities: List<Entity>, stacks: MutableList<ItemStack>) {
        for (entity in entities) {
            val stack = getEntitySummon(entity)

            if (stack == null || stack.isEmpty) continue

            stacks.add(stack)

            if (entity is ItemFrame && !entity.item.isEmpty) {
                val item: Item = if (entity is GlowItemFrame) Items.GLOW_ITEM_FRAME else Items.ITEM_FRAME
                stacks.add(ItemStack(item))
            }
        }
    }

    fun getEntitySummon(entity: Entity): ItemStack? = entity.pickResult

    private fun collectBlockMaterials(structure: BlockStructure, adapter: FabricBlockStateAdapter, stacks: MutableList<ItemStack>) {
        val originY = structure.origin.y
        val states = mutableMapOf<BlockState, Int>()
        var waterRequired = false

        for (pos in structure.blockPositions) {
            if (pos.y - originY == 0) continue

            val kibuState = structure.getBlockState(pos)
            val state = adapter.revert(kibuState) ?: continue

            if (!waterRequired) {
                if ((state.properties.contains(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED))
                    || state.`is`(Blocks.WATER_CAULDRON)) {
                    waterRequired = true
                }
            }

            states.compute(state) { _, prev -> if (prev == null) 1 else prev + 1 }
        }

        states.forEach { (state, count) ->
            val stack = getMaterialStack(state)
            collectStacks(stack, count, stacks)
        }

        if (waterRequired) {
            stacks.add(ItemStack(Items.WATER_BUCKET))
        }
    }

    private fun collectStacks(stack: ItemStack, count: Int, stacks: MutableList<ItemStack>) {
        if (stack.isEmpty) return

        var remaining = count * stack.count
        stack.count = 1

        val maxCount = stack.maxStackSize

        while (remaining > 0) {
            val decrement = minOf(remaining, maxCount)
            stacks.add(stack.copyWithCount(decrement))
            remaining -= decrement
        }
    }

    fun giveBuildingMaterials(players: Iterable<ServerPlayer>, entities: List<Entity>) {
        val stacks = getBuildingMaterials(entities)

        for (player in players) {
            for (stack in stacks) {
                player.inventory.add(stack.copy())
            }
            PlayerInventoryAccess.setSelectedSlot(player, 0)
        }
    }

    fun getSourceStack(state: BlockState): ItemStack {
        val properties = state.properties

        if (properties.contains(BlockStateProperties.SLAB_TYPE)) {
            if (state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
                return ItemStack(state.block.asItem(), 2)
            }
        }

        return ItemStack(state.block.asItem())
    }

    fun getMaterialStack(state: BlockState): ItemStack {
        val properties = state.properties

        if (properties.contains(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            if (state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                return ItemStack.EMPTY
            }
        }

        if (properties.contains(BlockStateProperties.BED_PART)) {
            if (state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
                return ItemStack.EMPTY
            }
        }

        return getSourceStack(state)
    }
}
