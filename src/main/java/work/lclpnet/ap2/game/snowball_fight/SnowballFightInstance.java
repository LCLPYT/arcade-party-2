package work.lclpnet.ap2.game.snowball_fight;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.List;
import java.util.Random;

public class SnowballFightInstance extends EliminationGameInstance {

    private static final float SNOWBALL_DAMAGE = 0.75f;
    private static final int SNOWBALL_AMOUNT = 3, SNOWBALL_BIG_AMOUNT = 5;

    public SnowballFightInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
        useSurvivalMode();
        useOldCombat();
    }

    @Override
    protected void prepare() {
        useRemainingPlayersDisplay();
        useNoHealing();
        useSmoothDeath();

        teleportPlayers();
    }

    @Override
    protected void ready() {
        Participants participants = gameHandle.getParticipants();

        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.BREAK_BLOCKS, (entity, pos) -> {
                if (entity instanceof ServerPlayerEntity player && participants.isParticipating(player) && !winManager.isGameOver()) {
                    onBreakBlock(player, pos);
                }

                return false;
            });

            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource) ->
                    entity instanceof ServerPlayerEntity damaged && participants.isParticipating(damaged)
                    && damageSource.getSource() instanceof ProjectileEntity && damageSource.getAttacker() != entity);
        });

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, (entity, source, amount) -> {
            if (source.getSource() instanceof SnowballEntity && Math.abs(amount) < 1e-4f) {
                entity.damage(source, SNOWBALL_DAMAGE);
                return false;
            }

            return true;
        });

        hooks.registerHook(PlayerInteractionHooks.USE_ITEM, (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);

            if (stack.isOf(Items.SNOWBALL) && stack.getCount() == 1) {
                onDepleteStack(player);
            }

            return TypedActionResult.pass(ItemStack.EMPTY);
        });

        for (ServerPlayerEntity player : participants) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, Integer.MAX_VALUE, 20, false, false, false));
        }
    }

    @Override
    protected void eliminate(ServerPlayerEntity player, @Nullable DamageSource source) {
        if (source != null) {
            ServerWorld world = getWorld();

            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_DEATH, SoundCategory.PLAYERS, 0.5f, 1f);

            double x = player.getX();
            double y = player.getY() + 1;
            double z = player.getZ();

            var effect = new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.LIGHT_BLUE_CONCRETE.getDefaultState());
            world.spawnParticles(effect, x, y, z, 50, 0.2, 1, 0.2, 1);

            world.spawnParticles(ParticleTypes.SNOWFLAKE, x, y, z, 50, 0.2, 1, 0.2, 0.05);
        }

        super.eliminate(player, source);
    }

    private static void onDepleteStack(PlayerEntity player) {
        PlayerInventory inventory = player.getInventory();

        int selected = inventory.selectedSlot;
        int size = inventory.size();

        for (int i = 0; i < size; i++) {
            if (i == selected) continue;

            ItemStack stack = inventory.getStack(i);
            if (!stack.isOf(Items.SNOWBALL)) continue;

            inventory.setStack(i, ItemStack.EMPTY);
            stack.increment(1);
            inventory.setStack(selected, stack);
            break;
        }
    }

    private void teleportPlayers() {
        ServerWorld world = getWorld();
        GameMap map = getMap();
        Participants participants = gameHandle.getParticipants();
        Random random = new Random();

        Number spacingValue = map.getProperty("spawn-spacing");
        double spacing = spacingValue != null ? spacingValue.doubleValue() : 16;

        SnowballFightSpawns spawns = new SnowballFightSpawns(spacing);
        List<Vec3d> available = spawns.findSpawns(world, map);
        List<Vec3d> spacedSpawns = spawns.generateSpacedSpawns(available, participants.count(), random);

        int i = 0;

        for (ServerPlayerEntity player : participants) {
            Vec3d spawn = spacedSpawns.get(i++);

            float yaw = random.nextFloat(360) - 180;

            player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), yaw, 0);
        }
    }

    private void onBreakBlock(ServerPlayerEntity player, BlockPos pos) {
        BlockState state = player.getServerWorld().getBlockState(pos);

        if (state.isOf(Blocks.SNOW)) {
            player.getInventory().insertStack(new ItemStack(Items.SNOWBALL, SNOWBALL_AMOUNT));
        } else if (state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.POWDER_SNOW)) {
            player.getInventory().insertStack(new ItemStack(Items.SNOWBALL, SNOWBALL_BIG_AMOUNT));
        }
    }
}
