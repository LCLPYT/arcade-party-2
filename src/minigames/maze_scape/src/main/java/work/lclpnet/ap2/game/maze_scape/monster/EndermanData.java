package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.core.mixin.entity.EnderManAccessor;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.AccelerationBehaviour;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.UnstuckBehaviour;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.ValidPositionBehaviour;
import work.lclpnet.ap2.game.maze_scape.util.EndermanEscape;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.impl.util.VisibilityChecker;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.List;
import java.util.UUID;

import static java.lang.Math.max;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;
import static net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
import static work.lclpnet.kibu.access.entity.EntityUtil.addAttributeModifier;
import static work.lclpnet.kibu.access.entity.EntityUtil.removeAttributeModifier;

public class EndermanData implements MonsterData<EnderMan> {

    private static final int
            VISIBLE_CHECK_INTERVAL_TICKS = 5,
            SCARED_TICKS = 16,
            FLEE_TIMEOUT_TICKS = Ticks.seconds(5),
            SCARE_FROZEN_TICKS = 10;
    private static final double
            FLEE_SPEED_BONUS = 0.05,
            ANGER_SPEED_BONUS = 0.09,
            SCARE_ANGER_AMOUNT = 10.0,
            LOOK_AT_ANGER_AMOUNT = 25.0,
            ANGER_TRIGGER_THRESHOLD = 350.0,
            ANGER_DECAY_PER_SECOND = 6.5,
            ANGER_TRIGGER_BONUS = ANGER_DECAY_PER_SECOND * 12.0;
    private static final boolean
            DEBUG_TARGET_FLEE_POS = false,
            DEBUG_DISABLE_ANGER = false;
    private static final Identifier
            FLEE_BONUS_ID = ApConstants.identifier("flee_bonus"),
            ANGER_BONUS_ID = ApConstants.identifier("anger_bonus"),
            SCARE_FROZEN_ID = ApConstants.identifier("scare_frozen");

    private final MonsterArgs args;
    private final CommonData common;
    private final VisibilityChecker visibilityChecker;
    private final EndermanEscape escape;
    private final UnstuckBehaviour unstuck;
    private int timer = 0;
    private boolean screaming = false;
    private int scaredTimer = 0;
    private @Nullable BlockPos fleeTargetPos = null;
    private int fleeTargetTimeout = 0;
    private double anger = 0;
    private @Nullable UUID angerTarget = null;
    private int frozenTimer = 0;

    public EndermanData(MonsterArgs args, MSStruct struct) {
        this.args = args;

        MSManager manager = args.manager();

        this.common = new CommonData(args, List.of(
                new ValidPositionBehaviour(manager, args.logger()),
                new AccelerationBehaviour(0.35, 0.42),
                unstuck = new UnstuckBehaviour(manager, 0.75)
        ));

        this.visibilityChecker = new VisibilityChecker(manager.world());
        this.escape = new EndermanEscape(struct, visibilityChecker, manager.participants(), manager.debugController());
    }

    @Override
    public void init(EnderMan mob) {
        common.init(mob);
        unstuck.init(mob);
    }

    @Override
    public void tick(EnderMan mob) {
        unstuck.setEnabled(fleeTargetPos == null);

        common.tick(mob);
        unstuck.tick(mob);

        if (timer % VISIBLE_CHECK_INTERVAL_TICKS == 0) {
            checkLookedAt();
        }

        if (scaredTimer > 0 && --scaredTimer == 0) {
            setScreaming(false);
        }

        if (frozenTimer > 0 && --frozenTimer == 0) {
            unfreeze(mob);
        }

        // timeout mob once the flee position is reached
        if (fleeTargetTimeout > 0) {
            if (--fleeTargetTimeout == 0) {
                stopFleeing(mob);
            }
        } else if (fleeTargetPos != null && fleeTargetPos.distToCenterSqr(mob.position()) <= 1.44)  {
            fleeTargetTimeout = FLEE_TIMEOUT_TICKS;
        }

        // validate anger target
        if (angerTarget != null) {
            ServerPlayer player = args.manager().participants().getParticipant(angerTarget);

            if (player == null || !player.isAlive() || player.isSpectator()) {
                anger = 0;
                angerTarget = null;
            }
        }

        if (timer % 20 == 0) {
            setAnger(max(0.0, anger - ANGER_DECAY_PER_SECOND), null);
        }

        timer++;
    }

    @Override
    public void onKillAcquired(EnderMan mob) {
        common.onKillAcquired(mob);
        setAnger(0, null);
        stopFleeing(mob);
    }

    @Override
    public @Nullable EnderMan mob() {
        if (common.mob() instanceof EnderMan enderman) {
            return enderman;
        }

        return null;
    }

    private void checkLookedAt() {
        var mob = mob();

        if (mob == null) return;

        ServerPlayer looking = visibilityChecker.getAnyoneLookingAt(mob, mob.position(), args.manager().participants());

        if (looking != null) {
            onLookedAt(mob, looking);
        }
    }

