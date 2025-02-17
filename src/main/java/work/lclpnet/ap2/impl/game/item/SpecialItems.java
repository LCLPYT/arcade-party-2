package work.lclpnet.ap2.impl.game.item;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DamageResistantComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
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
import work.lclpnet.ap2.base.util.IconMaker;
import work.lclpnet.ap2.impl.ds.WeightedList;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

import static java.lang.Math.*;
import static java.lang.String.join;
import static net.minecraft.util.math.MathHelper.cos;
import static net.minecraft.util.math.MathHelper.sin;

public class SpecialItems implements SpecialItemContext {

    public static final MapCodec<NbtCompound> NBT_CODEC = NbtCompound.CODEC.fieldOf("ap2:special_item");
    public static final String ID_KEY = "Id";

    private final MiniGameHandle gameHandle;
    private final GameMap map;
    private final ServerWorld world;
    private final Random random;
    private final SpecialItemPositions positions;
    private final SpecialItemRegistry registry;
    private final SpecialItemScene scene;
    private WeightedList<SpecialItem> weightedItems = WeightedList.empty();
    private int despawnTicks = 500;
    private int spawnMinTicks = Ticks.seconds(4);
    private int spawnMaxTicks = Ticks.seconds(8);
    private int maxItems = 16;

    public SpecialItems(MiniGameHandle gameHandle, GameMap map, ServerWorld world, Random random, SpecialItemPositions positions, SpecialItemRegistry registry) {
        this.gameHandle = gameHandle;
        this.map = map;
        this.world = world;
        this.random = random;
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

        spawnMinTicks = max(1, cfg.optNumber("spawn-min-ticks", spawnMinTicks).intValue());
        spawnMaxTicks = max(1, cfg.optNumber("spawn-max-ticks", spawnMaxTicks).intValue());
        despawnTicks = cfg.optNumber("despawn-ticks", despawnTicks).intValue();
        maxItems = cfg.optNumber("max-items", maxItems).intValue();

        positions.init(areaJson, mapSpawn);

        scene.init(gameHandle.getScheduler(), gameHandle.getHookRegistrar());
    }

    public void setup() {
        positions.update();

        scene.onPickup().register(this::pickup);

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(PlayerInventoryHooks.DROP_ITEM, this::onDropItem);

        for (SpecialItem item : registry.entries()) {
            item.registerHooks(hooks, this);
        }

        gameHandle.getGameScheduler().interval(this::tickPickup, 1);
    }

    private boolean onDropItem(PlayerEntity _player, int slotIdx, boolean inInventory) {
        if (!(_player instanceof ServerPlayerEntity player)) return false;

        ItemStack stack;

        if (inInventory) {
            Slot slot = player.currentScreenHandler.getSlot(slotIdx);
            stack = slot != null ? slot.getStack() : ItemStack.EMPTY;
        } else {
            stack = player.getInventory().getStack(slotIdx);
        }

        SpecialItem item = get(stack).orElse(null);

        if (item == null) return false;

        player.getInventory().setStack(8, ItemStack.EMPTY);
        dropSpecialItem(player, item);

        return true;
    }

    private void dropSpecialItem(ServerPlayerEntity player, SpecialItem item) {
        Vec3d pos = player.getEyePos().subtract(0, 0.3, 0);
        SpecialItemObject obj = scene.spawnItem(pos, item, createItemStack(item), gameHandle.getTranslations(), itemName(item));
        obj.setPickupDelay(40);

        scheduleDespawn(obj);

        float pitchSin = sin(player.getPitch() * (float) (Math.PI / 180.0));
        float pitchCos = cos(player.getPitch() * (float) (Math.PI / 180.0));
        float yawSin = sin(player.getYaw() * (float) (Math.PI / 180.0));
        float yawCos = cos(player.getYaw() * (float) (Math.PI / 180.0));
        float randomHorizontalAngle = random.nextFloat() * (float) (Math.PI * 2);
        float divergence = 0.02F * random.nextFloat();

        scene.velocity(obj).set(
                -yawSin * pitchCos * 0.3F + cos(randomHorizontalAngle) * divergence,
                -pitchSin * 0.3F + 0.1F + (random.nextFloat() - random.nextFloat()) * 0.1F,
                yawCos * pitchCos * 0.3F + sin(randomHorizontalAngle) * divergence
        ).mul(20);
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
        SpecialItem item = object.item();

        stack.set(DataComponentTypes.CUSTOM_NAME, itemName(item).translateFor(player));

        List<Text> lore = itemDescription(player, item);

        if (!lore.isEmpty()) {
            stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
        }

        player.getInventory().setStack(8, stack);

        item.onPickedUp(player);

        return true;
    }

    private TranslatedText itemName(SpecialItem item) {
        Identifier gameId = gameHandle.getGameInfo().getId();
        String key = join(".", "item", gameId.getNamespace(), gameId.getPath(), item.id());

        return gameHandle.getTranslations().translateText(key)
                .styled(style -> style.withItalic(false).withFormatting(Rarity.UNCOMMON.getFormatting()));
    }

    private List<Text> itemDescription(ServerPlayerEntity player, SpecialItem item) {
        Identifier gameId = gameHandle.getGameInfo().getId();
        String key = join(".", "item", gameId.getNamespace(), gameId.getPath(), item.id(), "desc");

        if (!gameHandle.getTranslations().getTranslator().hasTranslation("en_us", key)) {
            return List.of();
        }

        RootText desc = gameHandle.getTranslations().translateText(player, key)
                .styled(style -> style.withItalic(false).withFormatting(Formatting.GREEN));

        return IconMaker.wrapText(desc, 32);
    }

    public boolean hasAnySpecialItem(ServerPlayerEntity player) {
        return !hasSpecialItem(player, null);
    }

    @Override
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

    @Override
    public TaskScheduler scheduler() {
        return gameHandle.getGameScheduler();
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
        ItemStack stack = item.createItemStack(world.getRegistryManager());

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

    public void spawnRandomItem() {
        if (scene.itemCount() >= maxItems) return;

        BlockPos blockPos = positions.randomPos(random).orElse(null);
        SpecialItem item = weightedItems.getRandomElement(random);

        if (blockPos == null || item == null) return;

        Vec3d pos = blockPos.toBottomCenterPos();

        if (!world.getWorldBorder().contains(pos)) return;

        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 15, 0.1, 0.1, 0.1, 0.1);

        SpecialItemObject obj = scene.spawnItem(pos, item, createItemStack(item), gameHandle.getTranslations(), itemName(item));

        scheduleDespawn(obj);
    }

    private void scheduleDespawn(SpecialItemObject obj) {
        if (despawnTicks <= 0) return;

        gameHandle.getGameScheduler().timeout(() -> scene.remove(obj), despawnTicks);
    }

    public void spawnPeriodically() {
        gameHandle.getGameScheduler().interval(new Runnable() {
            int timer = 0;
            int next = randomInterval();

            @Override
            public void run() {
                if (timer++ < next) return;

                timer = 0;
                next = randomInterval();
                spawnRandomItem();
            }

            int randomInterval() {
                int minTicks = min(spawnMinTicks, spawnMaxTicks);
                int maxTicks = max(spawnMinTicks, spawnMaxTicks);
                return random.nextInt(maxTicks - minTicks + 1) + minTicks;
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
