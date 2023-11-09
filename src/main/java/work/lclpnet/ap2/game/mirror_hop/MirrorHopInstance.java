package work.lclpnet.ap2.game.mirror_hop;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.ScoreboardManager;
import work.lclpnet.ap2.impl.util.collision.ChunkedCollisionDetector;
import work.lclpnet.ap2.impl.util.collision.PlayerMovementObserver;
import work.lclpnet.ap2.impl.util.effect.ApEffects;
import work.lclpnet.ap2.impl.util.movement.CooldownMovementBlocker;
import work.lclpnet.ap2.impl.util.movement.MovementBlocker;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;

public class MirrorHopInstance extends DefaultGameInstance {

    private final ScoreDataContainer data = new ScoreDataContainer();
    private final CollisionDetector collisionDetector = new ChunkedCollisionDetector();
    private final PlayerMovementObserver movementObserver = new PlayerMovementObserver(collisionDetector);
    private final MovementBlocker movementBlocker;
    private MirrorHopChoices choices = null;
    private int progress = -1;

    public MirrorHopInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
        movementBlocker = new CooldownMovementBlocker(gameHandle.getScheduler());
    }

    @Override
    protected DataContainer getData() {
        return data;
    }

    @Override
    protected void prepare() {
        gameHandle.getPlayerUtil().enableEffect(ApEffects.DARKNESS);

        choices = MirrorHopChoices.from(getMap(), gameHandle.getLogger());
        choices.randomize(new Random());
        choices.addColliders(collisionDetector);

        ScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        Team team = scoreboardManager.createTeam("team");
        team.setShowFriendlyInvisibles(true);

        scoreboardManager.joinTeam(gameHandle.getParticipants(), team);
    }

    @Override
    protected void ready() {
        GameMap map = getMap();
        ServerWorld world = getWorld();
        HookRegistrar hooks = gameHandle.getHookRegistrar();
        Participants participants = gameHandle.getParticipants();
        MinecraftServer server = gameHandle.getServer();

        BlockBox goal = MapUtil.readBox(map.requireProperty("goal"));

        movementObserver.init(hooks, server);

        movementObserver.whenEntering(goal, player -> {
            if (!participants.isParticipating(player) || isGameOver()) return;

            data.addScore(player, 1);

            win(player);
        });

        movementObserver.setRegionEnterListener((player, collider) -> {
            if (!(participants.isParticipating(player))
                || !(collider instanceof MirrorHopChoices.Platform platform)) return;

            int idx = choices.getChoiceIndex(platform);
            if (idx == -1) return;

            if (!choices.isCorrect(platform, idx)) {
                collisionDetector.remove(collider);

                breakPlatform(platform);
                return;
            }

            int score = idx + 1;

            if (score > data.getScore(player)) {
                data.setScore(player, score);
            }

            if (progress >= idx) return;

            progress = idx;
            solidifyPlatform(platform);
        });

        movementBlocker.init(hooks);

        Number criticalHeight = getMap().getProperty("critical-height");

        if (criticalHeight != null) {
            hooks.registerHook(PlayerMoveCallback.HOOK, (player, from, to) -> {
                if (!participants.isParticipating(player)) return false;

                if (!(to.getY() >= criticalHeight.floatValue())) {
                    playerFell(player);
                }

                return false;
            });
        }

        removeGate(map, world);
    }

    private void playerFell(ServerPlayerEntity player) {
        gameHandle.getWorldFacade().teleport(player);

        int ticks = Ticks.seconds(4);
        movementBlocker.disableMovement(player, ticks);

        StatusEffectInstance invisibility = new StatusEffectInstance(StatusEffects.INVISIBILITY, ticks, 1, false, false, false);
        player.addStatusEffect(invisibility);
    }

    private static void removeGate(GameMap map, ServerWorld world) {
        var gate = MapUtil.readBox(map.requireProperty("gate"));

        BlockState air = Blocks.AIR.getDefaultState();

        for (BlockPos pos : gate) {
            world.setBlockState(pos, air);
        }
    }

    private void solidifyPlatform(MirrorHopChoices.Platform platform) {
        BlockBox ground = platform.getGround();
        ServerWorld world = getWorld();
        GameMap map = getMap();

        BlockState solid = MapUtil.readBlockState(map.requireProperty("solid_material"));

        for (BlockPos pos : ground) {
            world.setBlockState(pos, solid);
        }

        Vec3d center = platform.getGround().getCenter();
        double x = center.getX(), y = center.getY() + 1, z = center.getZ();

        world.playSound(null, x, y, z, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 0.3f, 1);
        world.spawnParticles(ParticleTypes.EGG_CRACK, x, y, z, 10, 0.8, 0.5, 0.8, 0.1);
    }

    private void breakPlatform(MirrorHopChoices.Platform platform) {
        BlockBox ground = platform.getGround();
        ServerWorld world = getWorld();

        BlockState air = Blocks.AIR.getDefaultState();

        for (BlockPos pos : ground) {
            world.setBlockState(pos.down(), air);
        }

        Vec3d center = platform.getGround().getCenter();
        double x = center.getX(), y = center.getY(), z = center.getZ();

        world.playSound(null, x, y + 1, z, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.BLOCKS, 0.3f, 0);

        var particleEffect = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.WHITE_CONCRETE_POWDER.getDefaultState());
        world.spawnParticles(particleEffect, x, y, z, 10, 0.8, 0.5, 0.8, 0.5);
    }
}
