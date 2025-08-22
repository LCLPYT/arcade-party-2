package work.lclpnet.ap2.game.cozy_campfire.setup;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.*;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.core.type.ApFuelRegistry;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class CCFuel {

    private final Set<Block> breakableBlocks = new HashSet<>();
    private final Object2IntMap<Item> fuel = new Object2IntOpenHashMap<>();
    private final ServerWorld world;
    private final CCBaseManager baseManager;

    public CCFuel(ServerWorld world, CCBaseManager baseManager) {
        this.world = world;
        this.baseManager = baseManager;
    }

    public void registerFuel(int fuelPerSecond) {
        // vanilla materials
        FuelRegistry fuelRegistry = world.getFuelRegistry();
        ApFuelRegistry fuelAccess = (ApFuelRegistry) fuelRegistry;

        for (Item item : fuelRegistry.getFuelItems()) {
            int fuelTicks = fuelAccess.ap2$getFuelTicks(item);

            if (fuelTicks > 0) {
                fuel.put(item, fuelTicks);
            }
        }

        // custom materials
        addFuel(ItemTags.LEAVES, 50);
        addFuel(ItemTags.BEDS, 2200);
        fuel.put(Items.VINE, 40);
        fuel.put(Items.BEEHIVE, 250);
        fuel.put(Items.BOOKSHELF, 400);
        fuel.put(Items.CHISELED_BOOKSHELF, 410);
        fuel.put(Items.TARGET, 160);
        addFuel(ItemTags.WOODEN_DOORS, 350);

        // adjust vanilla values
        fuel.put(Items.CHARCOAL, 530);
        fuel.put(Items.COAL, 530);
        fuel.put(Items.DRIED_KELP_BLOCK, fuelPerSecond * 4);

        fuel.keySet().stream()
                .map(item -> item instanceof BlockItem bi ? bi : null)
                .filter(Objects::nonNull)
                .map(BlockItem::getBlock)
                .forEach(breakableBlocks::add);

        breakableBlocks.add(Blocks.FIRE);

        transform(fuelPerSecond);
    }

    private void addFuel(TagKey<Item> tag, int time) {
        for (var entry : Registries.ITEM.iterateEntries(tag)) {
            if (entry.isIn(ItemTags.NON_FLAMMABLE_WOOD)) continue;

            Item item = entry.value();

            fuel.put(item, time);
        }
    }

    private void transform(int fuelPerSecond) {
        int lowerBound = fuelPerSecond / 2;  // at least 0.5 seconds
        int upperBound = fuelPerSecond * 5;  // at most 5 seconds

        fuel.forEach((item, value) -> {
            int clamped = MathHelper.clamp(value, lowerBound, upperBound);

            fuel.put(item, clamped);
        });
    }

    public boolean isFuel(ServerPlayerEntity player, BlockPos pos) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;

        // prevent breaking fuel blocks in bases
        if (baseManager.isInAnyBase(x, y, z)) {
            return false;
        }

        BlockState state = world.getBlockState(pos);

        if (breakableBlocks.contains(state.getBlock())) return true;

        ItemStack stack = player.getMainHandStack();
        BlockEntity blockEntity = world.getBlockEntity(pos);

        LootWorldContext.Builder builder = new LootWorldContext.Builder(world)
                .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(pos))
                .add(LootContextParameters.TOOL, stack)
                .addOptional(LootContextParameters.THIS_ENTITY, player)
                .addOptional(LootContextParameters.BLOCK_ENTITY, blockEntity);

        return state.getDroppedStacks(builder).stream().anyMatch(this::isFuel);
    }

    public boolean isFuel(ItemStack stack) {
        return fuel.containsKey(stack.getItem());
    }

    public int getValue(ItemStack stack) {
        return fuel.getOrDefault(stack.getItem(), 0) * stack.getCount();
    }
}
