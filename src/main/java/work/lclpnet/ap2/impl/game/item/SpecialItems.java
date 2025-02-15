package work.lclpnet.ap2.impl.game.item;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DamageResistantComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.util.world.BlockPredicate;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.resource.ApResources;
import work.lclpnet.ap2.impl.ds.WeightedList;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

public class SpecialItems implements SpecialItemContext {

    public static final MapCodec<NbtCompound> NBT_CODEC = NbtCompound.CODEC.fieldOf("ap2:special_item");
    public static final String ID_KEY = "Id";

    private final MiniGameHandle gameHandle;
    private final GameMap map;
    private final ServerWorld world;
    private final SpecialItemPositions positions;
    private final SpecialItemRegistry registry;
    private WeightedList<SpecialItem> weightedItems = WeightedList.empty();

    public SpecialItems(MiniGameHandle gameHandle, GameMap map, ServerWorld world, SpecialItemPositions positions, SpecialItemRegistry registry) {
        this.gameHandle = gameHandle;
        this.map = map;
        this.world = world;
        this.positions = positions;
        this.registry = registry;
    }

    public SpecialItemPositions positions() {
        return positions;
    }

    public void init() {
        JSONObject cfg = map.requireProperty("items");

        JSONObject overrides = cfg.optJSONObject("overrides");

        weightedItems = registry.weightedItems(overrides != null ? overrides : new JSONObject());

        JSONObject areaJson = cfg.getJSONObject("spawn-area");
        BlockPos mapSpawn = BlockPos.ofFloored(MapUtils.getSpawnPosition(map));

        positions.init(areaJson, mapSpawn);
    }

    public void setup() {
        positions.update();

        HookRegistrar hooks = gameHandle.getHookRegistrar();
        hooks.registerHook(PlayerInventoryHooks.PLAYER_PICKUP, this::onPickup);

        for (SpecialItem item : registry.entries()) {
            item.registerHooks(hooks, this);
        }
    }

    private boolean onPickup(PlayerEntity player, ItemEntity itemEntity) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !gameHandle.getParticipants().isParticipating(serverPlayer)) {
            return false;
        }

        SpecialItem specialItem = get(itemEntity.getStack()).orElse(null);

        if (specialItem == null) {
            return false;
        }

        tryPickupSpecialItem(serverPlayer, itemEntity, specialItem);

        return true;
    }

    /** The default pickup handler, if there is no override */
    private void tryPickupSpecialItem(ServerPlayerEntity player, ItemEntity itemEntity, SpecialItem specialItem) {
        // check if the player already has a special item
        if (hasAnySpecialItem(player)) return;

        ItemStack stack = itemEntity.getStack().copy();
        player.getInventory().setStack(8, stack);
        player.sendPickup(itemEntity, itemEntity.getStack().getCount());
        itemEntity.discard();

        specialItem.onPickedUp(player);
    }

    public boolean hasAnySpecialItem(ServerPlayerEntity player) {
        return !hasSpecialItem(player, null);
    }

    public boolean hasSpecialItem(ServerPlayerEntity player, @Nullable SpecialItem item) {
        return get(player.getInventory().getStack(8)).orElse(null) == item;
    }

    @Override
    public void removeSpecialItem(ServerPlayerEntity player, SpecialItem item) {
        if (item == null) return;

        SpecialItem currentItem = get(player.getInventory().getStack(8)).orElse(null);

        if (currentItem == item) {
            player.getInventory().setStack(8, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean isSpecialItem(ItemStack stack, @Nullable SpecialItem item) {
        return get(stack).orElse(null) == item;
    }

    public Optional<SpecialItem> get(ItemStack stack) {
        NbtComponent component = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        DataResult<NbtCompound> res = component.get(NBT_CODEC);
        NbtCompound nbt = res.resultOrPartial().orElse(null);

        if (nbt == null) {
            return Optional.empty();
        }

        String id = nbt.getString(ID_KEY);

        return registry.get(id);
    }

    public ItemStack createItemStack(SpecialItem item) {
        ItemStack stack = item.createItemStack();

        // persist special item id in the stack
        var nbt = new NbtCompound();
        nbt.putString(ID_KEY, item.id());

        stack.set(DataComponentTypes.CUSTOM_DATA, stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .with(NbtOps.INSTANCE, NBT_CODEC, nbt)
                .getOrThrow());

        stack.set(DataComponentTypes.DAMAGE_RESISTANT, new DamageResistantComponent(DamageTypeTags.IS_FIRE));
        stack.set(DataComponentTypes.RARITY, Rarity.UNCOMMON);

        return stack;
    }

    public void spawnRandomItem(Random random) {
        BlockPos blockPos = positions.randomPos(random).orElse(null);
        SpecialItem item = weightedItems.getRandomElement(random);

        if (blockPos == null || item == null) return;

        ItemStack stack = createItemStack(item);

        var itemEntity = new ItemEntity(EntityType.ITEM, world);
        itemEntity.setPosition(blockPos.toBottomCenterPos());
        itemEntity.setStack(stack);
        itemEntity.setNeverDespawn();

        world.spawnEntity(itemEntity);
    }

    public static SpecialItems create(MiniGameHandle gameHandle, GameMap map, ServerWorld world, Consumer<SpecialItemRegistrar> config) {
        return create(gameHandle, map, world, new WalkableBlockPredicate(world), config);
    }

    public static SpecialItems create(MiniGameHandle gameHandle, GameMap map, ServerWorld world, BlockPredicate validSpawn, Consumer<SpecialItemRegistrar> config) {
        var debugController = new DebugController();

        if (ApConstants.DEBUG) {
            debugController.init(ApResources.getInstance(), world);
        }

        var positions = new SpecialItemPositions(validSpawn, debugController);
        var registry = new SpecialItemRegistry();

        config.accept(registry);

        var specialItems = new SpecialItems(gameHandle, map, world, positions, registry);

        specialItems.init();

        return specialItems;
    }
}
