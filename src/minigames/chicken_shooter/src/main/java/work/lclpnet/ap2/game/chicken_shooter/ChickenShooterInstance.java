package work.lclpnet.ap2.game.chicken_shooter;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.stats.FFAStatsManager;
import work.lclpnet.ap2.api.stats.Stat;
import work.lclpnet.ap2.core.type.ApVariantHolder;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.DataContainers;
import work.lclpnet.ap2.impl.game.data.IntDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.ProjectileCanHitCallback;
import work.lclpnet.kibu.hook.entity.ProjectileHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static net.minecraft.ChatFormatting.YELLOW;
import static work.lclpnet.ap2.api.stats.CommonStats.SCORE;
import static work.lclpnet.ap2.impl.util.ItemHelper.unbreakable;

public class ChickenShooterInstance extends FFAGameInstance implements Runnable {

    private static final double BABY_CHANCE = 0.15;
    private static final double TNT_CHANCE = 0.07;
    private static final double TNT_RADIUS = 7.5;
    private static final int DURATION_SECONDS = 50;

    private static final Stat<Integer> BABY_CHICKENS = new Stat<>("baby_chickens", 0);
    private static final Stat<Integer> TNT_DETONATED = new Stat<>("tnt_detonated", 0);
    private static final Stat<Integer> CHICKENS_EXPLODED = new Stat<>("chickens_exploded", 0);

    private final FFAStatsManager stats;
    private final Random random = new Random();
    private final IntDataContainer<ServerPlayer, PlayerRef> data;
    private final Set<Chicken> chickenSet = new HashSet<>();
    private BlockBox chickenBox = null;
    private int despawnHeight = 0;
    private int time = 0;
    private int spawnInterval;

