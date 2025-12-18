package work.lclpnet.ap2.game.speed_builders.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.NeighborUpdater;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.ItemFramePutItemCallback;
import work.lclpnet.kibu.hook.entity.ItemFrameRemoveItemCallback;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.world.BlockModificationHooks;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

/**
 * Configure and handle custom block placement / destruction logic inside the build area of each player.
 */
public class SbConfiguration {

    private final MiniGameHandle gameHandle;
    private final SbManager manager;
    private final SbItems items;

    public SbConfiguration(MiniGameHandle gameHandle, SbManager manager, SbItems items) {
        this.gameHandle = gameHandle;
        this.manager = manager;
        this.items = items;
    }

    public void configureProtection() {
        gameHandle.protect(config -> {
            config.allow((entity, pos) -> entity instanceof ServerPlayer player && canModify(player, pos),
                    ProtectionTypes.PLACE_BLOCKS, ProtectionTypes.BREAK_BLOCKS, ProtectionTypes.USE_BLOCK,
                    ProtectionTypes.PLACE_FLUID, ProtectionTypes.EAT_CAKE, ProtectionTypes.COMPOSTER,
                    ProtectionTypes.CHARGE_RESPAWN_ANCHOR, ProtectionTypes.EXTINGUISH_CANDLE,
                    ProtectionTypes.PICKUP_FLUID);

            config.allow((player, itemFrame) -> player instanceof ServerPlayer serverPlayer && canModify(serverPlayer, itemFrame.blockPosition()),
                    ProtectionTypes.ITEM_FRAME_SET_ITEM, ProtectionTypes.ITEM_FRAME_ROTATE_ITEM,
                    ProtectionTypes.ITEM_FRAME_REMOVE_ITEM);

            config.allow(ProtectionTypes.USE_ITEM_ON_BLOCK, (entity, ctx)
                    -> entity instanceof ServerPlayer player
                    && canModify(player, ctx.getClickedPos().above()));

            config.allow(ProtectionTypes.MODIFY_INVENTORY);

            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, source) -> {
                if (entity instanceof ServerPlayer ||
                    !(source.getEntity() instanceof ServerPlayer player) ||
                    !canModify(player, entity.blockPosition())) return false;

                ItemStack stack = items.getEntitySummon(entity);

                // remove the entity when a player hit it
                entity.discard();

                giveStack(player, stack);

                return false;
            });
        });
    }

    public void registerHooks() {
        HookRegistrar hooks = gameHandle.getHooks();

        hooks.registerHook(PlayerInteractionHooks.ATTACK_BLOCK, (player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayer serverPlayer && canModify(serverPlayer, pos) && world instanceof ServerLevel serverWorld) {
                destroyBlockDelayed(serverWorld, pos, serverPlayer);
            }

            return InteractionResult.PASS;
        });

        hooks.registerHook(BlockModificationHooks.PLACE_FLUID, (world, pos, entity, fluid) -> {
            if (entity instanceof ServerPlayer player && canModify(player, pos)) {
                placeFluid(world, player, pos, fluid);
                manager.onEdit(player);
            }

            return true;
        });

        hooks.registerHook(ItemFrameRemoveItemCallback.HOOK, (itemFrame, attacker) -> {
            ItemStack stack = itemFrame.getItem();

            if (attacker instanceof ServerPlayer player && !stack.isEmpty() && canModify(player, itemFrame.blockPosition())) {
                giveStack(player, stack.copy());
            }

            return false;  // do not cancel, so the item frame is destroyed etc.
        });

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (player, world, hand, hitResult) -> {
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            if (state.is(BlockTags.BUTTONS)) {
                return InteractionResult.FAIL;
            }

            if (player instanceof ServerPlayer serverPlayer && canModify(serverPlayer, pos)) {
                manager.onEdit(serverPlayer);
            }

            return InteractionResult.PASS;
        });

        hooks.registerHook(BlockModificationHooks.PLACE_BLOCK, (world, pos, entity, newState) -> {
            if (entity instanceof ServerPlayer player && canModify(player, pos)) {
                manager.onEdit(player);
            }

            return false;
        });

        hooks.registerHook(BlockModificationHooks.USE_ITEM_ON_BLOCK, ctx -> {
            Player player = ctx.getPlayer();
            BlockPos pos = ctx.getClickedPos();

            if (player instanceof ServerPlayer serverPlayer && canModify(serverPlayer, pos)) {
                Level world = ctx.getLevel();
                BlockState state = world.getBlockState(pos);

                ItemStack stack = ctx.getItemInHand();

                if (stack.is(Items.WATER_BUCKET) && (state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON))) {
                    world.setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
                    world.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
                    manager.onEdit(serverPlayer);
                    return InteractionResult.FAIL;
                } else if (stack.is(Items.ITEM_FRAME) || stack.is(Items.GLOW_ITEM_FRAME)) {
                    manager.onEdit(serverPlayer);
                    return null;
                }
            }

            return null;
        });

        hooks.registerHook(ItemFramePutItemCallback.HOOK, (itemFrame, stack, player, hand) -> {
            if (player instanceof ServerPlayer serverPlayer && canModify(serverPlayer, itemFrame.blockPosition())) {
                manager.onEdit(serverPlayer);
            }
            return false;
        });

        hooks.registerHook(BlockModificationHooks.BREAK_BLOCK, (world, pos, entity) -> {
            if (entity instanceof ServerPlayer player && canModify(player, pos)) {
                BlockState state = world.getBlockState(pos);
                giveSourceItem(player, state);
            }

            return false;
        });
    }

    private void placeFluid(Level world, ServerPlayer player, BlockPos pos, Fluid fluid) {
        if (!(fluid instanceof FlowingFluid flowableFluid)) return;

        BlockState state = world.getBlockState(pos);

        if (state.getBlock() instanceof LiquidBlockContainer fluidFillable && fluid == Fluids.WATER) {
            if (fluidFillable.placeLiquid(world, pos, state, flowableFluid.getSource(false))) {
                this.playEmptyingSound(player, world, pos, fluid);
                manager.onEdit(player);
            }
        } else if (state.isAir()) {
            if (world.setBlock(pos, fluid.defaultFluidState().createLegacyBlock(), Block.UPDATE_ALL_IMMEDIATE)) {
                this.playEmptyingSound(player, world, pos, fluid);
                manager.onEdit(player);
            }
        }
    }

    private void playEmptyingSound(@Nullable Player player, LevelAccessor world, BlockPos pos, Fluid fluid) {
        Holder<Fluid> entry = BuiltInRegistries.FLUID.wrapAsHolder(fluid);

        SoundEvent soundEvent = entry != null && entry.is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
        world.playSound(player, pos, soundEvent, SoundSource.BLOCKS, 1.0f, 1.0f);
        world.gameEvent(player, GameEvent.FLUID_PLACE, pos);
    }

    private void destroyBlockDelayed(ServerLevel world, BlockPos pos, ServerPlayer player) {
        gameHandle.getScheduler().timeout(() -> {
            BlockState state = world.getBlockState(pos);

            if (state.isAir()) return;

            world.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));

            if (!destroyBlock(world, pos, state, player)) return;

            giveSourceItem(player, state);
        }, 1);
    }

    private boolean destroyBlock(ServerLevel world, BlockPos pos, BlockState state, ServerPlayer player) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int noUpdate = Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

        // destroy multi-blocks
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos other = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            return world.setBlock(pos, air, noUpdate) && world.setBlock(other, air, noUpdate);
        }

        if (state.hasProperty(BlockStateProperties.BED_PART) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction dir = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            BlockPos other = pos.relative(dir, state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT ? 1 : -1);
            return world.setBlock(pos, air, noUpdate) && world.setBlock(other, air, noUpdate);
        }

        if (!world.setBlock(pos, air, noUpdate)) return false;

        // handle neighbour update manually
        var mutable = new BlockPos.MutableBlockPos();

        // check if neighbours will break in order to give source items
        for (Direction dir : NeighborUpdater.UPDATE_ORDER) {
            mutable.setWithOffset(pos, dir);
            BlockState neighbourState = world.getBlockState(mutable);
            BlockState updated = neighbourState.updateShape(world, world, mutable, dir.getOpposite(), pos, air, world.getRandom());

            if (updated.isAir()) {
                giveSourceItem(player, neighbourState);
            }

            world.setBlock(mutable, updated, noUpdate);
        }

        world.updateNeighborsAt(pos, Blocks.AIR);

        return true;
    }

    private void giveSourceItem(ServerPlayer player, BlockState state) {
        ItemStack stack = items.getSourceStack(state);

        if (stack.isEmpty()) return;

        giveStack(player, stack);
    }

    private void giveStack(ServerPlayer player, ItemStack stack) {
        Inventory inventory = player.getInventory();

        if (inventory.getSlotWithRemainingSpace(stack) == -1 && player.getMainHandItem().isEmpty()) {
            inventory.add(inventory.getSelectedSlot(), stack);
        } else {
            inventory.add(stack);
        }
    }

    private boolean canModify(ServerPlayer player, BlockPos pos) {
        return manager.canModify(player) &&
               gameHandle.getParticipants().isParticipating(player) &&
               manager.isWithinBuildingArea(player, pos);
    }
}
