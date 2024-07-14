package work.lclpnet.ap2.game.bow_spleef;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerInventory;
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
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.ItemStackHelper;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.ap2.impl.util.handler.DoubleJumpHandler;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.entity.ProjectileHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.hook.world.BlockBreakParticleCallback;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.Objects;

public class BowSpleefInstance extends EliminationGameInstance {

    private static final int WORLD_BORDER_DELAY = Ticks.seconds(80);
    private static final int WORLD_BORDER_TIME = Ticks.seconds(20);
    private final DoubleJumpHandler doubleJumpHandler;

    public BowSpleefInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        doubleJumpHandler = new DoubleJumpHandler(gameHandle.getPlayerUtil(), gameHandle.getScheduler());
    }

    @Override
    protected void prepare() {
        useSmoothDeath();
        useNoHealing();
        useRemainingPlayersDisplay();

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(BlockBreakParticleCallback.HOOK, (world, pos, state) -> true);

        hooks.registerHook(ProjectileHooks.HIT_BLOCK,(projectile, hit) -> {
            removeBlocks(hit.getBlockPos(), (ServerWorld) projectile.getWorld());
            projectile.discard();
        });

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, (entity, source, amount) -> {
            if (!(source.getSource() instanceof ProjectileEntity projectile)) return true;

            removeBlocks(entity.getBlockPos().down(), (ServerWorld) entity.getWorld());
            projectile.discard();
            return false;
        });

        commons().whenBelowCriticalHeight().then(this::eliminate);
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource)
                -> damageSource.getSource() instanceof ProjectileEntity || damageSource.isOf(DamageTypes.OUTSIDE_BORDER)));

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        doubleJumpHandler.init(hooks);

        TranslationService translations = gameHandle.getTranslations();

        giveBowsToPlayers(translations);

        commons().scheduleWorldBorderShrink(WORLD_BORDER_DELAY, WORLD_BORDER_TIME, Ticks.seconds(5))
                .then(this::removeBlocksUnder);
    }

    private void giveBowsToPlayers(TranslationService translations) {
        var infinity = ItemStackHelper.getEnchantment(Enchantments.INFINITY, getWorld().getRegistryManager());

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            ItemStack stack = new ItemStack(Items.BOW);

            stack.set(DataComponentTypes.CUSTOM_NAME, translations.translateText(player, "game.ap2.bow_spleef.bow")
                    .styled(style -> style.withItalic(false).withFormatting(Formatting.GOLD)));

            stack.addEnchantment(infinity,1);

            stack.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));

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
                x + 1, y, z + 1)) {

            world.setBlockState(p, Blocks.AIR.getDefaultState());
        }
        double cx = x + 0.5;
        double cz = z + 0.5;

        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, cx, y, cz, 60, 1, 0.6, 1, 0.01);
        world.spawnParticles(ParticleTypes.FLAME, cx, y, cz, 30, 1, 0.6, 1, 0.04);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.AMBIENT, 0.12f, 0f);
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
}
