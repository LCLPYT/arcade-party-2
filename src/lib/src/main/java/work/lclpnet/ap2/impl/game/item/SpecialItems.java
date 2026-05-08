package work.lclpnet.ap2.impl.game.item;

import com.mojang.serialization.MapCodec;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.util.world.BlockPredicate;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.CustomNbt;
import work.lclpnet.ap2.impl.util.IconMaker;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.gaco.ds.WeightedList;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.hook.player.PlayerSwingHandHook;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

import static java.lang.Math.*;
import static java.lang.String.join;
import static net.minecraft.util.Mth.cos;
import static net.minecraft.util.Mth.sin;

public class SpecialItems implements SpecialItemContext {

    public static final MapCodec<CompoundTag> NBT_CODEC = CompoundTag.CODEC.fieldOf("ap2:special_item");
    public static final String ID_KEY = "Id";
    private static final int ITEM_PARTICLE_MIN_TICKS = 22, ITEM_PARTICLE_MAX_TICKS = 38;

    private final MiniGameHandle gameHandle;
    private final GameMap map;
    private final ServerLevel level;
    private final Random random;
    private final SpecialItemPositions positions;
    private final SpecialItemRegistry registry;
    private final SpecialItemScene scene;
    private WeightedList<SpecialItem> weightedItems = WeightedList.empty();
    private @Setter @Getter int despawnTicks = 600;
    private @Setter @Getter int spawnMinTicks = Ticks.seconds(4);
    private @Setter @Getter int spawnMaxTicks = Ticks.seconds(7);
    private @Setter @Getter int maxItems = 16;
    private @Setter @Getter boolean markGlowing = false;

    public SpecialItems(MiniGameHandle gameHandle, GameMap map, ServerLevel level, Random random, SpecialItemPositions positions, SpecialItemRegistry registry) {
        this.gameHandle = gameHandle;
        this.map = map;
        this.level = level;
        this.random = random;
        this.positions = positions;
        this.registry = registry;
        this.scene = new SpecialItemScene(random, level);
    }

    public SpecialItemPositions positions() {
        return positions;
    }

    public void init() {
        JSONObject cfg = map.requireProperty("items");
        JSONObject overrides = cfg.optJSONObject("overrides");

        weightedItems = registry.weightedItems(overrides != null ? overrides : new JSONObject());

        spawnMinTicks = max(1, cfg.optNumber("spawn-min-ticks", spawnMinTicks).intValue());
        spawnMaxTicks = max(1, cfg.optNumber("spawn-max-ticks", spawnMaxTicks).intValue());
        despawnTicks = cfg.optNumber("despawn-ticks", despawnTicks).intValue();
        maxItems = cfg.optNumber("max-items", maxItems).intValue();

        BlockShape shape = getSpawnArea(map);

        positions.setShape(shape);

        scene.init(gameHandle.getRootScheduler(), gameHandle.getHooks());
    }

    public static @NotNull BlockShape getSpawnArea(GameMap map) {
        BlockPos mapSpawn = BlockPos.containing(MapUtils.getSpawnPosition(map));

        JSONObject cfg = map.requireProperty("items");
        JSONObject areaJson = cfg.getJSONObject("spawn-area");

        return MapUtil.readShape(areaJson, mapSpawn);
    }

    public void setup() {
        positions.update();

        scene.onPickup().register(this::pickup);

        HookRegistrar hooks = gameHandle.getHooks();
        TaskScheduler scheduler = gameHandle.getScheduler();

        PlayerInventoryHooks.DROP_ITEM.registerWith(hooks, this::onDropItem);
        PlayerInteractionHooks.USE_ITEM.registerWith(hooks, this::interact);
        PlayerInventoryHooks.SWAP_HANDS.registerWith(hooks, this::swapHands);
        PlayerSwingHandHook.HOOK.registerWith(hooks, this::onSwingHand);

        for (SpecialItem item : registry.entries()) {
            item.registerHooks(hooks, this);
            item.scheduleTasks(scheduler, this);
        }

        scheduler.interval(this::tickPickup, 1);
    }

    private boolean swapHands(ServerPlayer player, int i) {
        ItemStack stack = player.getInventory().getItem(8);
        SpecialItem item = get(stack).orElse(null);

        if (item == null) return false;

        InteractionResult result = useItem(player, stack, item, null);

        return result != InteractionResult.PASS;
    }

