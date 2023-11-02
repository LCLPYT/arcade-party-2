package work.lclpnet.ap2.game.bow_spleef;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
import net.minecraft.world.border.WorldBorder;
import work.lclpnet.ap2.api.base.WorldBorderManager;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.DoubleJumpHandler;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.entity.ProjectileHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.hook.world.BlockBreakParticleCallback;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

public class BowSpleefInstance extends EliminationGameInstance {

    private static final int WORLD_BORDER_DELAY = Ticks.seconds(80);
    private static final int WORLD_BORDER_TIME = Ticks.seconds(30);
    private final DoubleJumpHandler doubleJumpHandler;

    public BowSpleefInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        doubleJumpHandler = new DoubleJumpHandler(gameHandle.getPlayerUtil());
    }

    @Override
    protected void prepare() {
        useSmoothDeath();
        useNoHealing();

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
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource)
                    -> damageSource.getSource() instanceof ProjectileEntity || damageSource.isOf(DamageTypes.OUTSIDE_BORDER));
        });

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        doubleJumpHandler.init(hooks);

        TranslationService translations = gameHandle.getTranslations();

        giveBowsToPlayers(translations);
        scheduleSuddenDeath();
    }

    private void giveBowsToPlayers(TranslationService translations) {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            ItemStack stack = new ItemStack(Items.BOW);

            stack.setCustomName(translations.translateText(player, "game.ap2.bow_spleef.bow")
                    .styled(style -> style.withItalic(false).withFormatting(Formatting.GOLD)));
            stack.addEnchantment(Enchantments.INFINITY,1);

            ItemStackUtil.setUnbreakable(stack, true);

            PlayerInventory inventory = player.getInventory();
            inventory.setStack(4, stack);
            PlayerInventoryAccess.setSelectedSlot(player, 4);

            inventory.setStack(9,new ItemStack(Items.ARROW));
        }
    }

    private void removeBlocks(BlockPos pos, ServerWorld world) {
        for (BlockPos p : BlockPos.iterate(
                pos.getX()-1,pos.getY()-1,pos.getZ()-1,
                pos.getX()+1,pos.getY(),pos.getZ()+1)) {

            world.setBlockState(p, Blocks.AIR.getDefaultState());
        }
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,60,1,0.6,1,0.01);
        world.spawnParticles(ParticleTypes.FLAME, pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,30,1,0.6,1,0.04);
        world.playSound(null,pos.getX(),pos.getY(),pos.getZ(), SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.AMBIENT,0.12f,0f);
    }

    private void scheduleSuddenDeath() {
        TaskScheduler scheduler = gameHandle.getScheduler();

        scheduler.timeout(() -> {
            WorldBorderManager manager = gameHandle.getWorldBorderManager();
            manager.setupWorldBorder(getWorld());

            WorldBorder worldBorder = manager.getWorldBorder();
            worldBorder.setCenter(0.5, 0.5);
            worldBorder.setSize(50);
            worldBorder.setSafeZone(0);
            worldBorder.setDamagePerBlock(0.8);
            worldBorder.interpolateSize(worldBorder.getSize(), 3, WORLD_BORDER_TIME * 50L);

            for (ServerPlayerEntity player : PlayerLookup.world(getWorld())) {
                player.playSound(SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.HOSTILE, 1, 0);
            }
        }, WORLD_BORDER_DELAY);

        scheduler.timeout(() -> {
            for (ServerPlayerEntity player : gameHandle.getParticipants()) {
                removeBlocksUnder(player);
            }
        }, WORLD_BORDER_DELAY + WORLD_BORDER_TIME + Ticks.seconds(10));
    }

    private void removeBlocksUnder(ServerPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        int x = playerPos.getX();
        int y = playerPos.getY();
        int z = playerPos.getZ();

        ServerWorld world = getWorld();
        BlockState air = Blocks.AIR.getDefaultState();

        for (BlockPos pos : BlockPos.iterate(x - 1, y - 20, z - 1, x + 1, y, z + 1)) world.setBlockState(pos, air);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.AMBIENT, 0.8f, 1f);
    }
}
