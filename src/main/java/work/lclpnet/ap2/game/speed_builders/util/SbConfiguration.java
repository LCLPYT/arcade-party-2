package work.lclpnet.ap2.game.speed_builders.util;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.kibu.hook.entity.ItemFramePutItemCallback;
import work.lclpnet.kibu.hook.entity.ItemFrameRemoveItemCallback;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.world.BlockModificationHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

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
            config.allow((entity, pos) -> entity instanceof ServerPlayerEntity player && canModify(player, pos),
                    ProtectionTypes.PLACE_BLOCKS, ProtectionTypes.BREAK_BLOCKS, ProtectionTypes.USE_BLOCK,
                    ProtectionTypes.PLACE_FLUID, ProtectionTypes.EAT_CAKE, ProtectionTypes.COMPOSTER,
                    ProtectionTypes.CHARGE_RESPAWN_ANCHOR, ProtectionTypes.EXTINGUISH_CANDLE,
                    ProtectionTypes.PICKUP_FLUID);

            config.allow((player, itemFrame) -> player instanceof ServerPlayerEntity serverPlayer && canModify(serverPlayer, itemFrame.getBlockPos()),
                    ProtectionTypes.ITEM_FRAME_SET_ITEM, ProtectionTypes.ITEM_FRAME_ROTATE_ITEM,
                    ProtectionTypes.ITEM_FRAME_REMOVE_ITEM);

            config.allow(ProtectionTypes.USE_ITEM_ON_BLOCK,
                    (entity, pos) -> entity instanceof ServerPlayerEntity player && canModify(player, pos.up()));

            config.allow(ProtectionTypes.MODIFY_INVENTORY);

            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, source) -> {
                if (entity instanceof ServerPlayerEntity ||
                    !(source.getAttacker() instanceof ServerPlayerEntity player) ||
                    !canModify(player, entity.getBlockPos())) return false;

                ItemStack stack = items.getEntitySummon(entity);

                // remove the entity when a player hit it
                entity.discard();

                giveStack(player, stack);

                return false;
            });
        });
    }

    public void registerHooks() {
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(PlayerInteractionHooks.ATTACK_BLOCK, (player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && canModify(serverPlayer, pos)) {
                destroyBlock(world, pos, serverPlayer);
            }

            return ActionResult.PASS;
        });

        hooks.registerHook(PlayerInteractionHooks.BREAK_BLOCK, (world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && canModify(serverPlayer, pos)) {
                giveSourceItem(serverPlayer, state);
            }

            return true;
        });

        hooks.registerHook(BlockModificationHooks.PLACE_FLUID, (world, pos, entity, fluid) -> {
            if (entity instanceof ServerPlayerEntity player && canModify(player, pos)) {
                placeFluid(world, player, pos, fluid);
                manager.onEdit(player);
            }

            return true;
        });

        hooks.registerHook(ItemFrameRemoveItemCallback.HOOK, (itemFrame, attacker) -> {
            ItemStack stack = itemFrame.getHeldItemStack();

            if (attacker instanceof ServerPlayerEntity player && !stack.isEmpty()) {
                giveStack(player, stack.copy());
            }

            return false;  // do not cancel, so the item frame is destroyed etc.
        });

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (player, world, hand, hitResult) -> {
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            if (state.isIn(BlockTags.BUTTONS)) {
                return ActionResult.FAIL;
            }

            if (player instanceof ServerPlayerEntity serverPlayer) {
                manager.onEdit(serverPlayer);
            }

            return ActionResult.PASS;
        });

        hooks.registerHook(BlockModificationHooks.PLACE_BLOCK, (world, pos, entity, newState) -> {
            if (entity instanceof ServerPlayerEntity player) {
                manager.onEdit(player);
            }

            return false;
        });

        hooks.registerHook(BlockModificationHooks.USE_ITEM_ON_BLOCK, ctx -> {
            PlayerEntity player = ctx.getPlayer();

            if (player instanceof ServerPlayerEntity serverPlayer) {
                BlockPos pos = ctx.getBlockPos();
                World world = ctx.getWorld();
                BlockState state = world.getBlockState(pos);

                ItemStack stack = ctx.getStack();

                if (stack.isOf(Items.WATER_BUCKET) && (state.isOf(Blocks.CAULDRON) || state.isOf(Blocks.WATER_CAULDRON))) {
                    world.setBlockState(pos, Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, LeveledCauldronBlock.MAX_LEVEL));
                    world.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1.0f, 1.0f);
                    manager.onEdit(serverPlayer);
                    return ItemActionResult.FAIL;
                } else if (stack.isOf(Items.ITEM_FRAME) || stack.isOf(Items.GLOW_ITEM_FRAME)) {
                    manager.onEdit(serverPlayer);
                    return null;
                }
            }

            return null;
        });

        hooks.registerHook(ItemFramePutItemCallback.HOOK, (itemFrame, stack, player, hand) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                manager.onEdit(serverPlayer);
            }
            return false;
        });
    }

    private void placeFluid(World world, ServerPlayerEntity player, BlockPos pos, Fluid fluid) {
        if (!(fluid instanceof FlowableFluid flowableFluid)) return;

        BlockState state = world.getBlockState(pos);

        if (state.getBlock() instanceof FluidFillable fluidFillable && fluid == Fluids.WATER) {
            if (fluidFillable.tryFillWithFluid(world, pos, state, flowableFluid.getStill(false))) {
                this.playEmptyingSound(player, world, pos, fluid);
                manager.onEdit(player);
            }
        } else if (state.isAir()) {
            if (world.setBlockState(pos, fluid.getDefaultState().getBlockState(), Block.NOTIFY_ALL_AND_REDRAW)) {
                this.playEmptyingSound(player, world, pos, fluid);
                manager.onEdit(player);
            }
        }
    }

    private void playEmptyingSound(@Nullable PlayerEntity player, WorldAccess world, BlockPos pos, Fluid fluid) {
        RegistryEntry<Fluid> entry = Registries.FLUID.getEntry(fluid);

        SoundEvent soundEvent = entry != null && entry.isIn(FluidTags.LAVA) ? SoundEvents.ITEM_BUCKET_EMPTY_LAVA : SoundEvents.ITEM_BUCKET_EMPTY;
        world.playSound(player, pos, soundEvent, SoundCategory.BLOCKS, 1.0f, 1.0f);
        world.emitGameEvent(player, GameEvent.FLUID_PLACE, pos);
    }

    private void destroyBlock(World world, BlockPos pos, ServerPlayerEntity player) {
        gameHandle.getGameScheduler().timeout(() -> {
            BlockState state = world.getBlockState(pos);

            if (state.isAir()) return;

            world.syncWorldEvent(null, WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));

            if (!world.setBlockState(pos, Blocks.AIR.getDefaultState())) return;

            giveSourceItem(player, state);
        }, 1);
    }

    private void giveSourceItem(ServerPlayerEntity player, BlockState state) {
        ItemStack stack = items.getSourceStack(state);

        if (stack.isEmpty()) return;

        giveStack(player, stack);
    }

    private void giveStack(ServerPlayerEntity player, ItemStack stack) {
        PlayerInventory inventory = player.getInventory();

        if (inventory.getOccupiedSlotWithRoomForStack(stack) == -1 && player.getMainHandStack().isEmpty()) {
            inventory.insertStack(inventory.selectedSlot, stack);
        } else {
            inventory.insertStack(stack);
        }
    }

    private boolean canModify(ServerPlayerEntity player, BlockPos pos) {
        return manager.canModify(player) &&
               gameHandle.getParticipants().isParticipating(player) &&
               manager.isWithinBuildingArea(player, pos);
    }
}
