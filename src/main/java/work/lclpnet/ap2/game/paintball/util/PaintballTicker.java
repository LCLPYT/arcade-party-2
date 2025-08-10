package work.lclpnet.ap2.game.paintball.util;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.RayCastUtil;
import work.lclpnet.ap2.impl.util.VanishManager;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.lang.Math.max;
import static java.lang.Math.random;
import static work.lclpnet.ap2.impl.util.SoundHelper.playSoundAt;
import static work.lclpnet.kibu.access.VelocityModifier.setVelocity;
import static work.lclpnet.lobby.util.PlayerReset.resetAttribute;
import static work.lclpnet.lobby.util.PlayerReset.setAttribute;

public class PaintballTicker {

    private static final boolean DEBUG_WALL_CLIMBING = false;

    private static final float HEAL_PER_SECOND = 4.0f;
    private static final int
            HEAL_DELAY_TICKS = Ticks.seconds(3),
            SOUND_TICKS = 2;

    private final ServerWorld world;
    private final Participants participants;
    private final PaintballTeams teams;
    private final PaintManager paintManager;
    private final PaintGunManager paintGunManager;
    private final VanishManager vanishManager;
    private final DebugController debugController;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public PaintballTicker(ServerWorld world, Participants participants, PaintballTeams teams, PaintManager paintManager,
                           PaintGunManager paintGunManager, VanishManager vanishManager,
                           DebugController debugController) {
        this.world = world;
        this.participants = participants;
        this.teams = teams;
        this.paintManager = paintManager;
        this.paintGunManager = paintGunManager;
        this.vanishManager = vanishManager;
        this.debugController = debugController;
    }

    public void start(TaskScheduler scheduler, HookRegistrar hooks) {
        scheduler.interval(this::tick, 1);

        // needs to be registered after the PaintballInstance ALLOW_DAMAGE hook
        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, (entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player && participants.isParticipating(player)) {
                entry(player).outOfCombatTicks = 0;
            }

            return true;
        });
    }

    private @NotNull Entry entry(ServerPlayerEntity player) {
        return entries.computeIfAbsent(player.getUuid(), u -> new Entry());
    }

    private void tick() {
        for (ServerPlayerEntity player : participants) {
            tickPlayer(player);
        }
    }

    private void tickPlayer(ServerPlayerEntity player) {
        Entry entry = entry(player);
        entry.outOfCombatTicks++;

        BlockState inkContactState = null;

        if (player.isSneaking()) {
            inkContactState = tickWallClimbing(player);
        }

        OnInk onInk;

        if (inkContactState != null) {
            onInk = OnInk.OWN;
        } else {
            var res = standingOnInk(player);

            onInk = res.left();
            inkContactState = res.right();
        }

        if (onInk != OnInk.ENEMY) {
            player.removeStatusEffect(StatusEffects.SLOWNESS);
        }

        if (onInk == OnInk.OWN && player.isSneaking()) {
            vanishManager.vanish(player);

            setAttribute(player, EntityAttributes.MOVEMENT_SPEED, 0.14);
            setAttribute(player, EntityAttributes.SNEAKING_SPEED, 1.0);

            if (entry.outOfCombatTicks >= HEAL_DELAY_TICKS) {
                player.setHealth(player.getHealth() + HEAL_PER_SECOND / 20);
            }

            if (entry.nextSound-- <= 0) {
                entry.nextSound = SOUND_TICKS;
                playSoundAt(player, SoundEvents.BLOCK_HONEY_BLOCK_SLIDE, SoundCategory.PLAYERS, 0.40f, 1.65f + (float) random() * 0.2f);
            }

            BlockState state = inkContactState;

            teams.teamOf(player).ifPresent(team -> {
                if (state != null) {
                    world.spawnParticles(
                            new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                            player.getX(), player.getY(), player.getZ(), 2, 0.2, 0, 0.2, 0.2
                    );
                } else {
                    world.spawnParticles(
                            new DustParticleEffect(team.key().color(), 0.8f),
                            player.getX(), player.getY(), player.getZ(), 2, 0.2, 0, 0.2, 0.2
                    );
                }
            });

            paintGunManager.setReloading(player);
            tickReload(player, entry);

            return;
        }

        vanishManager.show(player);
        resetAttribute(player, EntityAttributes.MOVEMENT_SPEED);
        resetAttribute(player, EntityAttributes.SNEAKING_SPEED);

        paintGunManager.removeReloading(player);

        if (onInk == OnInk.ENEMY) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 1, false, false, false));
        }
    }

    private @Nullable BlockState tickWallClimbing(ServerPlayerEntity player) {
        PaintballTeam playerTeam = teams.teamOf(player).orElse(null);

        if (playerTeam == null) return null;

        Vec3d input = PlayerUtil.getHorizontalInputVector(player);

        if (DEBUG_WALL_CLIMBING) {
            debugController.exclusive("input_" + player.getNameForScoreboard(), controller
                    -> controller.renderer().ifPresent(renderer
                    -> renderer.arrow(player.getPos(), input, 0.5f, Blocks.REDSTONE_BLOCK.getDefaultState())));
        }

        EntityDimensions dimensions = player.getDimensions(player.getPose());
        float width = dimensions.width();

        BlockHitResult hit = RayCastUtil.raycastBlocks(world, player.getPos(), input, width, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.of(player));

        if (hit.getType() != HitResult.Type.BLOCK) return null;

        BlockState collisionState = world.getBlockState(hit.getBlockPos());
        DyeTeamKey collisionTeam = paintManager.getTeam(collisionState.getBlock());

        if (collisionTeam != playerTeam.key()) return null;

        setVelocity(player, player.getVelocity().withAxis(Direction.Axis.Y, 0.25));

        return collisionState;
    }

    private void tickReload(ServerPlayerEntity player, Entry entry) {
        Pair<PaintGun, ItemStack> pair = paintGunManager.getPaintGunAndStack(player).orElse(null);

        if (pair == null) return;

        PaintGun paintGun = pair.left();
        ItemStack stack = pair.right();

        if (stack.getDamage() <= 0) return;  // nothing to reload

        if (entry.reloadTicks < paintGun.reloadTicks()) {
            entry.reloadTicks++;
            return;
        }

        entry.reloadTicks = 0;
        stack.set(DataComponentTypes.DAMAGE, max(0, stack.getDamage() - paintGun.reloadAmount()));

        player.playSoundToPlayer(SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.PLAYERS, 0.2f, 1f);
    }

    private @NotNull Pair<OnInk, BlockState> standingOnInk(ServerPlayerEntity player) {
        if (player.isSpectator()) {
            return Pair.of(OnInk.NONE, null);
        }

        PaintballTeam team = teams.teamOf(player).orElse(null);

        if (team == null) {
            return Pair.of(OnInk.NONE, null);
        }

        double width = player.getDimensions(player.getPose()).width();
        BlockState ownInkContactState = null;

        for (BlockPos pos : BlockBox.of(Box.of(player.getPos(), width, 0.1, width))) {
            BlockState state = world.getBlockState(pos);
            DyeTeamKey paintTeam = paintManager.getTeam(state.getBlock());

            if (paintTeam == null) continue;

            if (paintTeam != team.key()) {
                return Pair.of(OnInk.ENEMY, state);
            }

            ownInkContactState = state;
        }

        return ownInkContactState != null
                ? Pair.of(OnInk.OWN, ownInkContactState)
                : Pair.of(OnInk.NONE, null);
    }

    private enum OnInk { NONE, ENEMY, OWN }

    private static class Entry {
        int reloadTicks = 0;
        int outOfCombatTicks = 0;
        int nextSound = 0;
    }
}
