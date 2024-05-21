package work.lclpnet.ap2.game.guess_it.challenge;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.block.*;
import net.minecraft.block.enums.RailShape;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.util.world.AdjacentBlocks;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.MobSpawner;
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks;
import work.lclpnet.ap2.impl.util.world.stage.Stage;
import work.lclpnet.kibu.access.entity.FireworkEntityAccess;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.LocalizedFormat;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.*;

import static net.minecraft.util.math.Direction.*;

public class MinecartChallenge implements Challenge, LongerChallenge, SchedulerAction {

    private static final float TURN_CHANCE = 0.2f;
    private static final int DURATION_TICKS = Ticks.seconds(16);
    private static final int MAX_RUNTIME_TICKS = Ticks.seconds(35);
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Random random;
    private final Stage stage;
    private final WorldModifier modifier;
    private BlockPos powerPos = null;
    private UUID minecartUuid = null;
    private Runnable onDone = null;
    private BlockPos goal = null;
    private int finalTime = 0;
    private int running = 0;

    public MinecartChallenge(MiniGameHandle gameHandle, ServerWorld world, Random random, Stage stage, WorldModifier modifier) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.stage = stage;
        this.modifier = modifier;
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
        TranslationService translations = gameHandle.getTranslations();
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
        modifier.setBlockState(powerPos, Blocks.REDSTONE_BLOCK.getDefaultState(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS);

        BlockPos up = powerPos.up();
        modifier.setBlockState(up, world.getBlockState(up).with(PoweredRailBlock.POWERED, true), Block.FORCE_STATE | Block.NOTIFY_LISTENERS);

        onDone = () -> {
            finalTime = (int) (System.currentTimeMillis() - startTime);
            callback.run();
        };

        running = 0;
        gameHandle.getGameScheduler().interval(this, 1);
    }

    @Override
    public void run(RunningTask info) {
        Entity entity = world.getEntity(minecartUuid);

        if (entity != null && !isOnGoal(entity) && ++running < MAX_RUNTIME_TICKS) return;

        info.cancel();
        onDone.run();

        if (entity == null) return;

        entity.getPassengersDeep().forEach(Entity::discard);
        entity.discard();

        FireworkExplosionComponent explosion = new FireworkExplosionComponent(FireworkExplosionComponent.Type.SMALL_BALL, IntList.of(0xff0000), IntList.of(), false, false);

        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(1, List.of(explosion)));

        FireworkRocketEntity firework = new FireworkRocketEntity(world, entity.getX(), entity.getY(), entity.getZ(), rocket);
        world.spawnEntity(firework);

        FireworkEntityAccess.explode(firework);
    }

    private boolean isOnGoal(Entity entity) {
        return goal.getX() == entity.getBlockX() && goal.getZ() == entity.getBlockZ();
    }

    private void generateTracks() {
        Set<BlockPos> positions = new HashSet<>();

        var it = stage.groundPositionIterator();

        while (it.hasNext()) {
            positions.add(it.next().toImmutable());
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
                state = Blocks.DETECTOR_RAIL.getDefaultState().with(DetectorRailBlock.SHAPE, shape);
            } else if (nextPower-- <= 0 && dir == nextDir) {
                nextPower = 5 + random.nextInt(9);
                state = Blocks.POWERED_RAIL.getDefaultState()
                        .with(PoweredRailBlock.SHAPE, shape)
                        .with(PoweredRailBlock.POWERED, true);
            } else {
                state = Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, shape);
            }

            if (i > 0 && state.isOf(Blocks.POWERED_RAIL)) {
                modifier.setBlockState(track.pos.down(), Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
            }

            modifier.setBlockState(track.pos, state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }

        PosDir firstTrack = tracks[0];
        BlockPos buffer = firstTrack.pos.offset(firstTrack.dir.getOpposite());

        modifier.setBlockState(buffer, Blocks.POLISHED_ANDESITE.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);

        double x = firstTrack.pos.getX() + 0.5;
        double y = firstTrack.pos.getY();
        double z = firstTrack.pos.getZ() + 0.5;

        MinecartEntity minecart = new MinecartEntity(EntityType.MINECART, world);
        minecart.setPos(x, y, z);

        VillagerEntity villager = new VillagerEntity(EntityType.VILLAGER, world);
        new MobSpawner(world, random).randomizeEntity(villager);
        villager.setPos(x, y, z);

        modifier.spawnEntity(minecart);
        modifier.spawnEntity(villager);

        villager.startRiding(minecart, true);

        powerPos = firstTrack.pos.down();
        minecartUuid = minecart.getUuid();
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

                Direction dir = Direction.fromVector(dx, 0, dz);

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
            BlockPos buffer = start.offset(direction);
            open.add(buffer);

            // block possible tracks besides the first track
            closed.add(start.offset(direction.rotateYClockwise()));
            closed.add(start.offset(direction.rotateYCounterclockwise()));

            // ensure that there is buffer space in the other direction
            buffer = start.offset(direction.getOpposite());
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

                    open.add(pos.toImmutable());
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
            BlockPos newPos = current.pos.offset(current.dir);

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
            BlockPos newPos = current.pos.offset(newDir);

            if (open.contains(newPos)) {
                return new PosDir(newPos, newDir);
            }

            // try to turn the other way
            turn = turn.opposite();
            newDir = turnDirection(current.dir, turn);
            newPos = current.pos.offset(newDir);

            if (open.contains(newPos)) {
                return new PosDir(newPos, newDir);
            }

            return null;
        }

        private static Direction turnDirection(Direction direction, Turn turn) {
            return switch (turn) {
                case LEFT -> direction.rotateYCounterclockwise();
                case RIGHT -> direction.rotateYClockwise();
            };
        }
    }
}
