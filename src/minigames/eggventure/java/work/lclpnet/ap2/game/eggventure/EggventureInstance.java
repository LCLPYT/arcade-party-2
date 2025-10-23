package work.lclpnet.ap2.game.eggventure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.util.heads.PlayerHead;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.DataContainers;
import work.lclpnet.ap2.impl.game.data.IntDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.tags.PlayerHeadTags;
import work.lclpnet.ap2.impl.util.ApRegistries;
import work.lclpnet.ap2.impl.util.ColorUtil;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.ap2.impl.util.RayCastUtil;
import work.lclpnet.ap2.impl.util.checkpoint.CheckpointHelper;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.player.PlayerSwingHandHook;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.Math.PI;
import static net.minecraft.util.Formatting.*;
import static work.lclpnet.ap2.impl.util.ItemHelper.getLeatherArmor;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class EggventureInstance extends FFAGameInstance implements MapBootstrap {

    private static final boolean DEBUG_EGG_POSITIONS = false;
    private static final MapCodec<Boolean> NBT_CODEC = Codec.BOOL.fieldOf("easter_egg");

    private final IntDataContainer<ServerPlayerEntity, PlayerRef> data;
    private final Random random = new Random();
    private final Set<BlockPos> remainingPositions = new HashSet<>();

    public EggventureInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        data = DataContainers.finaleCompatibleScoreContainer(gameHandle, PlayerRef::create);
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    public @NotNull CompletableFuture<Void> createWorldBootstrap(@NotNull ServerWorld world, @NotNull GameMap map) {
        BlockShape shape = MapUtil.readShape(map, "egg-area");
        List<BlockPos> positions = new ArrayList<>();

        for (BlockPos pos : shape) {
            if (isEasterEgg(world, pos)) {
                positions.add(pos.toImmutable());
            }
        }

        int minEggs = map.requireProperty("min-eggs");
        int maxEggs = map.requireProperty("max-eggs");
        int eggs = minEggs + random.nextInt(maxEggs - minEggs + 1);

        List<PlayerHead> variants = eggVariants(world.getRegistryManager());

        if (variants.isEmpty()) {
            throw new IllegalStateException("There are no egg variants defined");
        }

        if (ApConstants.DEBUG) {
            gameHandle.getLogger().info("There are {} possible egg positions and {} should be placed", positions.size(), eggs);
        }

        var debugController = commons(map, world).debugController();

        for (int i = 0; i < eggs && !positions.isEmpty(); i++) {
            BlockPos pos = positions.remove(random.nextInt(positions.size()));

            if (DEBUG_EGG_POSITIONS) {
                debugController.renderer().ifPresent(renderer
                        -> renderer.marker(pos.toCenterPos(), Blocks.GREEN_TERRACOTTA.getDefaultState(), 0x00ff00));
            }

            PlayerHead variant = variants.get(random.nextInt(variants.size()));

            world.getBlockEntity(pos, BlockEntityType.SKULL).ifPresent(variant::apply);

            remainingPositions.add(pos);
        }

        for (BlockPos pos : positions) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.SKIP_DROPS | Block.FORCE_STATE);

            if (DEBUG_EGG_POSITIONS) {
                debugController.renderer().ifPresent(renderer
                        -> renderer.marker(pos.toCenterPos(), Blocks.BLUE_TERRACOTTA.getDefaultState(), 0x0000ff));
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    private boolean isEasterEgg(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        if (!state.isOf(Blocks.PLAYER_HEAD) && !state.isOf(Blocks.PLAYER_WALL_HEAD)) return false;

        SkullBlockEntity skull = world.getBlockEntity(pos, BlockEntityType.SKULL).orElse(null);

        if (skull == null) return false;

        return skull.getComponents()
                .getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .get(NBT_CODEC)
                .result()
                .orElse(false);
    }

    static @NotNull List<PlayerHead> eggVariants(DynamicRegistryManager registryManager) {
        var headEntries = registryManager
                .getOrThrow(ApRegistries.PLAYER_HEAD)
                .iterateEntries(PlayerHeadTags.EASTER_EGGS);

        List<PlayerHead> heads = new ArrayList<>();

        for (var entry : headEntries) {
            heads.add(entry.value());
        }
        return heads;
    }

    @Override
    protected void prepare() {
        new DebugEggsCommand(gameHandle.getLogger()).register(gameHandle.getCommands());

        var variants = eggVariants(getWorld().getRegistryManager());

        if (variants.isEmpty()) {
            throw new IllegalStateException("There are no egg variants defined");
        }

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            PlayerHead variant = variants.get(random.nextInt(variants.size()));
            player.equipStack(EquipmentSlot.HEAD, variant.createStack());

            int color = ColorUtil.getRandomHsvColor(random);

            player.equipStack(EquipmentSlot.CHEST, getLeatherArmor(Items.LEATHER_CHESTPLATE, color));
            player.equipStack(EquipmentSlot.LEGS, getLeatherArmor(Items.LEATHER_LEGGINGS, color));
            player.equipStack(EquipmentSlot.FEET, getLeatherArmor(Items.LEATHER_BOOTS, color));
        }

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        ScoreboardObjective objective = scoreboardManager.createObjective("points", ScoreboardCriterion.DUMMY,
                Text.literal("Points").formatted(YELLOW, BOLD), ScoreboardCriterion.RenderType.INTEGER,
                StyledNumberFormat.YELLOW);

        useScoreboardStatsSync(data, objective);

        scoreboardManager.setDisplay(ScoreboardDisplaySlot.LIST, objective);
    }

    @Override
    protected void afterInitialDelay() {
        ServerWorld world = getWorld();
        DynamicEntityManager dynamicEntityManager = new DynamicEntityManager(world);
        var tutorial = new EggventureTutorial(world, dynamicEntityManager, random, gameHandle.getTranslations());

        dynamicEntityManager.init(gameHandle.getGameScheduler(), gameHandle.getHooks());
        tutorial.start(gameHandle.getGameScheduler(), gameHandle.getParticipants()).thenRun(super::afterInitialDelay);
    }

    @Override
    protected void ready() {
        GameMap map = getMap();
        BlockBox gate = MapUtil.readBox(map.requireProperty("gate"));
        ServerWorld world = getWorld();

        for (BlockPos pos : gate) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
        }

        HookRegistrar hooks = gameHandle.getHooks();

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (_player, _world, hand, hitResult) -> {
            BlockPos pos = hitResult.getBlockPos();

            if (_player instanceof ServerPlayerEntity player
                    && gameHandle.getParticipants().isParticipating(player)
                    && hand == Hand.MAIN_HAND
                    && isEasterEgg(world, pos)) {
                onFindEasterEgg(player, pos);
            }

            return ActionResult.PASS;
        });

        hooks.registerHook(PlayerSwingHandHook.HOOK, (player, hand) -> {
            if (hand != Hand.MAIN_HAND || !gameHandle.getParticipants().isParticipating(player)) return;

            double range = player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE);

            HitResult hit = RayCastUtil.raycast(world, player.getEyePos(), player.getRotationVector(), range,
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, ShapeContext.absent(),
                    entity -> !entity.isSpectator());

            if (!(hit instanceof BlockHitResult blockHit)) return;

            BlockPos pos = blockHit.getBlockPos();

            if (isEasterEgg(world, pos)) {
                onFindEasterEgg(player, pos);
            }
        });

        int minDurationSeconds = map.requireProperty("min-duration-seconds");
        int maxDurationSeconds = map.requireProperty("max-duration-seconds");
        int durationSeconds = minDurationSeconds + random.nextInt(maxDurationSeconds - minDurationSeconds + 1);

        var subject = gameHandle.getTranslations().translateText(gameHandle.getGameInfo().getTaskKey());

        commons().createTimer(subject, durationSeconds).whenDone(this::completeAndShowRemaining);

        gameHandle.getGameScheduler().interval(20, Ticks.seconds(10), this::checkNearbyEggs);

        CheckpointHelper.setupResetItem(hooks, winManager::isGameOver, player -> gameHandle.getParticipants().isParticipating(player))
                .then(this::reset);

        CheckpointHelper.giveResetItem(gameHandle.getParticipants(), getWorld(), gameHandle.getTranslations(), 4);
    }

    private void reset(ServerPlayerEntity player) {
        gameHandle.getWorldFacade().teleport(player);
    }

    private void checkNearbyEggs() {
        final double checkDistSq = 20 * 20;

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            if (remainingPositions.stream().anyMatch(pos -> player
                    .squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < checkDistSq)) continue;

            gameHandle.getTranslations().translateText("game.ap2.eggventure.no_eggs_nearby")
                    .formatted(RED)
                    .sendTo(player, true);
        }
    }

    private void completeAndShowRemaining() {
        if (winManager.isGameOver()) return;

        winManager.complete();

        ServerWorld world = getWorld();

        for (BlockPos pos : remainingPositions) {
            BlockState state = world.getBlockState(pos);
            ItemStack stack = ItemHelper.getStackWithData(world, pos);

            if (!state.isOf(Blocks.PLAYER_HEAD)) {
                gameHandle.getLogger().warn("Unexpected block: {}", state);
                continue;
            }

            int rotation = state.get(SkullBlock.ROTATION, 0);

            var display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
            display.setItemStack(stack);
            display.setPosition(pos.toCenterPos());
            display.setTransformation(new AffineTransformation(new Matrix4f().rotateY((float) (rotation / -8f * PI))));
            display.setGlowing(true);

            world.spawnEntity(display);
        }

        gameHandle.getTranslations().translateText("game.ap2.eggventure.eggs_left", styled(remainingPositions.size(), YELLOW))
                .formatted(GREEN)
                .sendTo(gameHandle.getParticipants(), true);
    }

    private void onFindEasterEgg(ServerPlayerEntity player, BlockPos pos) {
        if (winManager.isGameOver()) return;

        ServerWorld world = player.getWorld();
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.SKIP_DROPS | Block.FORCE_STATE | Block.NOTIFY_LISTENERS);

        commons().addScore(player, 1, data);

        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        world.playSound(null, x, y, z, SoundEvents.ENTITY_ALLAY_ITEM_THROWN, SoundCategory.PLAYERS, 1f, 1f);
        player.playSoundToPlayer(SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.75f, 1.2f);

        world.spawnParticles(ParticleTypes.CRIMSON_SPORE, x, y, z, 75, 0.25, 0.25, 0.25, 0);
        world.spawnParticles(ParticleTypes.WARPED_SPORE, x, y, z, 75, 0.25, 0.25, 0.25, 0);
        world.spawnParticles(ParticleTypes.GLOW, x, y, z, 25, 0.5, 0.5, 0.5, 0);

        remainingPositions.remove(pos);
    }
}
