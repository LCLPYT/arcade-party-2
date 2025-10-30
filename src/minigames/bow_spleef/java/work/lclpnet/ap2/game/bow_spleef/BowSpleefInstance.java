package work.lclpnet.ap2.game.bow_spleef;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
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
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.ProjectileHooks;
import work.lclpnet.kibu.hook.world.BlockBreakParticleCallback;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

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
                -> playerConfig.setFishingRodPull(true), globalConfig -> {}));
    }

    @Override
    protected void prepare() {
        useSmoothDeath();
        useNoHealing();
        useRemainingPlayersDisplay();

        HookRegistrar hooks = gameHandle.getHooks();

        hooks.registerHook(BlockBreakParticleCallback.HOOK, (world, pos, state) -> true);

        Hook<Impact> impactHook = HookFactory.createArrayBacked(Impact.class, callbacks -> (projectile, pos) -> {
            for (Impact callback : callbacks) {
                callback.onImpact(projectile, pos);
            }
        });

        hooks.registerHook(ProjectileHooks.HIT_BLOCK, (projectile, hit) -> {
            if (projectile instanceof ArrowEntity) {
                impactHook.invoker().onImpact(projectile, hit.getBlockPos());
            }
        });

        hooks.registerHook(ProjectileHitEntityCallback.HOOK, (projectile, hit) -> {
            if (projectile instanceof ArrowEntity) {
                impactHook.invoker().onImpact(projectile, hit.getEntity().getBlockPos().down());
            }
        });

        // don't spawn chickens from thrown eggs
        hooks.registerHook(EntitySpawnCallback.HOOK, (entity, world) -> entity instanceof ChickenEntity);

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
            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource)
                    -> damageSource.isOf(DamageTypes.OUTSIDE_BORDER)
                    || (damageSource.isOf(DamageTypes.THROWN) && damageSource.getSource() instanceof FishingBobberEntity));

            config.allow(ProtectionTypes.EXPLOSION);
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
        var infinity = ItemHelper.getEnchantment(Enchantments.INFINITY, getWorld().getRegistryManager());

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            ItemStack stack = unbreakable(new ItemStack(Items.BOW));

            stack.set(DataComponentTypes.CUSTOM_NAME, translations.translateText(player, "game.ap2.bow_spleef.bow")
                    .styled(style -> style.withItalic(false).withFormatting(Formatting.GOLD)));

            stack.addEnchantment(infinity,1);
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);

            PlayerInventory inventory = player.getInventory();
            inventory.setStack(4, stack);

            PlayerInventoryAccess.setSelectedSlot(player, 4);

            inventory.setStack(9,new ItemStack(Items.ARROW));
        }
    }

    private void removeBlocks(BlockPos pos, ServerWorld world) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        for (BlockPos p : BlockPos.iterate(
                x - 1, y - 1, z - 1,
                x + 1, y + 1, z + 1)) {

            world.setBlockState(p, Blocks.AIR.getDefaultState());
        }
        double cx = x + 0.5;
        double cz = z + 0.5;

        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, cx, y, cz, 60, 1, 0.6, 1, 0.01);
        world.spawnParticles(ParticleTypes.FLAME, cx, y, cz, 30, 1, 0.6, 1, 0.04);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.AMBIENT, 0.12f, 0f);

        specialItems.positions().update();
    }

    private void removeBlocksUnder() {
        ServerWorld world = getWorld();

        JSONArray spawnJson = Objects.requireNonNull(getMap().getProperty("spawn"), "Spawn not configured");
        BlockPos spawn = MapUtil.readBlockPos(spawnJson);

        int x = spawn.getX();
        int y = spawn.getY();
        int z = spawn.getZ();

        BlockState air = Blocks.AIR.getDefaultState();

        SoundHelper.playSound(gameHandle.getServer(), SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.AMBIENT, 0.8f, 1f);

        for (BlockPos pos : BlockPos.iterate(x - 3, y - 30, z - 3, x + 3, y + 10, z + 3)) {
            world.setBlockState(pos, air);
        }
    }

    public interface Impact {
        void onImpact(ProjectileEntity projectile, BlockPos pos);
    }
}