    private void onLookedAt(EnderMan mob, ServerPlayer player) {
        if (isAngry()) return;

        boolean wasFleeing = isFleeing();

        setScreaming(true);
        scaredTimer = SCARED_TICKS;

        if (fleeTargetPos == null || visibilityChecker.isAnyoneLookingAt(mob, Vec3.atBottomCenterOf(fleeTargetPos), args.manager().participants())) {
            var optPath = escape.findEscapePath(mob);

            if (DEBUG_TARGET_FLEE_POS) {
                DebugController parent = args.manager().debugController().parent();
                parent.renderer().ifPresent(renderer -> parent.exclusive("target_flee_pos", _ -> {
                    if (optPath.isEmpty()) return;

                    BlockPos pos = optPath.get().getTarget();
                    renderer.marker(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Blocks.CONCRETE.cyan().defaultBlockState(), 0x03b2fe, 0.5);
                }));
            }

            optPath.ifPresentOrElse(path -> flee(mob, path), () -> angerFully(player));
        }

        if (wasFleeing) {
            setAnger(anger + LOOK_AT_ANGER_AMOUNT, player);
        } else {
            setAnger(anger + SCARE_ANGER_AMOUNT, player);
            playSoundFar(player, mob, SoundEvents.ENDERMAN_HURT, 0.5f, 1.4f);
            freeze(mob);  // freeze is temporarily
        }
    }

    private void setAnger(double amount, @Nullable ServerPlayer player) {
        if (DEBUG_DISABLE_ANGER) return;

        boolean wasAngry = isAngry();

        anger = amount;

        if (anger >= ANGER_TRIGGER_THRESHOLD) {
            if (player != null) {
                angerTarget = player.getUUID();
            }
        } else {
            angerTarget = null;
        }

        boolean angry = isAngry();

        if (wasAngry == angry) return;

        // state changed
        setScreaming(angry);

        if (angry) {
            anger += ANGER_TRIGGER_BONUS;
            scaredTimer = 0;
        }

        EnderMan mob = mob();

        if (mob == null) return;

        stopFleeing(mob);
        unfreeze(mob);

        mob.setSilent(!angry);
        mob.getEntityData().set(EnderManAccessor.DATA_STARED_AT(), angry);

        if (angry) {
            addAttributeModifier(mob, MOVEMENT_SPEED, ANGER_BONUS_ID, ANGER_SPEED_BONUS, ADD_VALUE);
        } else {
            removeAttributeModifier(mob, MOVEMENT_SPEED, ANGER_BONUS_ID);
        }

        if (angry && player != null) {
            playSoundFar(player, mob, SoundEvents.ENDERMAN_SCREAM, 1f, 1f);
        }
    }

    private void playSoundFar(@NotNull ServerPlayer player, EnderMan mob, SoundEvent sound, float volume, float pitch) {
        Level world = mob.level();

        double dist = 16 * volume;

        if (player.distanceToSqr(mob) >= dist * dist) {
            world.playSound(player, mob.blockPosition(), sound, mob.getSoundSource(), volume, pitch);
            ServerPlayerAccess.playSoundToPlayer(player, sound, mob.getSoundSource(), volume, pitch);
        } else {
            world.playSound(null, mob.blockPosition(), sound, mob.getSoundSource(), volume, pitch);
        }
    }

    private void flee(EnderMan mob, Path path) {
        fleeTargetTimeout = 0;
        fleeTargetPos = path.getTarget();
        mob.getNavigation().moveTo(path, 1);

        addAttributeModifier(mob, MOVEMENT_SPEED, FLEE_BONUS_ID, FLEE_SPEED_BONUS, ADD_VALUE);
    }

    private void stopFleeing(EnderMan mob) {
        fleeTargetPos = null;
        fleeTargetTimeout = 0;
        frozenTimer = 0;

        if (DEBUG_TARGET_FLEE_POS) {
            args.manager().debugController().parent().exclusive("target_flee_pos", _ -> {});
        }

        removeAttributeModifier(mob, MOVEMENT_SPEED, FLEE_BONUS_ID);
    }

    private void angerFully(ServerPlayer player) {
        setAnger(ANGER_TRIGGER_THRESHOLD, player);
    }

    public boolean isScreaming() {
        return screaming;
    }

    private void setScreaming(boolean screaming) {
        this.screaming = screaming;

        EnderMan mob = mob();

        if (mob != null) {
            mob.getEntityData().set(EnderManAccessor.DATA_CREEPY(), screaming);
        }
    }

    public @Nullable BlockPos targetPos() {
        if (angerTarget != null) {
            ServerPlayer player = args.manager().participants().getParticipant(angerTarget);

            BlockPos angerTargetPos = player != null && !player.isSpectator()
                    ? player.blockPosition()
                    : null;

            if (angerTargetPos != null) {
                return angerTargetPos;
            }
        }

        if (fleeTargetPos != null) {
            return fleeTargetPos;
        }

        EnderMan mob = mob();

        if (mob == null) {
            return null;
        }

        LivingEntity target = mob.getTarget();

        if (target == null || !target.isAlive() || target.isSpectator()) {
            return null;
        }

        return target.blockPosition();
    }

    public boolean isAngry() {
        return anger >= ANGER_TRIGGER_THRESHOLD;
    }

    public boolean isFleeing() {
        return fleeTargetPos != null && angerTarget == null;
    }

    private void freeze(Mob mob) {
        frozenTimer = SCARE_FROZEN_TICKS;

        addAttributeModifier(mob, MOVEMENT_SPEED, SCARE_FROZEN_ID, Double.NEGATIVE_INFINITY, ADD_VALUE);
    }

    private void unfreeze(Mob mob) {
        frozenTimer = 0;
        removeAttributeModifier(mob, MOVEMENT_SPEED, SCARE_FROZEN_ID);
    }
}
