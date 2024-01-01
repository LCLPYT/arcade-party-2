package work.lclpnet.ap2.game.jump_and_run;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.ap2.game.jump_and_run.gen.JumpAndRun;
import work.lclpnet.ap2.game.jump_and_run.gen.JumpAndRunSetup;
import work.lclpnet.ap2.impl.game.BootstrapMapOptions;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.ScoreboardManager;
import work.lclpnet.ap2.impl.util.collision.ChunkedCollisionDetector;
import work.lclpnet.ap2.impl.util.collision.PlayerMovementObserver;
import work.lclpnet.kibu.translate.text.FormatWrapper;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.util.Formatting.BOLD;
import static net.minecraft.util.Formatting.YELLOW;

public class JumpAndRunInstance extends DefaultGameInstance {

    private final ScoreTimeDataContainer data = new ScoreTimeDataContainer();
    private final CollisionDetector collisionDetector = new ChunkedCollisionDetector();
    private final PlayerMovementObserver movementObserver = new PlayerMovementObserver(collisionDetector);
    private final List<BlockPos> gateBlocks = new ArrayList<>();
    private JumpAndRun jumpAndRun;

    public JumpAndRunInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer getData() {
        return data;
    }

    @Override
    protected void openMap() {
        MapFacade mapFacade = gameHandle.getMapFacade();
        Identifier gameId = gameHandle.getGameInfo().getId();

        mapFacade.openRandomMap(gameId, new BootstrapMapOptions((world, map) -> {
            world.setTimeOfDay(4000);
            var setup = new JumpAndRunSetup(gameHandle, map, world, 4.0f);  // time in minutes
            return setup.setup().thenAccept(jumpAndRun -> this.jumpAndRun = jumpAndRun);
        }), this::onMapReady);
    }

    @Override
    protected void prepare() {
        MinecraftServer server = gameHandle.getServer();

        GameRules gameRules = getWorld().getGameRules();
        gameRules.get(GameRules.RANDOM_TICK_SPEED).set(0, server);
        gameRules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);

        Participants participants = gameHandle.getParticipants();

        // setup room listeners
        var rooms = jumpAndRun.rooms();

        for (int i = 0, roomsSize = rooms.size(); i < roomsSize; i++) {
            BlockBox room = rooms.get(i);

            collisionDetector.add(room);

            int roomIndex = i;

            movementObserver.whenEntering(room, player -> {
                if (isGameOver() || !participants.isParticipating(player)) return;

                enterRoom(player, roomIndex);
            });
        }

        movementObserver.init(gameHandle.getHookRegistrar(), gameHandle.getServer());

        // close gate until ready
        closeGate();

        // add scoreboard
        ScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        ScoreboardObjective objective = scoreboardManager.createObjective("points", ScoreboardCriterion.DUMMY,
                Text.literal("Points").formatted(YELLOW, BOLD), ScoreboardCriterion.RenderType.INTEGER);

        useScoreboardStatsSync(objective);

        scoreboardManager.setDisplay(Scoreboard.LIST_DISPLAY_SLOT_ID, objective);
    }

    @Override
    protected void ready() {
        openGate();
    }

    private void openGate() {
        ServerWorld world = getWorld();
        BlockState air = Blocks.AIR.getDefaultState();

        for (BlockPos pos : gateBlocks) {
            world.setBlockState(pos, air);
        }
    }

    private void closeGate() {
        BlockBox gate = jumpAndRun.gate();
        ServerWorld world = getWorld();

        BlockState state = Blocks.WHITE_STAINED_GLASS.getDefaultState();

        for (BlockPos pos : gate) {
            if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) continue;

            world.setBlockState(pos, state);
            gateBlocks.add(pos.toImmutable());
        }
    }

    private void enterRoom(ServerPlayerEntity player, int room) {
        if (room <= data.getScore(player)) return;

        data.setScore(player, room);
        player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 2f);

        var msg = gameHandle.getTranslations().translateText(player, "game.ap2.jump_and_run.reached_room",
                        FormatWrapper.styled("#" + room, Formatting.YELLOW))
                .formatted(Formatting.GREEN);

        player.sendMessage(msg);

        if (isGameOver() || room < jumpAndRun.rooms().size() - 1) return;

        win(player);
    }
}