    public ChickenShooterInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        data = DataContainers.finaleCompatibleScoreContainer(gameHandle, PlayerRef::create);
        stats = createStats(data, SCORE, BABY_CHICKENS, TNT_DETONATED, CHICKENS_EXPLODED);
    }

    @Override
    protected DataContainer<ServerPlayer, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        ServerLevel world = getWorld();

        commons().gameRuleBuilder()
                .set(GameRules.ENTITY_DROPS, false)
                .set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);

        despawnHeight = getMap().requireProperty("despawn-height");

        // hooks
        HookRegistrar hooks = gameHandle.getHooks();

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks, (entity, source, amount) -> {
            if (!(source.getDirectEntity() instanceof Projectile projectile)
                    || !(entity instanceof Chicken chicken)) return false;

            projectile.discard();

            if (winManager.isGameOver() || !(source.getEntity() instanceof ServerPlayer attacker)) return false;

            float pitch = chicken.isBaby() ? 1.4f : 0.8f;
            ServerPlayerAccess.playSoundToPlayer(attacker, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.8f, pitch);

            int score = killChicken(chicken, attacker, world);

            commons().addScore(attacker, score, data);

            return false;
        });

        ProjectileHooks.HIT_BLOCK.registerWith(hooks, (projectile, hit) -> projectile.discard());

        // projectiles can only hit chickens (will pass through players)
        ProjectileCanHitCallback.HOOK.registerWith(hooks, (projectile, entity) -> entity instanceof Chicken);

        // Setup Scoreboard
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        var objective = scoreboardManager.translateObjective("score", "game.ap2.chicken_shooter.points")
                .formatted(YELLOW, ChatFormatting.BOLD);

        useScoreboardStatsSync(data, objective);
        objective.setSlot(DisplaySlot.LIST);
        objective.setNumberFormat(StyledFormat.PLAYER_LIST_DEFAULT);

        for (ServerPlayer player : PlayerLookup.all(gameHandle.getServer())) {
            objective.add(player);
        }

        PlayerTeam team = scoreboardManager.createTeam("team");
        team.setSeeFriendlyInvisibles(true);
        team.setCollisionRule(Team.CollisionRule.NEVER);

        for (ServerPlayer player : gameHandle.getParticipants()) {
            scoreboardManager.joinTeam(player, team);
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 1, false, false, false));
        }
    }

    @Override
    protected void go() {
        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource)
                -> damageSource.getDirectEntity() instanceof Projectile && entity instanceof Chicken));

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

        commons().createTimer(subject, DURATION_SECONDS).whenDone(winManager::complete);
    }

    private void chickenSpawner() {
        JSONArray spawnBounds = getMap().requireProperty("spawn-bounds");
        chickenBox = MapUtil.readBox(spawnBounds);
        gameHandle.getScheduler().interval(this, 1);
    }

    @SuppressWarnings("unchecked")
    private void spawnChicken() {
        ServerLevel world = getWorld();
        BlockPos.MutableBlockPos randomPos = new BlockPos.MutableBlockPos();
        chickenBox.randomBlockPos(randomPos, random);

        Chicken chicken = new Chicken(EntityType.CHICKEN, world);

        var variants = world.registryAccess().lookupOrThrow(Registries.CHICKEN_VARIANT).asHolderIdMap();

        if (variants.size() >= 1) {
            var variant = variants.byId(random.nextInt(variants.size()));
            ((ApVariantHolder<Holder<ChickenVariant>>) chicken).ap2$setVariant(variant);
        }

        if (random.nextFloat() < BABY_CHANCE) {
            chicken.setBaby(true);
        } else if (random.nextFloat() < TNT_CHANCE) {
            spawnTNT(chicken, world);
        }

        chicken.setPosRaw(randomPos.getX() + 0.5, randomPos.getY(), randomPos.getZ() + 0.5);
        world.addFreshEntity(chicken);

        chickenSet.add(chicken);
    }


    private void spawnTNT(Chicken chicken, ServerLevel world) {
        PrimedTnt tnt = new PrimedTnt(EntityType.TNT, world);
        tnt.setFuse(Integer.MAX_VALUE);
        tnt.startRiding(chicken, true, false);
        world.addFreshEntity(tnt);
    }

    private int killChicken(Chicken chicken, ServerPlayer attacker, ServerLevel world) {
        if (chicken.isRemoved()) return 0;

        int score = 0;

        double x = chicken.getX();
        double y = chicken.getY();
        double z = chicken.getZ();

        world.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 8, 0.4, 0.4, 0.4, 0.2);

        PrimedTnt tnt = chicken.getFirstPassenger() instanceof PrimedTnt t ? t : null;

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

    private int tntExplode(Chicken chicken, PrimedTnt tnt, ServerLevel world, ServerPlayer attacker, double x, double y, double z) {
        world.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 4, 0.5, 0.5, 0.5, 1);
        ServerPlayerAccess.playSoundToPlayer(attacker, SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 0.8f, 1.8f);
        ServerPlayerAccess.playSoundToPlayer(attacker, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 0.7f);

        Vec3 tntPos = tnt.position();

        int score = 0;
        var affected = world.getEntities(EntityTypeTest.forClass(Chicken.class), chickenEntity -> tntPos.closerThan(chickenEntity.position(), TNT_RADIUS));
        int count = 0;

        for (Chicken c : affected) {
            if (c == chicken || c.isRemoved()) continue;

            count++;
            score += killChicken(c, attacker, world);
        }

        tnt.discard();

        stats.increment(attacker, CHICKENS_EXPLODED, count);

        return score;
    }

    private void giveBowsToPlayers(Translations translations) {
        var infinity = ItemHelper.getEnchantment(Enchantments.INFINITY, getWorld().registryAccess());

        for (ServerPlayer player : gameHandle.getParticipants()) {
            ItemStack stack = unbreakable(new ItemStack(Items.BOW));

            stack.enchant(infinity, 1);
            stack.set(DataComponents.CUSTOM_NAME, translations.translateText(player, "game.ap2.chicken_shooter.bow")
                    .styled(style -> style.withItalic(false).applyFormat(ChatFormatting.GOLD)));

            Inventory inventory = player.getInventory();
            inventory.setItem(4, stack);

            PlayerInventoryAccess.setSelectedSlot(player, 4);

            inventory.setItem(9, new ItemStack(Items.ARROW));
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
                if (chicken.getFirstPassenger() instanceof PrimedTnt tnt) {
                    tnt.discard();
                }

                chicken.discard();
                getWorld().sendParticles(ParticleTypes.CLOUD, x, y, z, 3, 0.2, 0.2, 0.2, 0.02);
                return true;
            }

            return false;
        });
    }
}
