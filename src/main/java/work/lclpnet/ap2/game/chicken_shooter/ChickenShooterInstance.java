package work.lclpnet.ap2.game.chicken_shooter;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.ItemStackHelper;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.entity.ProjectileCanHitCallback;
import work.lclpnet.kibu.hook.entity.ProjectileHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static net.minecraft.util.Formatting.YELLOW;

public class ChickenShooterInstance extends DefaultGameInstance implements Runnable {

    private static final double BABY_CHANCE = 0.07;
    private static final double TNT_CHANCE = 0.07;
    private static final double TNT_RADIUS = 6;
    private static final int MIN_DURATION = 40;
    private static final int MAX_DURATION = 60;
    private final Random random = new Random();
    private final int durationSeconds = MIN_DURATION + random.nextInt(MAX_DURATION - MIN_DURATION + 1);
    private final ScoreTimeDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreTimeDataContainer<>(PlayerRef::create);
    private final Set<ChickenEntity> chickenSet = new HashSet<>();
    private BlockBox chickenBox = null;
    private int despawnHeight = 0;
    private int time = 0;

    public ChickenShooterInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected ScoreTimeDataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        ServerWorld world = getWorld();

        GameRules gameRules = world.getGameRules();
        gameRules.get(GameRules.DO_ENTITY_DROPS).set(false, null);
        gameRules.get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, null);

        despawnHeight = getMap().requireProperty("despawn-height");

        // hooks
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, (entity, source, amount) -> {
            if (!(source.getSource() instanceof ProjectileEntity projectile)
                || !(entity instanceof ChickenEntity chicken)) return false;

            projectile.discard();

            if (winManager.isGameOver() || !(source.getAttacker() instanceof ServerPlayerEntity attacker)) return false;

            float pitch = chicken.isBaby() ? 1.4f : 0.8f;
            attacker.playSoundToPlayer(SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.8f, pitch);

            int score = killChicken(chicken, attacker, world);

            commons().addScore(attacker, score, data);

            return false;
        });

        hooks.registerHook(ProjectileHooks.HIT_BLOCK, (projectile, hit) -> projectile.discard());

        // projectiles can only hit chickens (will pass through players)
        hooks.registerHook(ProjectileCanHitCallback.HOOK, (projectile, entity) -> entity instanceof ChickenEntity);

        // Setup Scoreboard
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        var objective = scoreboardManager.translateObjective("score", "game.ap2.chicken_shooter.points")
                .formatted(YELLOW, Formatting.BOLD);

        useScoreboardStatsSync(data, objective);
        objective.setSlot(ScoreboardDisplaySlot.LIST);
        objective.setNumberFormat(StyledNumberFormat.YELLOW);

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            objective.addPlayer(player);
        }

        Team team = scoreboardManager.createTeam("team");
        team.setShowFriendlyInvisibles(true);
        team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            scoreboardManager.joinTeam(player, team);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, Integer.MAX_VALUE, 1, false, false, false));
        }
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource)
                -> damageSource.getSource() instanceof ProjectileEntity && entity instanceof ChickenEntity));

        TranslationService translations = gameHandle.getTranslations();
        giveBowsToPlayers(translations);

        chickenSpawner();

        // Timer and game end
        var subject = translations.translateText("game.ap2.chicken_shooter.task");

        commons().createTimer(subject, durationSeconds).whenDone(this::onTimerDone);
    }

    private void chickenSpawner() {
        JSONArray spawnBounds = getMap().requireProperty("spawn-bounds");
        chickenBox = MapUtil.readBox(spawnBounds);
        gameHandle.getGameScheduler().interval(this, 1);
    }

    private void spawnChicken() {
        ServerWorld world = getWorld();
        BlockPos.Mutable randomPos = new BlockPos.Mutable();
        chickenBox.randomBlockPos(randomPos, random);

        ChickenEntity chicken = new ChickenEntity(EntityType.CHICKEN, world);

        if (random.nextFloat() < BABY_CHANCE) {
            chicken.setBaby(true);
        } else if (random.nextFloat() < TNT_CHANCE) {
            spawnTNT(chicken, world);
        }

        chicken.setPos(randomPos.getX() + 0.5, randomPos.getY(), randomPos.getZ() + 0.5);
        world.spawnEntity(chicken);

        chickenSet.add(chicken);
    }


    private void spawnTNT(ChickenEntity chicken, ServerWorld world) {
        TntEntity tnt = new TntEntity(EntityType.TNT, world);
        tnt.setFuse(Integer.MAX_VALUE);
        tnt.startRiding(chicken, true);
        world.spawnEntity(tnt);
    }

    private int killChicken(ChickenEntity chicken, ServerPlayerEntity attacker, ServerWorld world) {
        int score = 0;

        double x = chicken.getX();
        double y = chicken.getY();
        double z = chicken.getZ();

        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 8, 0.4, 0.4, 0.4, 0.2);

        TntEntity tnt = chicken.getFirstPassenger() instanceof TntEntity t ? t : null;

        chicken.discard();
        chickenSet.remove(chicken);

        if (tnt != null) {
            score += tntExplode(chicken, tnt, world, attacker, x, y, z);
        }

        if (chicken.isBaby()) score += 3;
        else score += 1;

        return score;
    }

    private int tntExplode(ChickenEntity chicken, TntEntity tnt, ServerWorld world, ServerPlayerEntity attacker, double x, double y, double z) {
        world.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 4, 0.5, 0.5, 0.5, 1);
        attacker.playSoundToPlayer(SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 0.8f, 1.8f);
        attacker.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.8f, 0.7f);

        Vec3d tntPos = tnt.getPos();

        int score = 0;
        var affected = world.getEntitiesByType(TypeFilter.instanceOf(ChickenEntity.class), chickenEntity -> tntPos.isInRange(chickenEntity.getPos(), TNT_RADIUS));

        for (ChickenEntity c : affected) {
            if (c == chicken) continue;
            score += killChicken(c, attacker, world);
        }

        tnt.discard();

        return score;
    }

    private void giveBowsToPlayers(TranslationService translations) {
        var infinity = ItemStackHelper.getEnchantment(Enchantments.INFINITY, getWorld().getRegistryManager());

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            ItemStack stack = new ItemStack(Items.BOW);

            stack.addEnchantment(infinity, 1);
            stack.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
            stack.set(DataComponentTypes.CUSTOM_NAME, translations.translateText(player, "game.ap2.chicken_shooter.bow")
                    .styled(style -> style.withItalic(false).withFormatting(Formatting.GOLD)));

            PlayerInventory inventory = player.getInventory();
            inventory.setStack(4, stack);

            PlayerInventoryAccess.setSelectedSlot(player, 4);

            inventory.setStack(9, new ItemStack(Items.ARROW));
        }
    }

    @Override
    public void run() {
        if (time % 10 == 0) {
            spawnChicken();
        }

        time++;

        chickenSet.removeIf(chicken -> {
            double x = chicken.getX() + 0.5;
            double y = chicken.getY();
            double z = chicken.getZ() + 0.5;

            if (y < despawnHeight) {
                if (chicken.getFirstPassenger() instanceof TntEntity tnt) {
                    tnt.discard();
                }

                chicken.discard();
                getWorld().spawnParticles(ParticleTypes.CLOUD, x, y, z, 3, 0.2, 0.2, 0.2, 0.02);
                return true;
            }

            return false;
        });
    }

    private void onTimerDone() {
        winManager.win(data.getBestSubject(resolver).orElse(null));
    }
}