    private boolean onDropItem(Player _player, int slotIdx, boolean inInventory) {
        if (!(_player instanceof ServerPlayer player)) return false;

        ItemStack stack;

        if (inInventory) {
            Slot slot = player.containerMenu.getSlot(slotIdx);
            stack = slot.getItem();
        } else {
            stack = player.getInventory().getItem(slotIdx);
        }

        SpecialItem item = get(stack).orElse(null);

        if (item == null) return false;

        if (!item.canBeDropped(player, stack)) return true;

        player.getInventory().setItem(8, ItemStack.EMPTY);
        dropSpecialItem(player, item, stack);
        item.onDropped(player);

        return true;
    }

    private void dropSpecialItem(ServerPlayer player, SpecialItem item, ItemStack stack) {
        Vec3 pos = player.getEyePosition().subtract(0, 0.3, 0);

        ItemStack dropStack = configureStack(item, item.usedItemStack(stack, level.registryAccess()));

        SpecialItemObject obj = scene.spawnItem(pos, item, dropStack, gameHandle.getTranslations(), itemName(item));
        obj.setPickupDelay(40);

        scheduleDespawn(obj);

        float pitchSin = sin(player.getXRot() * (float) (Math.PI / 180.0));
        float pitchCos = cos(player.getXRot() * (float) (Math.PI / 180.0));
        float yawSin = sin(player.getYRot() * (float) (Math.PI / 180.0));
        float yawCos = cos(player.getYRot() * (float) (Math.PI / 180.0));
        float randomHorizontalAngle = random.nextFloat() * (float) (Math.PI * 2);
        float divergence = 0.02F * random.nextFloat();

        scene.velocity(obj).set(
                -yawSin * pitchCos * 0.3F + cos(randomHorizontalAngle) * divergence,
                -pitchSin * 0.3F + 0.1F + (random.nextFloat() - random.nextFloat()) * 0.1F,
                yawCos * pitchCos * 0.3F + sin(randomHorizontalAngle) * divergence
        ).mul(20);
    }

    private InteractionResult interact(Player p, Level w, InteractionHand hand) {
        if (!(p instanceof ServerPlayer player)) return InteractionResult.PASS;

        ItemStack stack = p.getItemInHand(hand);
        SpecialItem item = get(stack).orElse(null);

        if (item == null) return InteractionResult.PASS;

        return useItem(player, stack, item, hand);
    }

    private InteractionResult useItem(ServerPlayer player, ItemStack stack, SpecialItem item, @Nullable InteractionHand hand) {
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;

        return item.onUse(player, stack, hand, this);
    }

    private void onSwingHand(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        SpecialItem item = get(stack).orElse(null);

        if (item == null || player.getCooldowns().isOnCooldown(stack)) return;

        item.onSwing(player, stack, hand, this);
    }

    private void tickPickup() {
        for (ServerPlayer player : gameHandle.getParticipants()) {
            scene.tickPickUp(player);
        }
    }

    private boolean pickup(ServerPlayer player, SpecialItemObject object) {
        SpecialItem item = object.item();

        // check if the player already has a special item
        if (hasAnySpecialItem(player) && item.shouldTransferToInventory(player)) return false;

        if (!item.canBePickedUp(player)) return false;

        ItemStack stack = object.itemDisplay().getStack().copy();

        stack.set(DataComponents.CUSTOM_NAME, itemName(item).translateFor(player));

        itemDescription(player, item).ifPresent(desc -> {
            List<Component> lore = IconMaker.wrapText(desc, 32);
            stack.set(DataComponents.LORE, new ItemLore(lore));

            player.sendOverlayMessage(Component.literal("↓ ").withStyle(ChatFormatting.AQUA).append(desc));
        });

        if (item.shouldTransferToInventory(player)) {
            player.getInventory().setItem(8, stack);
        }

        item.onPickedUp(player, stack, this);

        return true;
    }

    private TranslatedText itemName(SpecialItem item) {
        Identifier gameId = gameHandle.getGameInfo().getId();
        String key = join(".", "game", gameId.getNamespace(), gameId.getPath(), "item", item.id());

        return gameHandle.getTranslations().translateText(key)
                .styled(style -> style.withItalic(false).applyFormat(Rarity.UNCOMMON.color()));
    }

    private Optional<Component> itemDescription(ServerPlayer player, SpecialItem item) {
        Identifier gameId = gameHandle.getGameInfo().getId();
        String key = join(".", "game", gameId.getNamespace(), gameId.getPath(), "item", item.id(), "desc");

        if (!gameHandle.getTranslations().getTranslator().hasTranslation("en_us", key)) {
            return Optional.empty();
        }

        return Optional.of(gameHandle.getTranslations().translateText(player, key)
                .styled(style -> style.withItalic(false).applyFormat(ChatFormatting.GREEN)));
    }

    public boolean hasAnySpecialItem(ServerPlayer player) {
        return !hasSpecialItem(player, null);
    }

