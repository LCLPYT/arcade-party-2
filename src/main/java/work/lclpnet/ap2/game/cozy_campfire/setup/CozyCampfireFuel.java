package work.lclpnet.ap2.game.cozy_campfire.setup;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class CozyCampfireFuel {

    private final Set<Block> breakableBlocks = new HashSet<>();
    private final Map<Item, Integer> fuel = new HashMap<>();
    private final ServerWorld world;
    private final CozyCampfireBaseManager baseManager;

    public CozyCampfireFuel(ServerWorld world, CozyCampfireBaseManager baseManager) {
        this.world = world;
        this.baseManager = baseManager;
    }

    public void registerFuel() {
        // vanilla materials
        fuel.putAll(AbstractFurnaceBlockEntity.createFuelTimeMap());

        // custom materials
        addFuel(ItemTags.LEAVES, 50);
        addFuel(ItemTags.BEDS, 400);
        fuel.put(Items.VINE, 40);
        fuel.put(Items.BEEHIVE, 250);
        fuel.put(Items.BOOKSHELF, 400);
        fuel.put(Items.CHISELED_BOOKSHELF, 410);
        fuel.put(Items.TARGET, 160);
        addFuel(ItemTags.WOODEN_DOORS, 350);

        fuel.keySet().stream()
                .map(item -> item instanceof BlockItem bi ? bi : null)
                .filter(Objects::nonNull)
                .map(BlockItem::getBlock)
                .forEach(breakableBlocks::add);

        breakableBlocks.add(Blocks.FIRE);
        breakableBlocks.add(Blocks.POWDER_SNOW);
    }

    private void addFuel(TagKey<Item> tag, int time) {
        for (var entry : Registries.ITEM.iterateEntries(tag)) {
            if (entry.isIn(ItemTags.NON_FLAMMABLE_WOOD)) continue;

            Item item = entry.value();

            fuel.put(item, time);
        }
    }

    public boolean isFuel(ServerPlayerEntity player, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        if (state.isIn(BlockTags.CAMPFIRES)) {
            double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;

            // prevent breaking campfires in bases
            if (baseManager.isInAnyBase(x, y, z)) {
                return false;
            }
        }

        if (breakableBlocks.contains(state.getBlock())) return true;

        ItemStack stack = player.getMainHandStack();
        BlockEntity blockEntity = world.getBlockEntity(pos);

        LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(world)
                .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(pos))
                .add(LootContextParameters.TOOL, stack)
                .addOptional(LootContextParameters.THIS_ENTITY, player)
                .addOptional(LootContextParameters.BLOCK_ENTITY, blockEntity);

        return state.getDroppedStacks(builder).stream().anyMatch(this::isFuel);
    }

    public boolean isFuel(ItemStack stack) {
        return fuel.containsKey(stack.getItem());
    }
}
