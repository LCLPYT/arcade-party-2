package work.lclpnet.ap2.game.speed_builders;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.game.speed_builders.data.SbIsland;
import work.lclpnet.ap2.game.speed_builders.data.SbModule;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
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
    private SpeedBuildersManager manager = null;

    public SpeedBuildersInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        random = new Random();
        setup = new SpeedBuilderSetup(random, gameHandle.getLogger());

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
                    ProtectionTypes.PLACE_BLOCKS, ProtectionTypes.BREAK_BLOCKS, ProtectionTypes.USE_BLOCK);

            config.allow(ProtectionTypes.USE_ITEM_ON_BLOCK,
                    (entity, pos) -> entity instanceof ServerPlayerEntity player && canModify(player, pos.up()));

            config.allow(ProtectionTypes.MODIFY_INVENTORY);
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

        nextRound();
    }

    private void destroyBlock(World world, BlockPos pos, ServerPlayerEntity player) {
        gameHandle.getGameScheduler().timeout(() -> {
            BlockState state = world.getBlockState(pos);

            if (state.isAir()) return;

            world.syncWorldEvent(null, WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));

            if (!world.removeBlock(pos, false)) return;

            giveSourceItem(player, state);
        }, 1);
    }

    private void giveSourceItem(ServerPlayerEntity player, BlockState state) {
        ItemStack stack = manager.getSourceStack(state);

        if (stack.isEmpty()) return;

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

        commons().announceSubtitle("game.ap2.speed_builders.look");

        gameHandle.getGameScheduler().timeout(this::startBuilding, LOOK_DURATION_TICKS);
    }

    private void startBuilding() {
        commons().announceSubtitle("game.ap2.speed_builders.copy");

        manager.setBuildingPhase(true);
        manager.giveBuildingMaterials(gameHandle.getParticipants());

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
