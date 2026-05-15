package work.lclpnet.ap2.game.bow_spleef;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.core.hook.EntitySpawnCallback;
import work.lclpnet.ap2.core.hook.ProjectileHitEntityCallback;
import work.lclpnet.ap2.game.bow_spleef.item.*;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.game.item.SpecialItems;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.ap2.impl.util.handler.DoubleJumpHandler;
import work.lclpnet.ap2.impl.util.handler.VisualCooldown;
import work.lclpnet.combatctl.impl.CombatStyles;
import work.lclpnet.game.impl.prot.ProtectionTypes;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.ProjectileHooks;
import work.lclpnet.kibu.hook.level.BlockBreakParticleCallback;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;

import java.util.Objects;
import java.util.Random;

import static work.lclpnet.ap2.impl.util.ItemHelper.unbreakable;

public class BowSpleefInstance extends EliminationGameInstance {

    private static final int
            WORLD_BORDER_DELAY = Ticks.seconds(70),
            WORLD_BORDER_TIME = Ticks.seconds(20),
            DOUBLE_JUMP_COOLDOWN_TICKS = Ticks.seconds(2);

    private final DoubleJumpHandler doubleJumpHandler;
    private final Random random = new Random();
    private final HeavyWeightItem heavyWeightItem = new HeavyWeightItem();
    private final TripleJumpItem tripleJumpItem = new TripleJumpItem();
    private SpecialItems specialItems = null;

    public BowSpleefInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        var cooldown = new VisualCooldown(gameHandle.getRootScheduler());

        doubleJumpHandler = new DoubleJumpHandler(player -> !cooldown.isOnCooldown(player) && !heavyWeightItem.isHeavyWeighted(player));
        heavyWeightItem.setDoubleJumpHandler(doubleJumpHandler);

        doubleJumpHandler.onDoubleJump().then(player -> {
            if (specialItems != null
                    && specialItems.hasSpecialItem(player, tripleJumpItem)
                    && tripleJumpItem.handleExtraJump(player, specialItems)) return;

            doubleJumpHandler.disable(player);
            cooldown.setCooldown(player, DOUBLE_JUMP_COOLDOWN_TICKS);
        });

        cooldown.setOnCooldownOver(player -> {
            if (heavyWeightItem.isHeavyWeighted(player)) return;

            doubleJumpHandler.enable(player);
        });

