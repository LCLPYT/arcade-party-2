package work.lclpnet.ap2.game.eggventure;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
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
import work.lclpnet.ap2.impl.util.*;
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
import static net.minecraft.ChatFormatting.*;
import static work.lclpnet.ap2.impl.util.ItemHelper.getLeatherArmor;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class EggventureInstance extends FFAGameInstance implements MapBootstrap {

    private static final boolean DEBUG_EGG_POSITIONS = false;
    private static final MapCodec<Boolean> NBT_CODEC = Codec.BOOL.fieldOf("easter_egg");

    private final IntDataContainer<ServerPlayer, PlayerRef> data;
    private final Random random = new Random();
    private final Set<BlockPos> remainingPositions = new HashSet<>();

    public EggventureInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        data = DataContainers.finaleCompatibleScoreContainer(gameHandle, PlayerRef::create);
    }

    @Override
    protected DataContainer<ServerPlayer, PlayerRef> getData() {
        return data;
    }

    @Override
    public @NotNull CompletableFuture<Void> createWorldBootstrap(@NotNull ServerLevel world, @NotNull GameMap map) {
        BlockShape shape = MapUtil.readShape(map, "egg-area");
        List<BlockPos> positions = new ArrayList<>();

        for (BlockPos pos : shape) {
            if (isEasterEgg(world, pos)) {
                positions.add(pos.immutable());
            }
        }

        int minEggs = map.requireProperty("min-eggs");
        int maxEggs = map.requireProperty("max-eggs");
        int eggs = minEggs + random.nextInt(maxEggs - minEggs + 1);

        List<PlayerHead> variants = eggVariants(world.registryAccess());

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
                        -> renderer.marker(pos.getCenter(), Blocks.GREEN_TERRACOTTA.defaultBlockState(), 0x00ff00));
            }

            PlayerHead variant = variants.get(random.nextInt(variants.size()));

            world.getBlockEntity(pos, BlockEntityType.SKULL).ifPresent(variant::apply);

            remainingPositions.add(pos);
        }

        for (BlockPos pos : positions) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE);

            if (DEBUG_EGG_POSITIONS) {
                debugController.renderer().ifPresent(renderer
                        -> renderer.marker(pos.getCenter(), Blocks.BLUE_TERRACOTTA.defaultBlockState(), 0x0000ff));
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    private boolean isEasterEgg(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        if (!state.is(Blocks.PLAYER_HEAD) && !state.is(Blocks.PLAYER_WALL_HEAD)) return false;

        SkullBlockEntity skull = world.getBlockEntity(pos, BlockEntityType.SKULL).orElse(null);

        if (skull == null) return false;

        return CustomNbt.get(skull.components(), NBT_CODEC).orElse(false);
    }

    static @NotNull List<PlayerHead> eggVariants(RegistryAccess registryManager) {
        var headEntries = registryManager
                .lookupOrThrow(ApRegistries.PLAYER_HEAD)
                .getTagOrEmpty(PlayerHeadTags.EASTER_EGGS);

        List<PlayerHead> heads = new ArrayList<>();

        for (var entry : headEntries) {
            heads.add(entry.value());
        }
        return heads;
    }

    @Override
    protected void prepare() {
        new DebugEggsCommand(gameHandle.getLogger()).register(gameHandle.getCommands());

        var variants = eggVariants(getWorld().registryAccess());

        if (variants.isEmpty()) {
            throw new IllegalStateException("There are no egg variants defined");
        }

        for (ServerPlayer player : gameHandle.getParticipants()) {
            PlayerHead variant = variants.get(random.nextInt(variants.size()));
            player.setItemSlot(EquipmentSlot.HEAD, variant.createStack());

            int color = ColorUtil.getRandomHsvColor(random);

            player.setItemSlot(EquipmentSlot.CHEST, getLeatherArmor(Items.LEATHER_CHESTPLATE, color));
            player.setItemSlot(EquipmentSlot.LEGS, getLeatherArmor(Items.LEATHER_LEGGINGS, color));
            player.setItemSlot(EquipmentSlot.FEET, getLeatherArmor(Items.LEATHER_BOOTS, color));
        }

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        Objective objective = scoreboardManager.createObjective("points", ObjectiveCriteria.DUMMY,
                Component.literal("Points").withStyle(YELLOW, BOLD), ObjectiveCriteria.RenderType.INTEGER,
                StyledFormat.PLAYER_LIST_DEFAULT);

        useScoreboardStatsSync(data, objective);

        scoreboardManager.setDisplay(DisplaySlot.LIST, objective);
    }

    @Override
    protected void afterInitialDelay() {
        ServerLevel world = getWorld();
        DynamicEntityManager dynamicEntityManager = new DynamicEntityManager(world);
        var tutorial = new EggventureTutorial(world, dynamicEntityManager, random, gameHandle.getTranslations());

        dynamicEntityManager.init(gameHandle.getScheduler(), gameHandle.getHooks());
        tutorial.start(gameHandle.getScheduler(), gameHandle.getParticipants()).thenRun(super::afterInitialDelay);
    }

    @Override
    protected void go() {
        GameMap map = getMap();
        BlockBox gate = MapUtil.readBox(map.requireProperty("gate"));
        ServerLevel world = getWorld();

        for (BlockPos pos : gate) {
            world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }

        HookRegistrar hooks = gameHandle.getHooks();

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (_player, _world, hand, hitResult) -> {
            BlockPos pos = hitResult.getBlockPos();

            if (_player instanceof ServerPlayer player
                    && gameHandle.getParticipants().isParticipating(player)
                    && hand == InteractionHand.MAIN_HAND
                    && isEasterEgg(world, pos)) {
                onFindEasterEgg(player, pos);
            }

            return InteractionResult.PASS;
        });

        hooks.registerHook(PlayerSwingHandHook.HOOK, (player, hand) -> {
            if (hand != InteractionHand.MAIN_HAND || !gameHandle.getParticipants().isParticipating(player)) return;

            double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);

            HitResult hit = RayCastUtil.raycast(world, player.getEyePosition(), player.getLookAngle(), range,
                    net.minecraft.world.level.ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty(),
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

        gameHandle.getScheduler().interval(20, Ticks.seconds(10), this::checkNearbyEggs);

        CheckpointHelper.setupResetItem(hooks, winManager::isGameOver, player -> gameHandle.getParticipants().isParticipating(player))
                .then(this::reset);

        CheckpointHelper.giveResetItem(gameHandle.getParticipants(), getWorld(), gameHandle.getTranslations(), 4);
    }

    private void reset(ServerPlayer player) {
        gameHandle.getWorldFacade().teleport(player);
    }

    private void checkNearbyEggs() {
        final double checkDistSq = 20 * 20;

        for (ServerPlayer player : gameHandle.getParticipants()) {
            if (remainingPositions.stream().anyMatch(pos -> player
                    .distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < checkDistSq)) continue;

            gameHandle.getTranslations().translateText("game.ap2.eggventure.no_eggs_nearby")
                    .formatted(RED)
                    .sendTo(player, true);
        }
    }

    private void completeAndShowRemaining() {
        if (winManager.isGameOver()) return;

        winManager.complete();

        ServerLevel world = getWorld();

        for (BlockPos pos : remainingPositions) {
            BlockState state = world.getBlockState(pos);
            ItemStack stack = ItemHelper.getStackWithData(world, pos);

            if (!state.is(Blocks.PLAYER_HEAD)) {
                gameHandle.getLogger().warn("Unexpected block: {}", state);
                continue;
            }

            int rotation = state.getValueOrElse(SkullBlock.ROTATION, 0);

            var display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, world);
            display.setItemStack(stack);
            display.setPos(pos.getCenter());
            display.setTransformation(new Transformation(new Matrix4f().rotateY((float) (rotation / -8f * PI))));
            display.setGlowingTag(true);

            world.addFreshEntity(display);
        }

        gameHandle.getTranslations().translateText("game.ap2.eggventure.eggs_left", styled(remainingPositions.size(), YELLOW))
                .formatted(GREEN)
                .sendTo(gameHandle.getParticipants(), true);
    }

    private void onFindEasterEgg(ServerPlayer player, BlockPos pos) {
        if (winManager.isGameOver()) return;

        ServerLevel world = player.level();
        world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);

        commons().addScore(player, 1, data);

        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        world.playSound(null, x, y, z, SoundEvents.ALLAY_THROW, SoundSource.PLAYERS, 1f, 1f);
        player.playNotifySound(SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.75f, 1.2f);

        world.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y, z, 75, 0.25, 0.25, 0.25, 0);
        world.sendParticles(ParticleTypes.WARPED_SPORE, x, y, z, 75, 0.25, 0.25, 0.25, 0);
        world.sendParticles(ParticleTypes.GLOW, x, y, z, 25, 0.5, 0.5, 0.5, 0);

        remainingPositions.remove(pos);
    }
}
