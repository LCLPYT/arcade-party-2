package work.lclpnet.ap2.game.guess_it.challenge;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.world.AdjacentBlocks;
import work.lclpnet.ap2.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.MobSpawner;
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.gaco.ds.IndexedSet;
import work.lclpnet.game.util.WorldModifier;
import work.lclpnet.kibu.access.entity.FireworkEntityAccess;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.LocalizedFormat;

import java.util.*;

import static net.minecraft.core.Direction.*;
import static work.lclpnet.ap2.impl.util.world.PositionUtil.findGroundPositions;

public class MinecartChallenge implements Challenge, LongerChallenge, SchedulerAction {

    private static final float TURN_CHANCE = 0.2f;
    private static final int DURATION_TICKS = Ticks.seconds(16);
    private static final int MAX_RUNTIME_TICKS = Ticks.seconds(35);
    private final MiniGameHandle gameHandle;
    private final ServerLevel world;
    private final Random random;
    private final BlockShape blockShape;
    private final WorldModifier modifier;
    private BlockPos powerPos = null;
    private UUID minecartUuid = null;
    private Runnable onDone = null;
    private BlockPos goal = null;
    private int finalTime = 0;
    private int running = 0;

    public MinecartChallenge(MiniGameHandle gameHandle, ServerLevel world, Random random, BlockShape blockShape, WorldModifier modifier) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.blockShape = blockShape;
        this.modifier = modifier;
    }

    @Override
    public String id() {
        return "minecart";
    }

    @Override
    public String getPreparationKey() {
        return GuessItConstants.PREPARE_ESTIMATE;
    }

    @Override
    public int getDurationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void begin(InputInterface input, ChallengeMessenger messenger) {
        Translations translations = gameHandle.getTranslations();
        messenger.task(translations.translateText("game.ap2.guess_it.minecart"));

        input.expectInput().validateFloat(translations, 3);

        generateTracks();
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(LocalizedFormat.format("%.3f", finalTime / 1000f));
        result.grantClosest3(gameHandle.getParticipants().getAsSet(), finalTime, player -> choices.getFloat(player)
                .map(f -> Math.round(f * 1000))
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty));
    }

    @Override
    public void evaluateDeferred(Runnable callback) {
        long startTime = System.currentTimeMillis();
        modifier.setBlockState(powerPos, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);

        BlockPos up = powerPos.above();
        modifier.setBlockState(up, world.getBlockState(up).setValue(PoweredRailBlock.POWERED, true), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);

        onDone = () -> {
            finalTime = (int) (System.currentTimeMillis() - startTime);
            callback.run();
        };

        running = 0;
        gameHandle.getScheduler().interval(this, 1);
    }

    @Override
    public void run(RunningTask info) {
        Entity entity = world.getEntity(minecartUuid);

        if (entity != null && !isOnGoal(entity) && ++running < MAX_RUNTIME_TICKS) return;

        info.cancel();
        onDone.run();

        if (entity == null) return;

        entity.getIndirectPassengers().forEach(Entity::discard);
        entity.discard();

        FireworkExplosion explosion = new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, IntList.of(0xff0000), IntList.of(), false, false);

        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));

        FireworkRocketEntity firework = new FireworkRocketEntity(world, entity.getX(), entity.getY(), entity.getZ(), rocket);
        world.addFreshEntity(firework);

        FireworkEntityAccess.explode(firework);
    }

    private boolean isOnGoal(Entity entity) {
        return goal.getX() == entity.getBlockX() && goal.getZ() == entity.getBlockZ();
    }

    private void generateTracks() {
        Set<BlockPos> positions = new HashSet<>();

        for (BlockPos pos : findGroundPositions(blockShape, world)) {
            positions.add(pos.immutable());
        }

        if (positions.isEmpty()) {
            throw new IllegalStateException("No ground positions in stage");
        }

        int trackCount = 30 + random.nextInt(71);

        BlockPos start = positions.stream().skip(random.nextInt(positions.size())).findFirst().orElseThrow();
        AdjacentBlocks adjacent = new SimpleAdjacentBlocks(positions::contains, 0);
        PosDir[] tracks = new Generator().generate(start, trackCount, adjacent);

        int nextPower = 0;

        for (int i = 0; i < tracks.length; i++) {
            PosDir track = tracks[i];
            Direction dir = track.dir;

            boolean last = i == tracks.length - 1;
            Direction nextDir = last ? null : tracks[i + 1].dir;
            RailShape shape = getRailShape(dir, nextDir);

            BlockState state;

            if (last) {
                shape = getRailShape(dir, null);
                state = Blocks.DETECTOR_RAIL.defaultBlockState().setValue(DetectorRailBlock.SHAPE, shape);
            } else if (nextPower-- <= 0 && dir == nextDir) {
                nextPower = 5 + random.nextInt(9);
                state = Blocks.POWERED_RAIL.defaultBlockState()
                        .setValue(PoweredRailBlock.SHAPE, shape)
                        .setValue(PoweredRailBlock.POWERED, true);
            } else {
                state = Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape);
            }

            if (i > 0 && state.is(Blocks.POWERED_RAIL)) {
                modifier.setBlockState(track.pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            }

            modifier.setBlockState(track.pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        PosDir firstTrack = tracks[0];
        BlockPos buffer = firstTrack.pos.relative(firstTrack.dir.getOpposite());

        modifier.setBlockState(buffer, Blocks.POLISHED_ANDESITE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);

        double x = firstTrack.pos.getX() + 0.5;
        double y = firstTrack.pos.getY();
        double z = firstTrack.pos.getZ() + 0.5;

        Minecart minecart = new Minecart(EntityType.MINECART, world);
        minecart.setPosRaw(x, y, z);

        Villager villager = new Villager(EntityType.VILLAGER, world);
        new MobSpawner(world, random, new IndexedSet<>()).randomizeEntity(villager);
        villager.setPosRaw(x, y, z);

        modifier.spawnEntity(minecart);
        modifier.spawnEntity(villager);

        villager.startRiding(minecart, true, false);

        powerPos = firstTrack.pos.below();
        minecartUuid = minecart.getUUID();
        goal = tracks[tracks.length - 1].pos;
    }

    private static RailShape getRailShape(Direction pre, @Nullable Direction post) {
        if (post == null) {
            return switch (pre) {
                case NORTH, SOUTH -> RailShape.NORTH_SOUTH;
                default -> RailShape.EAST_WEST;
            };
        }

        // NS
        if (pre == NORTH && post == NORTH || pre == SOUTH && post == SOUTH) {
            return RailShape.NORTH_SOUTH;
        }

        // EW
        if (pre == EAST && post == EAST || pre == WEST && post == WEST) {
            return RailShape.EAST_WEST;
        }

        // NE
        if (pre == NORTH && post == EAST || pre == EAST && post == NORTH) {
            return RailShape.NORTH_EAST;
        }

        // NW
        if (pre == NORTH && post == WEST || pre == WEST && post == NORTH) {
            return RailShape.NORTH_WEST;
        }

        // SE
        if (pre == SOUTH && post == EAST || pre == EAST && post == SOUTH) {
            return RailShape.SOUTH_EAST;
        }

        // SW
        return RailShape.SOUTH_WEST;
    }

    enum Turn {
        LEFT,
        RIGHT;

        Turn opposite() {
            return values()[1 - ordinal()];
        }
    }

    record PosDir(BlockPos pos, Direction dir) {}

    private class Generator {
        Set<BlockPos> open = new HashSet<>();
        Set<BlockPos> closed = new HashSet<>();
        Stack<PosDir> path = new Stack<>();

        public PosDir[] generate(BlockPos start, int length, AdjacentBlocks adjacent) {
            List<Direction> directions = new ArrayList<>();

            for (BlockPos pos : adjacent.iterate(start)) {
                int dx = pos.getX() - start.getX();
                int dz = pos.getZ() - start.getZ();

                Direction dir = Direction.getNearest(dx, 0, dz, null);

                if (dir != null) {
                    directions.add(dir);
                }
            }

            if (directions.isEmpty()) {
                throw new IllegalStateException("Cannot find starting direction");
            }

            Direction direction = directions.get(random.nextInt(directions.size()));

            path.push(new PosDir(start, direction));
            closed.add(start);

            // force the second path position
            BlockPos buffer = start.relative(direction);
            open.add(buffer);

            // block possible tracks besides the first track
            closed.add(start.relative(direction.getClockWise()));
            closed.add(start.relative(direction.getCounterClockWise()));

            // ensure that there is buffer space in the other direction
            buffer = start.relative(direction.getOpposite());
            closed.add(buffer);

            while (!path.isEmpty() && path.size() < length) {
                PosDir current = path.peek();
                PosDir next = next(current);

                if (next == null) {
                    // backtracking
                    path.pop();
                    continue;
                }

                open.remove(next.pos);
                closed.add(next.pos);
                path.push(next);

                for (BlockPos pos : adjacent.iterate(next.pos)) {
                    if (closed.contains(pos)) continue;

                    open.add(pos.immutable());
                }
            }

            if (path.size() < 2) {
                throw new IllegalStateException("Could not generate minecart tracks");
            }

            return path.toArray(PosDir[]::new);
        }

        private PosDir next(PosDir current) {
            boolean turnTried = false;

            if (random.nextFloat() < TURN_CHANCE) {
                // try to turn
                PosDir next = turn(current);

                if (next != null) {
                    return next;
                }

                turnTried = true;
            }

            // go straight
            BlockPos newPos = current.pos.relative(current.dir);

            if (open.contains(newPos)) {
                return new PosDir(newPos, current.dir);
            }

            if (turnTried) {
                return null;
            }

            // try to turn
            return turn(current);
        }

        @Nullable
        private PosDir turn(PosDir current) {
            Turn turn = Turn.values()[random.nextInt(Turn.values().length)];
            Direction newDir = turnDirection(current.dir, turn);
            BlockPos newPos = current.pos.relative(newDir);

            if (open.contains(newPos)) {
                return new PosDir(newPos, newDir);
            }

            // try to turn the other way
            turn = turn.opposite();
            newDir = turnDirection(current.dir, turn);
            newPos = current.pos.relative(newDir);

            if (open.contains(newPos)) {
                return new PosDir(newPos, newDir);
            }

            return null;
        }

        private static Direction turnDirection(Direction direction, Turn turn) {
            return switch (turn) {
                case LEFT -> direction.getCounterClockWise();
                case RIGHT -> direction.getClockWise();
            };
        }
    }
}
