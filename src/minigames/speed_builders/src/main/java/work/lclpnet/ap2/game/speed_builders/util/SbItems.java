package work.lclpnet.ap2.game.speed_builders.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.speed_builders.data.SbModule;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.mc.KibuBlockState;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SbItems {

    @Nullable
    private SbModule module = null;

    public void setModule(@Nullable SbModule currentModule) {
        this.module = currentModule;
    }

    public List<ItemStack> getBuildingMaterials(List<? extends Entity> entities) {
        if (module == null) {
            return List.of();
        }

        BlockStructure structure = module.structure();
        FabricBlockStateAdapter adapter = FabricBlockStateAdapter.getInstance();

        List<ItemStack> stacks = new ArrayList<>();
        collectBlockMaterials(structure, adapter, stacks);
        collectEntityItems(entities, stacks);

        return stacks;
    }

    private void collectEntityItems(List<? extends Entity> entities, List<ItemStack> stacks) {
        for (Entity entity : entities) {
            ItemStack stack = getEntitySummon(entity);

            if (stack == null || stack.isEmpty()) continue;

            stacks.add(stack);

            if (entity instanceof ItemFrame frame && !frame.getItem().isEmpty()) {
                Item item = frame instanceof GlowItemFrame ? Items.GLOW_ITEM_FRAME : Items.ITEM_FRAME;
                stacks.add(new ItemStack(item));
            }
        }
    }

    public @Nullable ItemStack getEntitySummon(Entity entity) {
        return entity.getPickResult();
    }

    private void collectBlockMaterials(BlockStructure structure, FabricBlockStateAdapter adapter, List<ItemStack> stacks) {
        int originY = structure.getOrigin().getY();

        Map<BlockState, Integer> states = new HashMap<>();
        boolean waterRequired = false;

        for (KibuBlockPos pos : structure.getBlockPositions()) {
            if (pos.getY() - originY == 0) continue;

            KibuBlockState kibuState = structure.getBlockState(pos);
            BlockState state = adapter.revert(kibuState);

            if (state == null) continue;

            if (!waterRequired) {
                if ((state.getProperties().contains(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED))
                    || state.is(Blocks.WATER_CAULDRON)) {
                    waterRequired = true;
                }
            }

            states.compute(state, (_, prev) -> prev == null ? 1 : prev + 1);
        }

        states.forEach((state, count) -> {
            ItemStack stack = getMaterialStack(state);
            collectStacks(stack, count, stacks);
        });

        if (waterRequired) {
            stacks.add(new ItemStack(Items.WATER_BUCKET));
        }
    }

    private void collectStacks(ItemStack stack, int count, List<ItemStack> stacks) {
        if (stack.isEmpty()) return;

        count *= stack.getCount();
        stack.setCount(1);

        int maxCount = stack.getMaxStackSize();

        while (count > 0) {
            int decrement = Math.min(count, maxCount);

            stacks.add(stack.copyWithCount(decrement));

            count -= decrement;
        }
    }

    public void giveBuildingMaterials(Iterable<? extends ServerPlayer> players, List<? extends Entity> entities) {
        List<ItemStack> stacks = getBuildingMaterials(entities);

        for (ServerPlayer player : players) {
            for (ItemStack stack : stacks) {
                player.getInventory().add(stack.copy());
            }

            PlayerInventoryAccess.setSelectedSlot(player, 0);
        }
    }

    public ItemStack getSourceStack(BlockState state) {
        var properties = state.getProperties();

        if (properties.contains(BlockStateProperties.SLAB_TYPE)) {
            SlabType slabType = state.getValue(BlockStateProperties.SLAB_TYPE);

            if (slabType == SlabType.DOUBLE) {
                return new ItemStack(state.getBlock().asItem(), 2);
            }
        }

        return new ItemStack(state.getBlock().asItem());
    }

    public ItemStack getMaterialStack(BlockState state) {
        var properties = state.getProperties();

        if (properties.contains(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);

            if (half == DoubleBlockHalf.UPPER) {
                return ItemStack.EMPTY;
            }
        }

        if (properties.contains(BlockStateProperties.BED_PART)) {
            BedPart part = state.getValue(BlockStateProperties.BED_PART);

            if (part == BedPart.HEAD) {
                return ItemStack.EMPTY;
            }
        }

        return getSourceStack(state);
    }
}
