package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.core.mixin.EndermanEntityAccessor;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.AccelerationBehaviour;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.UnstuckBehaviour;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.ValidPositionBehaviour;
import work.lclpnet.ap2.game.maze_scape.util.EndermanEscape;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.game.maze_scape.util.VisibilityChecker;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.List;
import java.util.UUID;

import static java.lang.Math.max;
import static net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_VALUE;
import static net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED;
import static work.lclpnet.ap2.impl.util.EntityUtil.addAttributeModifier;
import static work.lclpnet.ap2.impl.util.EntityUtil.removeAttributeModifier;

public class EndermanData implements MonsterData<EndermanEntity> {

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
    public void init(EndermanEntity mob) {
        common.init(mob);
        unstuck.init(mob);
    }

    @Override
    public void tick(EndermanEntity mob) {
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
        } else if (fleeTargetPos != null && fleeTargetPos.getSquaredDistance(mob.getPos()) <= 1.44)  {
            fleeTargetTimeout = FLEE_TIMEOUT_TICKS;
        }

        // validate anger target
        if (angerTarget != null) {
            ServerPlayerEntity player = args.manager().participants().getParticipant(angerTarget)
                    .filter(p -> p.isAlive() && !p.isSpectator())
                    .orElse(null);

            if (player == null) {
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
    public void onKillAcquired(EndermanEntity mob) {
        common.onKillAcquired(mob);
        setAnger(0, null);
        stopFleeing(mob);
    }

    @Override
    public @Nullable EndermanEntity mob() {
        if (common.mob() instanceof EndermanEntity enderman) {
            return enderman;
        }

        return null;
    }

    private void checkLookedAt() {
        var mob = mob();

        if (mob == null) return;

        ServerPlayerEntity looking = visibilityChecker.getAnyoneLookingAt(mob, mob.getPos(), args.manager().participants());

        if (looking != null) {
            onLookedAt(mob, looking);
        }
    }

    private void onLookedAt(EndermanEntity mob, ServerPlayerEntity player) {
        if (isAngry()) return;

        boolean wasFleeing = isFleeing();

        setScreaming(true);
        scaredTimer = SCARED_TICKS;

        if (fleeTargetPos == null || visibilityChecker.isAnyoneLookingAt(mob, fleeTargetPos.toBottomCenterPos(), args.manager().participants())) {
            var optPath = escape.findEscapePath(mob);

            if (DEBUG_TARGET_FLEE_POS) {
                DebugController parent = args.manager().debugController().parent();
                parent.renderer().ifPresent(renderer -> parent.exclusive("target_flee_pos", c -> {
                    if (optPath.isEmpty()) return;

                    BlockPos pos = optPath.get().getTarget();
                    renderer.marker(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Blocks.CYAN_CONCRETE.getDefaultState(), 0x03b2fe, 0.5);
                }));
            }

            optPath.ifPresentOrElse(path -> flee(mob, path), () -> angerFully(player));
        }

        if (wasFleeing) {
            setAnger(anger + LOOK_AT_ANGER_AMOUNT, player);
        } else {
            setAnger(anger + SCARE_ANGER_AMOUNT, player);
            playSoundFar(player, mob, SoundEvents.ENTITY_ENDERMAN_HURT, 0.5f, 1.4f);
            freeze(mob);  // freeze is temporarily
        }
    }

    private void setAnger(double amount, @Nullable ServerPlayerEntity player) {
        if (DEBUG_DISABLE_ANGER) return;

        boolean wasAngry = isAngry();

        anger = amount;

        if (anger >= ANGER_TRIGGER_THRESHOLD) {
            if (player != null) {
                angerTarget = player.getUuid();
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

        EndermanEntity mob = mob();

        if (mob == null) return;

        stopFleeing(mob);
        unfreeze(mob);

        mob.setSilent(!angry);
        mob.getDataTracker().set(EndermanEntityAccessor.PROVOKED(), angry);

        if (angry) {
            addAttributeModifier(mob, MOVEMENT_SPEED, ANGER_BONUS_ID, ANGER_SPEED_BONUS, ADD_VALUE);
        } else {
            removeAttributeModifier(mob, MOVEMENT_SPEED, ANGER_BONUS_ID);
        }

        if (angry && player != null) {
            playSoundFar(player, mob, SoundEvents.ENTITY_ENDERMAN_SCREAM, 1f, 1f);
        }
    }

    private void playSoundFar(@NotNull ServerPlayerEntity player, EndermanEntity mob, SoundEvent sound, float volume, float pitch) {
        World world = mob.getWorld();

        double dist = 16 * volume;

        if (player.squaredDistanceTo(mob) >= dist * dist) {
            world.playSound(player, mob.getBlockPos(), sound, mob.getSoundCategory(), volume, pitch);
            player.playSoundToPlayer(sound, mob.getSoundCategory(), volume, pitch);
        } else {
            world.playSound(null, mob.getBlockPos(), sound, mob.getSoundCategory(), volume, pitch);
        }
    }

    private void flee(EndermanEntity mob, Path path) {
        fleeTargetTimeout = 0;
        fleeTargetPos = path.getTarget();
        mob.getNavigation().startMovingAlong(path, 1);

        addAttributeModifier(mob, MOVEMENT_SPEED, FLEE_BONUS_ID, FLEE_SPEED_BONUS, ADD_VALUE);
    }

    private void stopFleeing(EndermanEntity mob) {
        fleeTargetPos = null;
        fleeTargetTimeout = 0;
        frozenTimer = 0;

        if (DEBUG_TARGET_FLEE_POS) {
            args.manager().debugController().parent().exclusive("target_flee_pos", debugger -> {});
        }

        removeAttributeModifier(mob, MOVEMENT_SPEED, FLEE_BONUS_ID);
    }

    private void angerFully(ServerPlayerEntity player) {
        setAnger(ANGER_TRIGGER_THRESHOLD, player);
    }

    public boolean isScreaming() {
        return screaming;
    }

    private void setScreaming(boolean screaming) {
        this.screaming = screaming;

        EndermanEntity mob = mob();

        if (mob != null) {
            mob.getDataTracker().set(EndermanEntityAccessor.ANGRY(), screaming);
        }
    }

    public @Nullable BlockPos targetPos() {
        if (angerTarget != null) {
            BlockPos angerTargetPos = args.manager().participants().getParticipant(angerTarget)
                    .filter(p -> !p.isSpectator())
                    .map(Entity::getBlockPos)
                    .orElse(null);

            if (angerTargetPos != null) {
                return angerTargetPos;
            }
        }

        if (fleeTargetPos != null) {
            return fleeTargetPos;
        }

        EndermanEntity mob = mob();

        if (mob == null) {
            return null;
        }

        LivingEntity target = mob.getTarget();

        if (target == null || !target.isAlive() || target.isSpectator()) {
            return null;
        }

        return target.getBlockPos();
    }

    public boolean isAngry() {
        return anger >= ANGER_TRIGGER_THRESHOLD;
    }

    public boolean isFleeing() {
        return fleeTargetPos != null && angerTarget == null;
    }

    private void freeze(MobEntity mob) {
        frozenTimer = SCARE_FROZEN_TICKS;

        addAttributeModifier(mob, MOVEMENT_SPEED, SCARE_FROZEN_ID, Double.NEGATIVE_INFINITY, ADD_VALUE);
    }

    private void unfreeze(MobEntity mob) {
        frozenTimer = 0;
        removeAttributeModifier(mob, MOVEMENT_SPEED, SCARE_FROZEN_ID);
    }
}
