package work.lclpnet.ap2.game.speed_builders;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidFillable;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.game.speed_builders.data.SbIsland;
import work.lclpnet.ap2.game.speed_builders.data.SbModule;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.behaviour.world.ServerWorldBehaviour;
import work.lclpnet.kibu.hook.entity.ItemFrameRemoveItemCallback;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.world.BlockModificationHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.util.BossBarTimer;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class SpeedBuildersInstance extends EliminationGameInstance implements MapBootstrap {

    private static final int BUILD_DURATION_SECONDS = 30;
    private static final int LOOK_DURATION_TICKS = Ticks.seconds(10);
    private static final int JUDGE_DURATION_TICKS = Ticks.seconds(6);
    private final Random random;
    private final SpeedBuilderSetup setup;
    private final SpeedBuilderItems items;
    private SpeedBuildersManager manager = null;

    public SpeedBuildersInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        random = new Random();
        setup = new SpeedBuilderSetup(random, gameHandle.getLogger());
        items = new SpeedBuilderItems();

        useSurvivalMode();
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        return setup.setup(map, world).thenRun(() -> {
            Participants participants = gameHandle.getParticipants();

            var islands = setup.createIslands(participants, world);

            manager = new SpeedBuildersManager(islands, setup.getModules(), gameHandle, world, random);
        });
    }

    @Override
    protected void prepare() {
        setupGameRules();

        ServerWorld world = getWorld();
        ServerWorldBehaviour.setFluidTicksEnabled(world, false);

        manager.eachIsland(SbIsland::teleport);

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        Team team = scoreboardManager.createTeam("team");
        team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
        manager.setTeam(team);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            PlayerAbilities abilities = player.getAbilities();
            abilities.allowFlying = true;
            abilities.flying = true;
            player.sendAbilitiesUpdate();

            scoreboardManager.joinTeam(player, team);
        }
    }

    private void setupGameRules() {
        GameRules gameRules = getWorld().getGameRules();
        MinecraftServer server = gameHandle.getServer();

        gameRules.get(GameRules.RANDOM_TICK_SPEED).set(0, server);
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> {
            config.allow((entity, pos) -> entity instanceof ServerPlayerEntity player && canModify(player, pos),
                    ProtectionTypes.PLACE_BLOCKS, ProtectionTypes.BREAK_BLOCKS, ProtectionTypes.USE_BLOCK,
                    ProtectionTypes.PLACE_FLUID, ProtectionTypes.EAT_CAKE, ProtectionTypes.COMPOSTER,
                    ProtectionTypes.CHARGE_RESPAWN_ANCHOR, ProtectionTypes.EXTINGUISH_CANDLE,
                    ProtectionTypes.PICKUP_FLUID);

            config.allow((player, itemFrame) -> player instanceof ServerPlayerEntity serverPlayer && canModify(serverPlayer, itemFrame.getBlockPos()),
                    ProtectionTypes.ITEM_FRAME_SET_ITEM, ProtectionTypes.ITEM_FRAME_ROTATE_ITEM,
                    ProtectionTypes.ITEM_FRAME_REMOVE_ITEM);

            config.allow(ProtectionTypes.USE_ITEM_ON_BLOCK,
                    (entity, pos) -> entity instanceof ServerPlayerEntity player && canModify(player, pos.up()));

            config.allow(ProtectionTypes.MODIFY_INVENTORY);

            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, source) -> {
                if (entity instanceof ServerPlayerEntity ||
                    !(source.getAttacker() instanceof ServerPlayerEntity player) ||
                    !canModify(player, entity.getBlockPos())) return false;

                ItemStack stack = items.getEntitySummon(entity);

                // remove the entity when a player hit it
                entity.discard();

                giveStack(player, stack);

                return false;
            });
        });

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(PlayerInteractionHooks.ATTACK_BLOCK, (player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && canModify(serverPlayer, pos)) {
                destroyBlock(world, pos, serverPlayer);
            }

            return ActionResult.PASS;
        });

        hooks.registerHook(PlayerInteractionHooks.BREAK_BLOCK, (world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && canModify(serverPlayer, pos)) {
                giveSourceItem(serverPlayer, state);
            }

            return true;
        });

        hooks.registerHook(BlockModificationHooks.PLACE_FLUID, (world, pos, entity, fluid) -> {
            if (entity instanceof ServerPlayerEntity player && canModify(player, pos)) {
                placeFluid(world, player, pos, fluid);
            }

            return true;
        });

        hooks.registerHook(ItemFrameRemoveItemCallback.HOOK, (itemFrame, attacker) -> {
            ItemStack stack = itemFrame.getHeldItemStack();

            if (attacker instanceof ServerPlayerEntity player && !stack.isEmpty()) {
                giveStack(player, stack.copy());
            }

            return false;  // do not cancel, so the item frame is destroyed etc.
        });

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (player, world, hand, hitResult) -> {
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            if (state.isIn(BlockTags.BUTTONS)) {
                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        });

        nextRound();
    }

    private void placeFluid(World world, ServerPlayerEntity player, BlockPos pos, Fluid fluid) {
        if (!(fluid instanceof FlowableFluid flowableFluid)) return;

        BlockState state = world.getBlockState(pos);

        if (state.getBlock() instanceof FluidFillable fluidFillable && fluid == Fluids.WATER) {
            fluidFillable.tryFillWithFluid(world, pos, state, flowableFluid.getStill(false));
            this.playEmptyingSound(player, world, pos, fluid);
            return;
        } else if (!state.isAir()) {
            return;
        }

        if (world.setBlockState(pos, fluid.getDefaultState().getBlockState(), Block.NOTIFY_ALL_AND_REDRAW)) {
            this.playEmptyingSound(player, world, pos, fluid);
        }
    }

    private void playEmptyingSound(@Nullable PlayerEntity player, WorldAccess world, BlockPos pos, Fluid fluid) {
        RegistryEntry<Fluid> entry = Registries.FLUID.getEntry(fluid);

        SoundEvent soundEvent = entry != null && entry.isIn(FluidTags.LAVA) ? SoundEvents.ITEM_BUCKET_EMPTY_LAVA : SoundEvents.ITEM_BUCKET_EMPTY;
        world.playSound(player, pos, soundEvent, SoundCategory.BLOCKS, 1.0f, 1.0f);
        world.emitGameEvent(player, GameEvent.FLUID_PLACE, pos);
    }

    private void destroyBlock(World world, BlockPos pos, ServerPlayerEntity player) {
        gameHandle.getGameScheduler().timeout(() -> {
            BlockState state = world.getBlockState(pos);

            if (state.isAir()) return;

            world.syncWorldEvent(null, WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));

            if (!world.setBlockState(pos, Blocks.AIR.getDefaultState())) return;

            giveSourceItem(player, state);
        }, 1);
    }

    private void giveSourceItem(ServerPlayerEntity player, BlockState state) {
        ItemStack stack = items.getSourceStack(state);

        if (stack.isEmpty()) return;

        giveStack(player, stack);
    }

    private void giveStack(ServerPlayerEntity player, ItemStack stack) {
        PlayerInventory inventory = player.getInventory();

        if (inventory.getOccupiedSlotWithRoomForStack(stack) == -1 && player.getMainHandStack().isEmpty()) {
            inventory.insertStack(inventory.selectedSlot, stack);
        } else {
            inventory.insertStack(stack);
        }
    }

    private boolean canModify(ServerPlayerEntity player, BlockPos pos) {
        return manager.isBuildingPhase() &&
               gameHandle.getParticipants().isParticipating(player) &&
               manager.isWithinBuildingArea(player, pos);
    }

    private void nextRound() {
        SbModule module = manager.nextModule();
        manager.setModule(module);
        items.setModule(module);

        commons().announceSubtitle("game.ap2.speed_builders.look");

        gameHandle.getGameScheduler().timeout(this::startBuilding, LOOK_DURATION_TICKS);
    }

    private void startBuilding() {
        commons().announceSubtitle("game.ap2.speed_builders.copy");

        manager.setBuildingPhase(true);
        items.giveBuildingMaterials(gameHandle.getParticipants(), manager.getPreviewEntities());

        TranslationService translations = gameHandle.getTranslations();

        TranslatedText label = translations.translateText("game.ap2.speed_builders.label");
        BossBarTimer timer = commons().createTimer(label, BUILD_DURATION_SECONDS);

        timer.whenDone(this::onRoundOver);
    }

    private void onRoundOver() {
        commons().announce("game.ap2.speed_builders.time_up", "game.ap2.speed_builders.grade");

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            player.getInventory().clear();
        }

        manager.setBuildingPhase(false);

        gameHandle.getGameScheduler().timeout(this::eliminateWorst, JUDGE_DURATION_TICKS);
    }

    private void eliminateWorst() {
        nextRound();
    }
}