        gameHandle.getPlayerUtil().setDefaultCombatStyle(CombatStyles.CLASSIC.andThen(playerConfig
                -> playerConfig.setFishingRodPull(true), _ -> {}));
    }

    @Override
    protected void prepare() {
        useSmoothDeath();
        useNoHealing();
        useRemainingPlayersDisplay();

        HookRegistrar hooks = gameHandle.getHooks();

        BlockBreakParticleCallback.HOOK.registerWith(hooks, (_, _, _) -> true);

        Hook<Impact> impactHook = HookFactory.createArrayBacked(Impact.class, callbacks -> (projectile, pos) -> {
            for (Impact callback : callbacks) {
                callback.onImpact(projectile, pos);
            }
        });

        ProjectileHooks.HIT_BLOCK.registerWith(hooks, (projectile, hit) -> {
            if (projectile instanceof Arrow) {
                impactHook.invoker().onImpact(projectile, hit.getBlockPos());
            }
        });

        ProjectileHitEntityCallback.HOOK.registerWith(hooks, (projectile, hit) -> {
            if (projectile instanceof Arrow) {
                impactHook.invoker().onImpact(projectile, hit.getEntity().blockPosition().below());
            }
        });

        // don't spawn chickens from thrown eggs
        EntitySpawnCallback.HOOK.registerWith(hooks, (entity, _) -> entity instanceof Chicken);

        commons().whenBelowCriticalHeight().then(this::eliminate);

        specialItems = SpecialItems.create(gameHandle, getMap(), getWorld(), random, commons().debugController(), r -> r
                .register(new TripleShotItem(), 0.55f)
                .register(new BurstShotItem(), 0.4f)
                .register(new ExplodeAmmoItem(impactHook), 0.3f)
                .register(heavyWeightItem, 0.3f)
                .register(new FishingRodItem(), 0.1f)
                .register(new SwitcherItem(), 0.3f)
                .register(new LevitationItem(), 0.2f)
                .register(new LightWeightItem(), 0.25f)
                .register(tripleJumpItem, 0.15f)
                .register(new CreeperExplosionItem(), 0.15f));

        specialItems.setup();
        specialItems.syncWithWorldBorder();

        // register this callback after special item setup, to execute it last (updates spawn pos mesh)
        impactHook.register((projectile, pos) -> {
            removeBlocks(pos, getWorld());
            projectile.discard();
        });
    }

    @Override
    protected void go() {
        gameHandle.protect(config -> {
            ProtectionTypes.ALLOW_DAMAGE.allow(config, (_, damageSource)
                    -> damageSource.is(DamageTypes.OUTSIDE_BORDER)
                    || (damageSource.is(DamageTypes.THROWN) && damageSource.getDirectEntity() instanceof FishingHook));

            ProtectionTypes.EXPLOSION.allow(config);
        });

        HookRegistrar hooks = gameHandle.getHooks();

        doubleJumpHandler.init(hooks);
        doubleJumpHandler.enable(gameHandle.getParticipants());

        Translations translations = gameHandle.getTranslations();

        giveBowsToPlayers(translations);

        commons().scheduleWorldBorderShrink(WORLD_BORDER_DELAY, WORLD_BORDER_TIME, Ticks.seconds(5))
                .then(this::removeBlocksUnder);

        specialItems.spawnPeriodically();
    }

    private void giveBowsToPlayers(Translations translations) {
        var infinity = ItemHelper.getEnchantment(Enchantments.INFINITY, getWorld().registryAccess());

        for (ServerPlayer player : gameHandle.getParticipants()) {
            ItemStack stack = unbreakable(new ItemStack(Items.BOW));

            stack.set(DataComponents.CUSTOM_NAME, translations.translateText(player, "game.ap2.bow_spleef.bow")
                    .styled(style -> style.withItalic(false).applyFormat(ChatFormatting.GOLD)));

            stack.enchant(infinity,1);
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);

            Inventory inventory = player.getInventory();
            inventory.setItem(4, stack);

            PlayerInventoryAccess.setSelectedSlot(player, 4);

            inventory.setItem(9,new ItemStack(Items.ARROW));
        }
    }

    private void removeBlocks(BlockPos pos, ServerLevel world) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        for (BlockPos p : BlockPos.betweenClosed(
                x - 1, y - 1, z - 1,
                x + 1, y + 1, z + 1)) {

            world.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
        double cx = x + 0.5;
        double cz = z + 0.5;

        world.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, y, cz, 60, 1, 0.6, 1, 0.01);
        world.sendParticles(ParticleTypes.FLAME, cx, y, cz, 30, 1, 0.6, 1, 0.04);
        world.playSound(null, x, y, z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.AMBIENT, 0.12f, 0f);

        specialItems.positions().update();
    }

    private void removeBlocksUnder() {
        ServerLevel world = getWorld();

        JSONArray spawnJson = Objects.requireNonNull(getMap().getProperty("spawn"), "Spawn not configured");
        BlockPos spawn = MapUtil.readBlockPos(spawnJson);

        int x = spawn.getX();
        int y = spawn.getY();
        int z = spawn.getZ();

        BlockState air = Blocks.AIR.defaultBlockState();

        SoundHelper.playSound(gameHandle.getServer(), SoundEvents.WITHER_DEATH, SoundSource.AMBIENT, 0.8f, 1f);

        for (BlockPos pos : BlockPos.betweenClosed(x - 3, y - 30, z - 3, x + 3, y + 10, z + 3)) {
            world.setBlockAndUpdate(pos, air);
        }
    }

    public interface Impact {
        void onImpact(Projectile projectile, BlockPos pos);
    }
}