    @Override
    public boolean hasSpecialItem(ServerPlayer player, @Nullable SpecialItem item) {
        return get(player.getInventory().getItem(8)).orElse(null) == item;
    }

    @Override
    public void removeSpecialItem(ServerPlayer player, SpecialItem item) {
        if (item == null) return;

        SpecialItem currentItem = get(player.getInventory().getItem(8)).orElse(null);

        if (currentItem == item) {
            player.getInventory().setItem(8, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean isSpecialItem(ItemStack stack, @Nullable SpecialItem item) {
        return get(stack).orElse(null) == item;
    }

    @Override
    public TaskScheduler scheduler() {
        return gameHandle.getScheduler();
    }

    @Override
    public Translations translations() {
        return gameHandle.getTranslations();
    }

    public Optional<SpecialItem> get(ItemStack stack) {
        CompoundTag nbt = CustomNbt.get(stack, NBT_CODEC).orElse(null);

        if (nbt == null) {
            return Optional.empty();
        }

        String id = nbt.getStringOr(ID_KEY, "");

        return registry.get(id);
    }

    private ItemStack configureStack(SpecialItem item, ItemStack stack) {
        // persist special item id in the stack
        var nbt = new CompoundTag();
        nbt.putString(ID_KEY, item.id());

        CustomNbt.set(stack, NBT_CODEC, nbt);

        stack.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(level.registryAccess().getOrThrow(DamageTypeTags.IS_FIRE)));
        stack.set(DataComponents.RARITY, Rarity.UNCOMMON);

        return stack;
    }

    public void spawnRandomItem() {
        if (scene.itemCount() >= maxItems) return;

        WorldBorder worldBorder = level.getWorldBorder();
        BlockPos blockPos;
        int i = 0;

        do {
            blockPos = positions.randomPos(random).orElse(null);

            if (blockPos == null) return;
        } while (++i < 16 && !worldBorder.isWithinBounds(blockPos));

        Vec3 pos = blockPos.getBottomCenter();

        if (!worldBorder.isWithinBounds(pos)) return;

        SpecialItem item = weightedItems.getRandomElement(random);

        if (item == null) return;

        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 20, 0.1, 0.1, 0.1, 0.1);
        level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 400, 0.15, 4, 0.15, 0.1);

        ItemStack stack = configureStack(item, item.createItemStack(level.registryAccess()));

        SpecialItemObject obj = scene.spawnItem(pos, item, stack, gameHandle.getTranslations(), itemName(item));

        if (markGlowing) {
            obj.setGlowing(true);
        }

        scheduleDespawn(obj);
    }

    private void scheduleDespawn(SpecialItemObject obj) {
        if (despawnTicks <= 0) return;

        gameHandle.getScheduler().interval(new SchedulerAction() {
            int timer = 0;
            int particle = 0;

            @Override
            public void run(RunningTask task) {
                if (!scene.contains(obj)) {
                    task.cancel();
                    return;
                }

                int t = timer++;

                if (t == particle) {
                    particle = timer + ITEM_PARTICLE_MIN_TICKS + random.nextInt(ITEM_PARTICLE_MAX_TICKS - ITEM_PARTICLE_MIN_TICKS + 1);
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, obj.position.x, obj.position.y + 0.125, obj.position.z, 1, 0.35, 0.25, 0.35, 0.1);
                }

                if (t >= despawnTicks) {
                    scene.remove(obj);
                    task.cancel();
                }
            }
        }, 1);
    }

    public void spawnPeriodically() {
        gameHandle.getScheduler().interval(new Runnable() {
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
        WorldBorder border = level.getWorldBorder();

        gameHandle.getScheduler().interval(new Runnable() {
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

    public static SpecialItems create(MiniGameHandle gameHandle, GameMap map, ServerLevel world, Random random,
                                      DebugController debugController, Consumer<SpecialItemRegistrar> config) {

        var validSpawn = BlockPredicate.and(gameHandle.getWorldBorderManager().getWorldBorder()::isWithinBounds, new WalkableBlockPredicate(world));

        return create(gameHandle, map, world, random, validSpawn, debugController, config);
    }

    public static SpecialItems create(MiniGameHandle gameHandle, GameMap map, ServerLevel world, Random random,
                                      BlockPredicate validSpawn, DebugController debugController,
                                      Consumer<SpecialItemRegistrar> config) {

        var positions = new SpecialItemPositions(validSpawn, debugController);
        var registry = new SpecialItemRegistry();

        config.accept(registry);

        var specialItems = new SpecialItems(gameHandle, map, world, random, positions, registry);

        specialItems.init();

        return specialItems;
    }
}
