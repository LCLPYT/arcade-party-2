package work.lclpnet.ap2.game.anvil_fall;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import work.lclpnet.ap2.api.game.GameInfo;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedBossBar;
import work.lclpnet.ap2.impl.util.handler.Visibility;
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler;
import work.lclpnet.ap2.impl.util.handler.VisibilityManager;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.access.entity.FallingBlockAccess;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;
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
    private Vec3 center;

    public AnvilFallInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected void prepare() {
        commons().gameRuleBuilder()
                .set(GameRules.ENTITY_DROPS, false)
                .set(GameRules.FALL_DAMAGE, true);

        scanWorld();

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        PlayerTeam team = scoreboardManager.createTeam("team");
        team.setCollisionRule(Team.CollisionRule.NEVER);

        scoreboardManager.joinTeam(gameHandle.getParticipants(), team);

        var manager = new VisibilityManager(team, Visibility.PARTIALLY_VISIBLE);
        var handler = new VisibilityHandler(manager, gameHandle.getTranslations(), gameHandle.getParticipants());
        handler.init(gameHandle.getHooks());
    }

    @Override
    protected void go() {
        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource) -> {
            if (damageSource.is(DamageTypes.FALLING_ANVIL) && entity instanceof ServerPlayer serverPlayer) {
                onHitByAnvil(serverPlayer);
            }

            return false;
        }));

        setupBossBar();
        startAnvilSpawning();

        PlayerMoveCallback.HOOK.registerWith(gameHandle.getHooks(), this::onPlayerMove);

        for (ServerPlayer player : gameHandle.getParticipants()) {
            repelPlayer(player, player.position());
        }
    }

    private void setupBossBar() {
        GameInfo gameInfo = gameHandle.getGameInfo();
        Translations translations = gameHandle.getTranslations();
        Identifier id = gameInfo.identifier("status");

        String key = "game.ap2.anvil_fall.status";
        Object[] args = new Object[] {FormatWrapper.styled(0, ChatFormatting.YELLOW)};

        TranslatedBossBar bossBar = translations.translateBossBar(id, key, args)
                .with(gameHandle.getBossBarProvider())
                .formatted(ChatFormatting.GREEN);

        amountDisplay = new DynamicTranslatedBossBar(bossBar, key, args);

        bossBar.setColor(BossEvent.BossBarColor.GREEN);

        bossBar.addPlayers(PlayerLookup.all(gameHandle.getServer()));

        gameHandle.getBossBarHandler().showOnJoin(bossBar);
    }

    private void onHitByAnvil(ServerPlayer player) {
        if (!gameHandle.getParticipants().isParticipating(player)) return;

        ServerLevel world = player.level();
        Vec3 pos = player.position();

        double x = pos.x(), y = pos.y(), z = pos.z();

        world.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1f, 0.7f);
        world.sendParticles(ParticleTypes.LAVA, x, y, z, 25, 0.1, 0.1, 0.1, 0f);

        eliminate(player);
    }

    private void scanWorld() {
        ServerLevel world = getWorld();
        GameMap map = getMap();
        BlockBox box = MapUtil.readBox(map.requireProperty("anvil-box"));

        setup = AnvilFallSetup.scanWorld(world, box, random);

        playArea = MapUtil.readBox(map.requireProperty("play-area"));

        BlockPos spawn = MapUtil.readBlockPos(map.requireProperty("spawn"));
        center = new Vec3(spawn.getX() + 0.5, 0, spawn.getZ() + 0.5);
    }

    private void startAnvilSpawning() {
        amountDisplay.setArgument(0, FormatWrapper.styled(20 / INITIAL_DELAY, ChatFormatting.YELLOW));

        gameHandle.getScheduler().interval(new Runnable() {
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
                        Object obj = 20 % delay == 0 ? 20 / delay : "%.2f".formatted(20f / delay);
                        amountDisplay.setArgument(0, FormatWrapper.styled(obj, ChatFormatting.YELLOW));
                    } else {
                        amountDisplay.setArgument(0, FormatWrapper.styled(20, ChatFormatting.YELLOW));
                    }

                    spawnRandomAnvil();

                    return;
                }

                if (time % INCREASE_INTERVAL == 0) {
                    anvilAmount++;
                }

                if (prevAmount != anvilAmount) {
                    prevAmount = anvilAmount;
                    amountDisplay.setArgument(0, FormatWrapper.styled(anvilAmount * 20, ChatFormatting.YELLOW));
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

        ServerLevel world = getWorld();

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
        BlockState state = Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, randomDirection);

        FallingBlockEntity anvil = new FallingBlockEntity(EntityType.FALLING_BLOCK, world);
        anvil.setPosRaw(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        anvil.setSilent(true);
        anvil.time = 1;
        anvil.setHurtsEntities(2.0f, 40);
        FallingBlockAccess.setDropItem(anvil, false);
        FallingBlockAccess.setDestroyedOnLanding(anvil, true);
        FallingBlockAccess.setBlockState(anvil, state);

        world.addFreshEntity(anvil);
    }

    private boolean onPlayerMove(ServerPlayer player, PositionRotation from, PositionRotation to) {
        repelPlayer(player, to);

        return false;
    }

    private void repelPlayer(ServerPlayer player, Position to) {
        if (playArea == null || !gameHandle.getParticipants().isParticipating(player)) return;

        AABB boundingBox = player.getBoundingBox();

        if (playArea.contains(boundingBox.contract(1e-9, 0, 1e-9))) return;

        Vec3 vec = new Vec3(center.x() - to.x(), 0.5, center.z() - to.z());
        VelocityModifier.setVelocity(player, vec.normalize().scale(0.5));
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.ALLAY_HURT, SoundSource.PLAYERS, 0.5f, 2f);
    }
}
