package work.lclpnet.ap2.game.mirror_hop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer;
import work.lclpnet.ap2.impl.game.data.IntDataContainer;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.effect.ApEffects;
import work.lclpnet.ap2.impl.util.movement.CooldownMovementBlocker;
import work.lclpnet.ap2.impl.util.movement.MovementBlocker;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.gaco.collisions.ChunkedCollisionDetector;
import work.lclpnet.gaco.collisions.CollisionDetector;
import work.lclpnet.gaco.collisions.movement.PlayerMovementObserver;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.List;
import java.util.Random;

public class MirrorHopInstance extends FFAGameInstance {

    private final OrderedDataContainer<ServerPlayer, PlayerRef> winnerData;
    private final IntDataContainer<ServerPlayer, PlayerRef> scoreData;
    private final CombinedDataContainer<ServerPlayer, PlayerRef> combinedData;
    private final CollisionDetector collisionDetector = new ChunkedCollisionDetector();
    private final PlayerMovementObserver movementObserver;
    private final MovementBlocker movementBlocker;
    private MirrorHopChoices choices = null;
    private int progress = -1;

    public MirrorHopInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        movementObserver = new PlayerMovementObserver(collisionDetector, gameHandle.getParticipants()::isParticipating, true, 0e-5);
        movementBlocker = new CooldownMovementBlocker(gameHandle.getRootScheduler());
        winnerData = new OrderedDataContainer<>(PlayerRef::create);
        scoreData = new IntScoreDataContainer<>(PlayerRef::create);
        combinedData = new CombinedDataContainer<>(List.of(winnerData, scoreData));
    }

    @Override
    protected DataContainer<ServerPlayer, PlayerRef> getData() {
        return combinedData;
    }

    @Override
    protected void prepare() {
        gameHandle.getPlayerUtil().enableEffect(ApEffects.DARKNESS);

        choices = MirrorHopChoices.from(getMap(), gameHandle.getLogger());
        choices.randomize(new Random());
        choices.addColliders(collisionDetector);

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        PlayerTeam team = scoreboardManager.createTeam("team");
        team.setSeeFriendlyInvisibles(true);
        team.setCollisionRule(Team.CollisionRule.NEVER);

        scoreboardManager.joinTeam(gameHandle.getParticipants(), team);

        useTaskDisplay();
    }

    @Override
    protected void go() {
        GameMap map = getMap();
        ServerLevel world = getWorld();
        HookRegistrar hooks = gameHandle.getHooks();
        MinecraftServer server = gameHandle.getServer();

        BlockBox goal = MapUtil.readBox(map.requireProperty("goal"));

        movementObserver.init(hooks, server);

        movementObserver.whenEntering(goal, player -> {
            if (winManager.isGameOver()) return;

            winnerData.add(player);

            winManager.complete();
        });

        movementObserver.setRegionEnterListener((player, collider) -> {
            if (!(collider instanceof MirrorHopChoices.Platform platform)) return;

            int idx = choices.getChoiceIndex(platform);
            if (idx == -1) return;

            if (idx > progress) {
                commons().addScore(player, 1, scoreData);
                progress = idx;
            }

            collisionDetector.remove(collider);

            if (choices.isCorrect(platform, idx)) {
                solidifyPlatform(platform);
            } else {
                breakPlatform(platform);
            }
        });

        movementBlocker.init(hooks);

        commons().whenBelowCriticalHeight().then(this::playerFell);

        removeGate(map, world);
    }

    private void playerFell(ServerPlayer player) {
        gameHandle.getWorldFacade().teleport(player);

        int ticks = Ticks.seconds(4);
        movementBlocker.disableMovement(player, ticks);

        MobEffectInstance invisibility = new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 1, false, false, false);
        player.addEffect(invisibility);
    }

    private static void removeGate(GameMap map, ServerLevel world) {
        var gate = MapUtil.readBox(map.requireProperty("gate"));

        BlockState air = Blocks.AIR.defaultBlockState();

        for (BlockPos pos : gate) {
            world.setBlockAndUpdate(pos, air);
        }
    }

    private void solidifyPlatform(MirrorHopChoices.Platform platform) {
        BlockBox ground = platform.getGround();
        ServerLevel world = getWorld();
        GameMap map = getMap();

        BlockState solid = MapUtil.readBlockState(map.requireProperty("solid_material"));

        for (BlockPos pos : ground) {
            world.setBlockAndUpdate(pos, solid);
        }

        Vec3 center = platform.getGround().getCenter();
        double x = center.x(), y = center.y() + 1, z = center.z();

        world.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.3f, 1);
        world.sendParticles(ParticleTypes.EGG_CRACK, x, y, z, 10, 0.8, 0.5, 0.8, 0.1);
    }

    private void breakPlatform(MirrorHopChoices.Platform platform) {
        BlockBox ground = platform.getGround();
        ServerLevel world = getWorld();

        BlockState air = Blocks.AIR.defaultBlockState();

        for (BlockPos pos : ground) {
            world.setBlockAndUpdate(pos.below(), air);
        }

        Vec3 center = platform.getGround().getCenter();
        double x = center.x(), y = center.y(), z = center.z();

        world.playSound(null, x, y + 1, z, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.BLOCKS, 0.3f, 0);

        var particleEffect = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WHITE_CONCRETE_POWDER.defaultBlockState());
        world.sendParticles(particleEffect, x, y, z, 10, 0.8, 0.5, 0.8, 0.5);
    }
}
