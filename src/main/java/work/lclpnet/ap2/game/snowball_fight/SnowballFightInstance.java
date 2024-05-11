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
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.FixedNumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.hook.entity.EntityHealthCallback;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.List;
import java.util.Random;

public class SnowballFightInstance extends EliminationGameInstance {

    private static final int WORLD_BORDER_DELAY = Ticks.minutes(2);
    private static final int WORLD_BORDER_TIME = Ticks.seconds(20);
    private static final float SNOWBALL_DAMAGE = 0.75f;
    private ScoreboardObjective healthObjective = null;

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

        setupScoreboard();
        teleportPlayers();
    }

    @Override
    protected void ready() {
        Participants participants = gameHandle.getParticipants();
        CustomScoreboardManager manager = gameHandle.getScoreboardManager();
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.BREAK_BLOCKS, (entity, pos) -> {
                if (entity instanceof ServerPlayerEntity player && participants.isParticipating(player) && !winManager.isGameOver()) {
                    onBreakBlock(player, pos);
                }

                return false;
            });

            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource) ->
                    damageSource.isOf(DamageTypes.OUTSIDE_BORDER) ||
                    entity instanceof ServerPlayerEntity damaged && participants.isParticipating(damaged)
                    && damageSource.getSource() instanceof ProjectileEntity && damageSource.getAttacker() != entity);
        });

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

        hooks.registerHook(EntityHealthCallback.HOOK, (entity, health) -> {
            float oldHealth = entity.getHealth();

            if (entity instanceof ServerPlayerEntity player && participants.isParticipating(player) && health < oldHealth) {
                // update the scoreboard
                manager.setScore(player, healthObjective, (int) Math.ceil(health));
                manager.setNumberFormat(player, healthObjective, new FixedNumberFormat(healthText(health)));
            }

            return false;
        });

        for (ServerPlayerEntity player : participants) {
            EntityAttributeInstance attribute = player.getAttributeInstance(EntityAttributes.PLAYER_BLOCK_BREAK_SPEED);

            if (attribute != null) {
                attribute.setBaseValue(100);
            }
        }

        commons().scheduleWorldBorderShrink(WORLD_BORDER_DELAY, WORLD_BORDER_TIME, Ticks.seconds(5));
    }

    private void setupScoreboard() {
        CustomScoreboardManager manager = gameHandle.getScoreboardManager();

        healthObjective = manager.createObjective("health_name", ScoreboardCriterion.DUMMY, Text.empty(), ScoreboardCriterion.RenderType.HEARTS);
        healthObjective.setDisplayAutoUpdate(false);
        manager.setDisplay(ScoreboardDisplaySlot.BELOW_NAME, healthObjective);
        manager.setDisplay(ScoreboardDisplaySlot.LIST, healthObjective);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            float health = player.getHealth();
            manager.setNumberFormat(player, healthObjective, new FixedNumberFormat(healthText(health)));
            manager.setScore(player, healthObjective, (int) Math.ceil(health));
        }
    }

    private Text healthText(float health) {
        int hearts = Math.max(0, Math.min(20, (int) Math.ceil(health)));
        boolean half = hearts % 2 == 1;
        hearts >>= 1;

        MutableText text = Text.literal(" " + "♥".repeat(hearts)).withColor(0xff1313);

        if (half) {
            text.append(Text.literal("♡").withColor(0xff1313));
            hearts += 1;
        }

        if (hearts < 10) {
            text.append(Text.literal("♡".repeat(10 - hearts)).withColor(0x282828));
        }

        return text;
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

        if (state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.POWDER_SNOW)) {
            player.getInventory().insertStack(new ItemStack(Items.SNOWBALL, 1));
        }
    }
}
