package work.lclpnet.ap2.impl.game.item;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import lombok.Setter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DamageResistantComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.border.WorldBorder;
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
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

import static java.lang.Math.abs;

public class SpecialItems implements SpecialItemContext {

    public static final MapCodec<NbtCompound> NBT_CODEC = NbtCompound.CODEC.fieldOf("ap2:special_item");
    public static final String ID_KEY = "Id";

    private final MiniGameHandle gameHandle;
    private final GameMap map;
    private final ServerWorld world;
    private final SpecialItemPositions positions;
    private final SpecialItemRegistry registry;
    private final SpecialItemScene scene;
    private WeightedList<SpecialItem> weightedItems = WeightedList.empty();
    @Setter private int despawnTicks = 500;

    public SpecialItems(MiniGameHandle gameHandle, GameMap map, ServerWorld world, Random random, SpecialItemPositions positions, SpecialItemRegistry registry) {
        this.gameHandle = gameHandle;
        this.map = map;
        this.world = world;
        this.positions = positions;
        this.registry = registry;
        this.scene = new SpecialItemScene(random, world);
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

        scene.init(gameHandle.getScheduler());
    }

    public void setup() {
        positions.update();

        scene.onPickup().register(this::pickup);

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        for (SpecialItem item : registry.entries()) {
            item.registerHooks(hooks, this);
        }

        gameHandle.getGameScheduler().interval(this::tickPickup, 1);
    }

    private void tickPickup() {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            scene.tickPickUp(player);
        }
    }

    private boolean pickup(ServerPlayerEntity player, SpecialItemObject object) {
        // check if the player already has a special item
        if (hasAnySpecialItem(player)) return false;

        ItemStack stack = object.itemDisplay().getStack().copy();
        player.getInventory().setStack(8, stack);

        object.item().onPickedUp(player);

        return true;
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

        Vec3d pos = blockPos.toBottomCenterPos();

        if (!world.getWorldBorder().contains(pos)) return;

        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 15, 0.1, 0.1, 0.1, 0.1);

        SpecialItemObject obj = scene.spawnItem(pos, item, createItemStack(item));

        if (despawnTicks > 0) {
            gameHandle.getGameScheduler().timeout(() -> scene.remove(obj), despawnTicks);
        }
    }

    public void spawnPeriodically(int minIntervalTicks, int maxIntervalTicks, Random random) {
        gameHandle.getGameScheduler().interval(new Runnable() {
            int timer = 0;
            int next = randomInterval();

            @Override
            public void run() {
                if (timer++ < next) return;

                timer = 0;
                next = randomInterval();
                spawnRandomItem(random);
            }

            int randomInterval() {
                return random.nextInt(maxIntervalTicks - minIntervalTicks + 1);
            }
        }, 1);
    }

    public void syncWithWorldBorder() {
        WorldBorder border = world.getWorldBorder();

        gameHandle.getGameScheduler().interval(new Runnable() {
            double prevSize = Double.NaN, prevCenterX = Double.NaN, prevCenterZ = Double.NaN;

            @Override
            public void run() {
                double size = border.getSize();
                double centerX = border.getCenterX();
                double centerZ = border.getCenterZ();

                if (abs(prevSize - size) < 0.1 || abs(prevCenterX - centerX) < 0.1 || abs(prevCenterZ - centerZ) < 0.1) {
                    prevSize = size;
                    prevCenterX = centerX;
                    prevCenterZ = centerZ;

                    positions.update();
                }
            }
        }, 20);
    }

    public static SpecialItems create(MiniGameHandle gameHandle, GameMap map, ServerWorld world, Random random, Consumer<SpecialItemRegistrar> config) {
        var validSpawn = BlockPredicate.and(gameHandle.getWorldBorderManager().getWorldBorder()::contains, new WalkableBlockPredicate(world));

        return create(gameHandle, map, world, random, validSpawn, config);
    }

    public static SpecialItems create(MiniGameHandle gameHandle, GameMap map, ServerWorld world, Random random, BlockPredicate validSpawn, Consumer<SpecialItemRegistrar> config) {
        var debugController = new DebugController();

        if (ApConstants.DEBUG) {
            debugController.init(ApResources.getInstance(), world);
        }

        var positions = new SpecialItemPositions(validSpawn, debugController);
        var registry = new SpecialItemRegistry();

        config.accept(registry);

        var specialItems = new SpecialItems(gameHandle, map, world, random, positions, registry);

        specialItems.init();

        return specialItems;
    }
}
