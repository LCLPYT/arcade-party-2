package work.lclpnet.ap2.game.speed_builders.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
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

            if (entity instanceof ItemFrameEntity frame && !frame.getHeldItemStack().isEmpty()) {
                Item item = frame instanceof GlowItemFrameEntity ? Items.GLOW_ITEM_FRAME : Items.ITEM_FRAME;
                stacks.add(new ItemStack(item));
            }
        }
    }

    public @Nullable ItemStack getEntitySummon(Entity entity) {
        return entity.getPickBlockStack();
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

            if (!waterRequired && state.getProperties().contains(Properties.WATERLOGGED)) {
                if (state.get(Properties.WATERLOGGED)) {
                    waterRequired = true;
                }
            }

            states.compute(state, (_state, prev) -> prev == null ? 1 : prev + 1);
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

        int maxCount = stack.getMaxCount();

        while (count > 0) {
            int decrement = Math.min(count, maxCount);

            stacks.add(stack.copyWithCount(decrement));

            count -= decrement;
        }
    }

    public void giveBuildingMaterials(Iterable<? extends ServerPlayerEntity> players, List<? extends Entity> entities) {
        List<ItemStack> stacks = getBuildingMaterials(entities);

        for (ServerPlayerEntity player : players) {
            for (ItemStack stack : stacks) {
                player.getInventory().insertStack(stack.copy());
            }

            PlayerInventoryAccess.setSelectedSlot(player, 0);
        }
    }

    public ItemStack getSourceStack(BlockState state) {
        var properties = state.getProperties();

        if (properties.contains(Properties.SLAB_TYPE)) {
            SlabType slabType = state.get(Properties.SLAB_TYPE);

            if (slabType == SlabType.DOUBLE) {
                return new ItemStack(state.getBlock().asItem(), 2);
            }
        }

        return new ItemStack(state.getBlock().asItem());
    }

    public ItemStack getMaterialStack(BlockState state) {
        var properties = state.getProperties();

        if (properties.contains(Properties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);

            if (half == DoubleBlockHalf.UPPER) {
                return ItemStack.EMPTY;
            }
        }

        if (properties.contains(Properties.BED_PART)) {
            BedPart part = state.get(Properties.BED_PART);

            if (part == BedPart.HEAD) {
                return ItemStack.EMPTY;
            }
        }

        return getSourceStack(state);
    }
}
