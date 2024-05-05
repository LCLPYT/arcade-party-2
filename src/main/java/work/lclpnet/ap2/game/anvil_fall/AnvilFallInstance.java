package work.lclpnet.ap2.game.anvil_fall;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.game.GameInfo;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedBossBar;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.access.entity.FallingBlockAccess;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar;
import work.lclpnet.kibu.translate.text.FormatWrapper;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;

public class AnvilFallInstance extends EliminationGameInstance {

    public static final double DIRECT_ANVIL_CHANCE = 0.02;
    public static final int SPREAD_RADIUS = 8;
    public static final int INITIAL_DELAY = 5;
    public static final int INITIAL_DELAY_DECREASE_INTERVAL = Ticks.seconds(2);
    public static final int INCREASE_INTERVAL = Ticks.seconds(8);
    private final Direction[] directions = new Direction[] {Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.WEST};
    private final Random random = new Random();
    private DynamicTranslatedBossBar amountDisplay = null;
    private AnvilFallSetup setup;
    private BlockBox playArea = null;
    private Vec3d center;

    public AnvilFallInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected void prepare() {
        GameRules gameRules = getWorld().getGameRules();
        gameRules.get(GameRules.DO_ENTITY_DROPS).set(false, null);
        gameRules.get(GameRules.FALL_DAMAGE).set(true, null);

        scanWorld();

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        Team team = scoreboardManager.createTeam("team");
        team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);

        scoreboardManager.joinTeam(gameHandle.getParticipants(), team);
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource) -> {
            if (damageSource.isOf(DamageTypes.FALLING_ANVIL) && entity instanceof ServerPlayerEntity serverPlayer) {
                onHitByAnvil(serverPlayer);
            }

            return false;
        }));

        setupBossBar();
        startAnvilSpawning();

        gameHandle.getHookRegistrar().registerHook(PlayerMoveCallback.HOOK, this::onPlayerMove);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            repelPlayer(player, player.getPos());
        }
    }

    private void setupBossBar() {
        GameInfo gameInfo = gameHandle.getGameInfo();
        TranslationService translations = gameHandle.getTranslations();
        Identifier id = gameInfo.identifier("status");

        String key = "game.ap2.anvil_fall.status";
        Object[] args = new Object[] {FormatWrapper.styled(0, Formatting.YELLOW)};

        TranslatedBossBar bossBar = translations.translateBossBar(id, key, args)
                .with(gameHandle.getBossBarProvider())
                .formatted(Formatting.GREEN);

        amountDisplay = new DynamicTranslatedBossBar(bossBar, key, args);

        bossBar.setColor(BossBar.Color.GREEN);

        bossBar.addPlayers(PlayerLookup.all(gameHandle.getServer()));

        gameHandle.getBossBarHandler().showOnJoin(bossBar);
    }

    private void onHitByAnvil(ServerPlayerEntity player) {
        if (!gameHandle.getParticipants().isParticipating(player)) return;

        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos();

        double x = pos.getX(), y = pos.getY(), z = pos.getZ();

        world.playSound(null, x, y, z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1f, 0.7f);
        world.spawnParticles(ParticleTypes.LAVA, x, y, z, 25, 0.1, 0.1, 0.1, 0f);

        eliminate(player);
    }

    private void scanWorld() {
        ServerWorld world = getWorld();
        GameMap map = getMap();
        BlockBox box = MapUtil.readBox(map.requireProperty("anvil-box"));

        setup = AnvilFallSetup.scanWorld(world, box, random);

        playArea = MapUtil.readBox(map.requireProperty("play-area"));

        BlockPos spawn = MapUtil.readBlockPos(map.requireProperty("spawn"));
        center = new Vec3d(spawn.getX() + 0.5, 0, spawn.getZ() + 0.5);
    }

    private void startAnvilSpawning() {
        amountDisplay.setArgument(0, FormatWrapper.styled(20 / INITIAL_DELAY, Formatting.YELLOW));

        gameHandle.getGameScheduler().interval(new Runnable() {
            int delay = INITIAL_DELAY;
            int cooldown = 0;
            int anvilAmount = 1;
            int timer = 0;
            int prevAmount = 0;

            @Override
            public void run() {
                int time = ++timer;

                if (delay > 0) {
                    if (time % INITIAL_DELAY_DECREASE_INTERVAL == 0) {
                        if (--delay == 0) {
                            timer = 0;
                        }
                    }

                    if (cooldown > 0) {
                        cooldown--;
                        return;
                    }

                    cooldown = delay;

                    if (delay > 0) {
                        Object obj = 20 % delay == 0 ? 20 / delay : String.format("%.2f", 20f / delay);
                        amountDisplay.setArgument(0, FormatWrapper.styled(obj, Formatting.YELLOW));
                    } else {
                        amountDisplay.setArgument(0, FormatWrapper.styled(20, Formatting.YELLOW));
                    }

                    spawnRandomAnvil();

                    return;
                }

                if (time % INCREASE_INTERVAL == 0) {
                    anvilAmount++;
                }

                if (prevAmount != anvilAmount) {
                    prevAmount = anvilAmount;
                    amountDisplay.setArgument(0, FormatWrapper.styled(anvilAmount * 20, Formatting.YELLOW));
                }

                final int count = Math.min(anvilAmount, 256);

                for (int i = 0; i < count; i++) {
                    spawnRandomAnvil();
                }
            }
        }, 1);
    }

    private void spawnRandomAnvil() {
        if (winManager.isGameOver()) return;

        ServerWorld world = getWorld();

        // bias position towards a uniformly random chosen participant
        BlockPos pos = gameHandle.getParticipants().getRandomParticipant(random)
                .map(player -> {
                    if (random.nextFloat() < DIRECT_ANVIL_CHANCE) {
                        return setup.getRandomPositionAt(player.getBlockX(), player.getBlockZ());
                    }

                    return setup.getRandomPosition(player.getBlockX(), player.getBlockZ(), SPREAD_RADIUS);
                })
                .orElseGet(setup::getRandomPosition);

        Direction randomDirection = directions[random.nextInt(directions.length)];
        BlockState state = Blocks.ANVIL.getDefaultState().with(AnvilBlock.FACING, randomDirection);

        FallingBlockEntity anvil = new FallingBlockEntity(EntityType.FALLING_BLOCK, world);
        anvil.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        anvil.setSilent(true);
        anvil.timeFalling = 1;
        anvil.setHurtEntities(2.0f, 40);
        FallingBlockAccess.setDropItem(anvil, false);
        FallingBlockAccess.setDestroyedOnLanding(anvil, true);
        FallingBlockAccess.setBlockState(anvil, state);

        world.spawnEntity(anvil);
    }

    private boolean onPlayerMove(ServerPlayerEntity player, PositionRotation from, PositionRotation to) {
        repelPlayer(player, to);

        return false;
    }

    private void repelPlayer(ServerPlayerEntity player, Position to) {
        if (playArea == null || !gameHandle.getParticipants().isParticipating(player)) return;

        Box boundingBox = player.getBoundingBox();

        if (playArea.contains(boundingBox.shrink(1e-9, 0, 1e-9))) return;

        Vec3d vec = new Vec3d(center.getX() - to.getX(), 0.5, center.getZ() - to.getZ());
        VelocityModifier.setVelocity(player, vec.normalize().multiply(0.5));
        player.playSoundToPlayer(SoundEvents.ENTITY_ALLAY_HURT, SoundCategory.PLAYERS, 0.5f, 2f);
    }
}
