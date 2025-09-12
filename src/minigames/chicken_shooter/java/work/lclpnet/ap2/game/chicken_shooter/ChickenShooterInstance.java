package work.lclpnet.ap2.game.chicken_shooter;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.ChickenVariant;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
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
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.stats.CommonStats;
import work.lclpnet.ap2.api.stats.FFAStatsManager;
import work.lclpnet.ap2.api.stats.Stat;
import work.lclpnet.ap2.api.stats.StatsDisplay;
import work.lclpnet.ap2.core.type.ApVariantHolder;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.DataContainers;
import work.lclpnet.ap2.impl.game.data.IntDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.ProjectileCanHitCallback;
import work.lclpnet.kibu.hook.entity.ProjectileHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static net.minecraft.util.Formatting.YELLOW;
import static work.lclpnet.ap2.impl.util.ItemHelper.unbreakable;

public class ChickenShooterInstance extends FFAGameInstance implements Runnable {

    private static final double BABY_CHANCE = 0.15;
    private static final double TNT_CHANCE = 0.07;
    private static final double TNT_RADIUS = 6;
    private static final int MIN_DURATION = 40;
    private static final int MAX_DURATION = 60;

    private static final Stat<Integer> SCORE = CommonStats.SCORE;
    private static final Stat<Integer> BABY_CHICKENS = new Stat<>("baby_chickens", 0);
    private static final Stat<Integer> TNT_DETONATED = new Stat<>("tnt_detonated", 0);
    private static final Stat<Integer> CHICKENS_EXPLODED = new Stat<>("chickens_exploded", 0);

    private final FFAStatsManager stats = new FFAStatsManager(Set.of(
            SCORE, BABY_CHICKENS, TNT_DETONATED, CHICKENS_EXPLODED
    ));

    private final Random random = new Random();
    private final int durationSeconds = MIN_DURATION + random.nextInt(MAX_DURATION - MIN_DURATION + 1);
    private final IntDataContainer<ServerPlayerEntity, PlayerRef> data;
    private final Set<ChickenEntity> chickenSet = new HashSet<>();
    private BlockBox chickenBox = null;
    private int despawnHeight = 0;
    private int time = 0;
    private int spawnInterval;

    public ChickenShooterInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        data = DataContainers.finaleCompatibleScoreContainer(gameHandle, PlayerRef::create);
        data.register((player, score) -> stats.set(player, SCORE, score));
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        ServerWorld world = getWorld();

        commons().gameRuleBuilder()
                .set(GameRules.DO_ENTITY_DROPS, false)
                .set(GameRules.ANNOUNCE_ADVANCEMENTS, false);

        despawnHeight = getMap().requireProperty("despawn-height");

        // hooks
        HookRegistrar hooks = gameHandle.getHooks();

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
            objective.add(player);
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

        Translations translations = gameHandle.getTranslations();

        int playerCount = gameHandle.getParticipants().count();

        if (playerCount > 7) {
            spawnInterval = 5;
        } else if (playerCount > 3) {
            spawnInterval = 7;
        } else {
            spawnInterval = 10;
        }

        giveBowsToPlayers(translations);

        chickenSpawner();

        // Timer and game end
        var subject = translations.translateText("game.ap2.chicken_shooter.task");

        commons().createTimer(subject, durationSeconds).whenDone(winManager::complete);

        StatsDisplay statsDisplay = new StatsDisplay(gameHandle.getTranslations());

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            statsDisplay.open(player);
        }
    }

    private void chickenSpawner() {
        JSONArray spawnBounds = getMap().requireProperty("spawn-bounds");
        chickenBox = MapUtil.readBox(spawnBounds);
        gameHandle.getGameScheduler().interval(this, 1);
    }

    @SuppressWarnings("unchecked")
    private void spawnChicken() {
        ServerWorld world = getWorld();
        BlockPos.Mutable randomPos = new BlockPos.Mutable();
        chickenBox.randomBlockPos(randomPos, random);

        ChickenEntity chicken = new ChickenEntity(EntityType.CHICKEN, world);

        var variants = world.getRegistryManager().getOrThrow(RegistryKeys.CHICKEN_VARIANT).getIndexedEntries();

        if (variants.size() >= 1) {
            var variant = variants.get(random.nextInt(variants.size()));
            ((ApVariantHolder<RegistryEntry<ChickenVariant>>) chicken).ap2$setVariant(variant);
        }

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
        if (chicken.isRemoved()) return 0;

        int score = 0;

        double x = chicken.getX();
        double y = chicken.getY();
        double z = chicken.getZ();

        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 8, 0.4, 0.4, 0.4, 0.2);

        TntEntity tnt = chicken.getFirstPassenger() instanceof TntEntity t ? t : null;

        chicken.discard();
        chickenSet.remove(chicken);

        if (tnt != null) {
            stats.increment(attacker, TNT_DETONATED);
            score += tntExplode(chicken, tnt, world, attacker, x, y, z);
        }

        if (chicken.isBaby()) {
            stats.increment(attacker, BABY_CHICKENS);
            score += 3;
        } else {
            score += 1;
        }

        return score;
    }

    private int tntExplode(ChickenEntity chicken, TntEntity tnt, ServerWorld world, ServerPlayerEntity attacker, double x, double y, double z) {
        world.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 4, 0.5, 0.5, 0.5, 1);
        attacker.playSoundToPlayer(SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 0.8f, 1.8f);
        attacker.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.8f, 0.7f);

        Vec3d tntPos = tnt.getPos();

        int score = 0;
        var affected = world.getEntitiesByType(TypeFilter.instanceOf(ChickenEntity.class), chickenEntity -> tntPos.isInRange(chickenEntity.getPos(), TNT_RADIUS));
        int count = 0;

        for (ChickenEntity c : affected) {
            if (c == chicken || chicken.isRemoved()) continue;

            count++;
            score += killChicken(c, attacker, world);
        }

        tnt.discard();

        stats.increment(attacker, CHICKENS_EXPLODED, count);

        return score;
    }

    private void giveBowsToPlayers(Translations translations) {
        var infinity = ItemHelper.getEnchantment(Enchantments.INFINITY, getWorld().getRegistryManager());

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            ItemStack stack = unbreakable(new ItemStack(Items.BOW));

            stack.addEnchantment(infinity, 1);
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

        if (time % spawnInterval == 0) {
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
}
