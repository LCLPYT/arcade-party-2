package work.lclpnet.ap2.game.snowball_fight;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.world.SpawnFinder;
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
        TaskScheduler scheduler = gameHandle.getScheduler();

        gameHandle.protect(config -> {
            ProtectionTypes.BREAK_BLOCKS.allow(config, (entity, pos) -> {
                if (entity instanceof ServerPlayer player && participants.isParticipating(player) && !winManager.isGameOver()) {
                    onBreakBlock(player, pos);
                }

                return false;
            });

            ProtectionTypes.ALLOW_DAMAGE.allow(config, (entity, damageSource)
                    -> damageSource.is(DamageTypes.OUTSIDE_BORDER)
                    || damageSource.is(DamageTypes.FREEZE)
                    || entity instanceof ServerPlayer damaged
                    && participants.isParticipating(damaged)
                    && damageSource.getDirectEntity() instanceof Projectile && damageSource.getEntity() != entity);
        });

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks, (entity, source, amount) -> {
            if (source.getDirectEntity() instanceof Snowball && Math.abs(amount) < 1e-4f && entity.level() instanceof ServerLevel world) {
                entity.hurtServer(world, source, SNOWBALL_DAMAGE);
                return false;
            }

            return true;
        });

        PlayerInteractionHooks.USE_ITEM.registerWith(hooks, (player, _, hand) -> {
            ItemStack stack = player.getItemInHand(hand);

            if (stack.is(Items.SNOWBALL) && stack.getCount() == 1) {
                onDepleteStack(player);
            }

            return InteractionResult.PASS;
        });

        for (ServerPlayer player : participants) {
            AttributeInstance attribute = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);

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
    public void eliminate(ServerPlayer player, @Nullable DamageSource source) {
        if (source != null) {
            ServerLevel world = getWorld();

            world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 0.5f, 1f);

            double x = player.getX();
            double y = player.getY() + 1;
            double z = player.getZ();

            var effect = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState());
            world.sendParticles(effect, x, y, z, 50, 0.2, 1, 0.2, 1);

            world.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 50, 0.2, 1, 0.2, 0.05);
        }

        super.eliminate(player, source);
    }

    private static void onDepleteStack(Player player) {
        Inventory inventory = player.getInventory();

        int selected = inventory.getSelectedSlot();
        int size = inventory.getContainerSize();

        for (int i = 0; i < size; i++) {
            if (i == selected) continue;

            ItemStack stack = inventory.getItem(i);
            if (!stack.is(Items.SNOWBALL)) continue;

            inventory.setItem(i, ItemStack.EMPTY);
            stack.grow(1);
            inventory.setItem(selected, stack);
            break;
        }
    }

    private void teleportPlayers() {
        ServerLevel world = getWorld();
        GameMap map = getMap();
        Participants participants = gameHandle.getParticipants();
        Random random = new Random();

        Number spacingValue = map.getProperty("spawn-spacing");
        double spacing = spacingValue != null ? spacingValue.doubleValue() : 16;

        SpawnFinder spawns = new SpawnFinder(spacing, commons().debugController());
        List<Vec3> available = spawns.findSpawns(world, map);
        List<Vec3> spacedSpawns = spawns.generateSpacedSpawns(available, participants.count(), random);

        int i = 0;

        for (ServerPlayer player : participants) {
            Vec3 spawn = spacedSpawns.get(i++);

            float yaw = random.nextFloat(360) - 180;

            player.teleportTo(world, spawn.x(), spawn.y(), spawn.z(), Set.of(), yaw, 0, true);
        }
    }

    private void onBreakBlock(ServerPlayer player, BlockPos pos) {
        BlockState state = player.level().getBlockState(pos);

        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
            addSnowball(player);
        }
    }

    private void addSnowball(ServerPlayer player) {
        if (player.getInventory().countItem(Items.SNOWBALL) < MAX_SNOWBALL_STACKS * Items.SNOWBALL.getDefaultMaxStackSize()) {
            player.getInventory().add(new ItemStack(Items.SNOWBALL, 1));
            return;
        }

        gameHandle.getTranslations().translateText("game.ap2.snowball_fight.max_snowballs")
                .formatted(ChatFormatting.RED)
                .sendTo(player, true);
    }
}
