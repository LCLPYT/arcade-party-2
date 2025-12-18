package work.lclpnet.ap2.game.paintball.util;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.util.RayCastUtil;
import work.lclpnet.ap2.impl.util.VanishManager;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.gaco.ds.BlockBox;
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

    private final ServerLevel world;
    private final Participants participants;
    private final PaintballTeams teams;
    private final PaintManager paintManager;
    private final PaintGunManager paintGunManager;
    private final VanishManager vanishManager;
    private final DebugController debugController;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public PaintballTicker(ServerLevel world, Participants participants, PaintballTeams teams, PaintManager paintManager,
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
            if (entity instanceof ServerPlayer player && participants.isParticipating(player)) {
                entry(player).outOfCombatTicks = 0;
            }

            return true;
        });
    }

    private @NotNull Entry entry(ServerPlayer player) {
        return entries.computeIfAbsent(player.getUUID(), u -> new Entry());
    }

    private void tick() {
        for (ServerPlayer player : participants) {
            tickPlayer(player);
        }
    }

    private void tickPlayer(ServerPlayer player) {
        Entry entry = entry(player);
        entry.outOfCombatTicks++;

        BlockState inkContactState = null;

        if (player.isShiftKeyDown()) {
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
            player.removeEffect(MobEffects.SLOWNESS);
        }

        if (onInk == OnInk.OWN && player.isShiftKeyDown()) {
            vanishManager.vanish(player);

            setAttribute(player, Attributes.MOVEMENT_SPEED, 0.14);
            setAttribute(player, Attributes.SNEAKING_SPEED, 1.0);

            if (entry.outOfCombatTicks >= HEAL_DELAY_TICKS) {
                player.setHealth(player.getHealth() + HEAL_PER_SECOND / 20);
            }

            if (entry.nextSound-- <= 0) {
                entry.nextSound = SOUND_TICKS;
                playSoundAt(player, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.40f, 1.65f + (float) random() * 0.2f);
            }

            BlockState state = inkContactState;

            teams.teamOf(player).ifPresent(team -> {
                if (state != null) {
                    world.sendParticles(
                            new BlockParticleOption(ParticleTypes.BLOCK, state),
                            player.getX(), player.getY(), player.getZ(), 2, 0.2, 0, 0.2, 0.2
                    );
                } else {
                    world.sendParticles(
                            new DustParticleOptions(team.key().color(), 0.8f),
                            player.getX(), player.getY(), player.getZ(), 2, 0.2, 0, 0.2, 0.2
                    );
                }
            });

            paintGunManager.setReloading(player);
            tickReload(player, entry);

            return;
        }

        vanishManager.show(player);
        resetAttribute(player, Attributes.MOVEMENT_SPEED);
        resetAttribute(player, Attributes.SNEAKING_SPEED);

        paintGunManager.removeReloading(player);

        if (onInk == OnInk.ENEMY) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 1, false, false, false));
        }
    }

    private @Nullable BlockState tickWallClimbing(ServerPlayer player) {
        PaintballTeam playerTeam = teams.teamOf(player).orElse(null);

        if (playerTeam == null) return null;

        Vec3 input = PlayerUtil.getHorizontalInputVector(player);

        if (DEBUG_WALL_CLIMBING) {
            debugController.exclusive("input_" + player.getScoreboardName(), controller
                    -> controller.renderer().ifPresent(renderer
                    -> renderer.arrow(player.position(), input, 0.5f, Blocks.REDSTONE_BLOCK.defaultBlockState())));
        }

        EntityDimensions dimensions = player.getDimensions(player.getPose());
        float width = dimensions.width();

        BlockHitResult hit = RayCastUtil.raycastBlocks(world, player.position(), input, width, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.of(player));

        if (hit.getType() != HitResult.Type.BLOCK) return null;

        BlockState collisionState = world.getBlockState(hit.getBlockPos());
        DyeTeamKey collisionTeam = paintManager.getTeam(collisionState.getBlock());

        if (collisionTeam != playerTeam.key()) return null;

        setVelocity(player, player.getDeltaMovement().with(Direction.Axis.Y, 0.25));

        return collisionState;
    }

    private void tickReload(ServerPlayer player, Entry entry) {
        Pair<PaintGun, ItemStack> pair = paintGunManager.getPaintGunAndStack(player).orElse(null);

        if (pair == null) return;

        PaintGun paintGun = pair.left();
        ItemStack stack = pair.right();

        if (stack.getDamageValue() <= 0) return;  // nothing to reload

        if (entry.reloadTicks < paintGun.reloadTicks()) {
            entry.reloadTicks++;
            return;
        }

        entry.reloadTicks = 0;
        stack.set(DataComponents.DAMAGE, max(0, stack.getDamageValue() - paintGun.reloadAmount()));

        player.playNotifySound(SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.2f, 1f);
    }

    private @NotNull Pair<OnInk, BlockState> standingOnInk(ServerPlayer player) {
        if (player.isSpectator()) {
            return Pair.of(OnInk.NONE, null);
        }

        PaintballTeam team = teams.teamOf(player).orElse(null);

        if (team == null) {
            return Pair.of(OnInk.NONE, null);
        }

        double width = player.getDimensions(player.getPose()).width();
        BlockState ownInkContactState = null;

        for (BlockPos pos : BlockBox.of(AABB.ofSize(player.position(), width, 0.1, width))) {
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
