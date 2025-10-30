package work.lclpnet.ap2.game.snowball_fight;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.List;
import java.util.Random;
import java.util.Set;

public class SnowballFightInstance extends EliminationGameInstance {

    private static final int
            WORLD_BORDER_DELAY = Ticks.minutes(1),
            WORLD_BORDER_TIME = Ticks.minutes(1) + Ticks.seconds(20),
            COMBAT_IDLE_TICKS = Ticks.seconds(9),
            FREEZING_DURATION_TICKS = Ticks.seconds(5),
            MAX_SNOWBALL_STACKS = 9;

    private static final float SNOWBALL_DAMAGE = 0.75f;

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

        commons().displayHealth();
        teleportPlayers();
    }

    @Override
    protected void go() {
        Participants participants = gameHandle.getParticipants();
        HookRegistrar hooks = gameHandle.getHooks();
        TaskScheduler scheduler = gameHandle.getGameScheduler();

        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.BREAK_BLOCKS, (entity, pos) -> {
                if (entity instanceof ServerPlayerEntity player && participants.isParticipating(player) && !winManager.isGameOver()) {
                    onBreakBlock(player, pos);
                }

                return false;
            });

            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource)
                    -> damageSource.isOf(DamageTypes.OUTSIDE_BORDER)
                    || damageSource.isOf(DamageTypes.FREEZE)
                    || entity instanceof ServerPlayerEntity damaged
                    && participants.isParticipating(damaged)
                    && damageSource.getSource() instanceof ProjectileEntity && damageSource.getAttacker() != entity);
        });

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, (entity, source, amount) -> {
            if (source.getSource() instanceof SnowballEntity && Math.abs(amount) < 1e-4f && entity.getEntityWorld() instanceof ServerWorld world) {
                entity.damage(world, source, SNOWBALL_DAMAGE);
                return false;
            }

            return true;
        });

        hooks.registerHook(PlayerInteractionHooks.USE_ITEM, (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);

            if (stack.isOf(Items.SNOWBALL) && stack.getCount() == 1) {
                onDepleteStack(player);
            }

            return ActionResult.PASS;
        });

        for (ServerPlayerEntity player : participants) {
            EntityAttributeInstance attribute = player.getAttributeInstance(EntityAttributes.BLOCK_BREAK_SPEED);

            if (attribute != null) {
                attribute.setBaseValue(100);
            }
        }

        commons().scheduleWorldBorderShrink(WORLD_BORDER_DELAY, WORLD_BORDER_TIME, Ticks.seconds(5));

        var freezingManager = new FreezingManager(scheduler, gameHandle.getTranslations(), participants,
                COMBAT_IDLE_TICKS, FREEZING_DURATION_TICKS);

        freezingManager.enable(hooks);
    }

    @Override
    public void eliminate(ServerPlayerEntity player, @Nullable DamageSource source) {
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

        int selected = inventory.getSelectedSlot();
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

            player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), Set.of(), yaw, 0, true);
        }
    }

    private void onBreakBlock(ServerPlayerEntity player, BlockPos pos) {
        BlockState state = player.getEntityWorld().getBlockState(pos);

        if (state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.POWDER_SNOW)) {
            addSnowball(player);
        }
    }

    private void addSnowball(ServerPlayerEntity player) {
        if (player.getInventory().count(Items.SNOWBALL) < MAX_SNOWBALL_STACKS * Items.SNOWBALL.getMaxCount()) {
            player.getInventory().insertStack(new ItemStack(Items.SNOWBALL, 1));
            return;
        }

        gameHandle.getTranslations().translateText("game.ap2.snowball_fight.max_snowballs")
                .formatted(Formatting.RED)
                .sendTo(player, true);
    }
}
