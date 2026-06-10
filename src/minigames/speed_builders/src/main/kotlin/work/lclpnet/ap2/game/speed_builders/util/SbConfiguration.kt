package work.lclpnet.ap2.game.speed_builders.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.redstone.NeighborUpdater
import work.lclpnet.ap2.ext.mc.isIn
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.kibu.hook.entity.ItemFramePutItemCallback
import work.lclpnet.kibu.hook.entity.ItemFrameRemoveItemCallback
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.level.BlockModificationHooks

class SbConfiguration(
    private val gameHandle: MiniGameHandle,
    private val manager: SbManager,
    private val items: SbItems
) {

    fun configureProtection() {
        gameHandle.protect { config ->
            for (type in listOf(
                ProtectionTypes.PLACE_BLOCKS,
                ProtectionTypes.BREAK_BLOCKS,
                ProtectionTypes.USE_BLOCK,
                ProtectionTypes.PLACE_FLUID,
                ProtectionTypes.EAT_CAKE,
                ProtectionTypes.COMPOSTER,
                ProtectionTypes.CHARGE_RESPAWN_ANCHOR,
                ProtectionTypes.EXTINGUISH_CANDLE,
                ProtectionTypes.PICKUP_FLUID
            )) {
                type.allow(config) { entity, pos -> entity is ServerPlayer && canModify(entity, pos) }
            }

            for (type in listOf(
                ProtectionTypes.ITEM_FRAME_SET_ITEM,
                ProtectionTypes.ITEM_FRAME_ROTATE_ITEM,
                ProtectionTypes.ITEM_FRAME_REMOVE_ITEM
            )) {
                type.allow(config) { player, itemFrame ->
                    player is ServerPlayer && canModify(player, itemFrame.blockPosition())
                }
            }

            ProtectionTypes.USE_ITEM_ON_BLOCK.allow(config) { entity, ctx ->
                entity is ServerPlayer && canModify(entity, ctx.clickedPos.above())
            }

            ProtectionTypes.MODIFY_INVENTORY.allow(config)

            ProtectionTypes.ALLOW_DAMAGE.allow(config) { entity, source ->
                val player = source.entity

                if (entity is ServerPlayer || player !is ServerPlayer || !canModify(player, entity.blockPosition())) {
                    return@allow false
                }

                entity.discard()
                items.getEntitySummon(entity)?.let { giveStack(player, it) }
                false
            }
        }
    }

    fun registerHooks() {
        val hooks = gameHandle.hooks

        PlayerInteractionHooks.ATTACK_BLOCK.registerWith(hooks) { player, world, _, pos, _ ->
            if (player is ServerPlayer && canModify(player, pos) && world is ServerLevel) {
                destroyBlockDelayed(world, pos, player)
            }
            InteractionResult.PASS
        }

        BlockModificationHooks.PLACE_FLUID.registerWith(hooks) { world, pos, entity, fluid ->
            if (entity is ServerPlayer && canModify(entity, pos)) {
                placeFluid(world, entity, pos, fluid)
                manager.onEdit(entity)
            }
            true
        }

        ItemFrameRemoveItemCallback.HOOK.registerWith(hooks) { itemFrame, attacker ->
            val stack = itemFrame.item
            if (attacker is ServerPlayer && !stack.isEmpty && canModify(attacker, itemFrame.blockPosition())) {
                giveStack(attacker, stack.copy())
            }
            false
        }

        PlayerInteractionHooks.USE_BLOCK.registerWith(hooks) { player, world, _, hitResult ->
            val pos = hitResult.blockPos
            val state = world.getBlockState(pos)

            if (state.isIn(BlockTags.BUTTONS)) {
                return@registerWith InteractionResult.FAIL
            }

            if (player is ServerPlayer && canModify(player, pos)) {
                manager.onEdit(player)
            }

            InteractionResult.PASS
        }

        BlockModificationHooks.PLACE_BLOCK.registerWith(hooks) { _, pos, entity, _ ->
            if (entity is ServerPlayer && canModify(entity, pos)) {
                manager.onEdit(entity)
            }
            false
        }

        BlockModificationHooks.USE_ITEM_ON_BLOCK.registerWith(hooks) { ctx ->
            val player = ctx.player
            val pos = ctx.clickedPos

            if (player is ServerPlayer && canModify(player, pos)) {
                val world = ctx.level
                val state = world.getBlockState(pos)
                val stack = ctx.itemInHand

                if (stack.isOf(Items.WATER_BUCKET) && (state.isOf(Blocks.CAULDRON) || state.isOf(Blocks.WATER_CAULDRON))) {
                    world.setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL))
                    world.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f)
                    manager.onEdit(player)
                    return@registerWith InteractionResult.FAIL
                } else if (stack.isOf(Items.ITEM_FRAME) || stack.isOf(Items.GLOW_ITEM_FRAME)) {
                    manager.onEdit(player)
                    return@registerWith null
                }
            }

            null
        }

        ItemFramePutItemCallback.HOOK.registerWith(hooks) { itemFrame, _, player, _ ->
            if (player is ServerPlayer && canModify(player, itemFrame.blockPosition())) {
                manager.onEdit(player)
            }
            false
        }

        BlockModificationHooks.BREAK_BLOCK.registerWith(hooks) { world, pos, entity ->
            if (entity is ServerPlayer && canModify(entity, pos)) {
                giveSourceItem(entity, world.getBlockState(pos))
            }
            false
        }
    }

    private fun placeFluid(world: Level, player: ServerPlayer, pos: BlockPos, fluid: Fluid) {
        if (fluid !is FlowingFluid) return

        val state = world.getBlockState(pos)

        if (state.block is LiquidBlockContainer && fluid == Fluids.WATER) {
            if ((state.block as LiquidBlockContainer).placeLiquid(world, pos, state, fluid.getSource(false))) {
                playEmptyingSound(player, world, pos, fluid)
                manager.onEdit(player)
            }
        } else if (state.isAir) {
            if (world.setBlock(pos, fluid.defaultFluidState().createLegacyBlock(), Block.UPDATE_ALL_IMMEDIATE)) {
                playEmptyingSound(player, world, pos, fluid)
                manager.onEdit(player)
            }
        }
    }

    private fun playEmptyingSound(player: ServerPlayer?, world: LevelAccessor, pos: BlockPos, fluid: Fluid) {
        val entry: Holder<Fluid> = BuiltInRegistries.FLUID.wrapAsHolder(fluid)
        val soundEvent: SoundEvent = if (entry.`is`(FluidTags.LAVA)) SoundEvents.BUCKET_EMPTY_LAVA else SoundEvents.BUCKET_EMPTY
        world.playSound(player, pos, soundEvent, SoundSource.BLOCKS, 1.0f, 1.0f)
        world.gameEvent(player, GameEvent.FLUID_PLACE, pos)
    }

    private fun destroyBlockDelayed(world: ServerLevel, pos: BlockPos, player: ServerPlayer) {
        gameHandle.scheduler.timeout({ ->
            val state = world.getBlockState(pos)
            if (!state.isAir) {
                world.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state))
                if (destroyBlock(world, pos, state, player)) {
                    giveSourceItem(player, state)
                }
            }
        }, 1)
    }

    private fun destroyBlock(world: ServerLevel, pos: BlockPos, state: BlockState, player: ServerPlayer): Boolean {
        val air = Blocks.AIR.defaultBlockState()
        val noUpdate = Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_CLIENTS or Block.UPDATE_SUPPRESS_DROPS

        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            val other = if (state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) pos.above() else pos.below()
            return world.setBlock(pos, air, noUpdate) && world.setBlock(other, air, noUpdate)
        }

        if (state.hasProperty(BlockStateProperties.BED_PART) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            val dir = state.getValue(BlockStateProperties.HORIZONTAL_FACING)
            val other = pos.relative(dir, if (state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) 1 else -1)
            return world.setBlock(pos, air, noUpdate) && world.setBlock(other, air, noUpdate)
        }

        if (!world.setBlock(pos, air, noUpdate)) return false

        val mutable = BlockPos.MutableBlockPos()

        for (dir in NeighborUpdater.UPDATE_ORDER) {
            mutable.setWithOffset(pos, dir)
            val neighbourState = world.getBlockState(mutable)
            val updated = neighbourState.updateShape(world, world, mutable, dir.opposite, pos, air, world.random)

            if (updated.isAir) {
                giveSourceItem(player, neighbourState)
            }

            world.setBlock(mutable, updated, noUpdate)
        }

        world.updateNeighborsAt(pos, Blocks.AIR)

        return true
    }

    private fun giveSourceItem(player: ServerPlayer, state: BlockState) {
        val stack = items.getSourceStack(state)
        if (stack.isEmpty) return
        giveStack(player, stack)
    }

    private fun giveStack(player: ServerPlayer, stack: ItemStack) {
        val inventory: Inventory = player.inventory
        if (inventory.getSlotWithRemainingSpace(stack) == -1 && player.mainHandItem.isEmpty) {
            inventory.add(inventory.selectedSlot, stack)
        } else {
            inventory.add(stack)
        }
    }

    private fun canModify(player: ServerPlayer, pos: BlockPos): Boolean =
        manager.canModify(player) &&
        gameHandle.participants.isParticipating(player) &&
        manager.isWithinBuildingArea(player, pos)
}
