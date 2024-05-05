package work.lclpnet.ap2.game.guess_it.challenge;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.*;
import java.util.stream.Collectors;

public class BlockCountChallenge implements Challenge, SchedulerAction {

    private static final int DURATION_TICKS = Ticks.seconds(20);
    private final MiniGameHandle gameHandle;
    private final Random random;
    private final Stage stage;
    private final WorldModifier modifier;
    private int amount = 0;
    private int distance = 0;
    private Map<Integer, List<BlockPos>> blocksByDistance = null;
    private int centerX = 0, centerY = 64, centerZ = 0;
    private int maxDistance = -1;
    private BlockState state = null;
    private Shape shape = null;

    public BlockCountChallenge(MiniGameHandle gameHandle, Random random, Stage stage, WorldModifier modifier) {
        this.gameHandle = gameHandle;
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
    public void prepare() {
        BlockPos center = stage.getCenter();
        centerX = center.getX();
        centerY = center.getY();
        centerZ = center.getZ();

        var shapes = Shape.values();
        shape = shapes[random.nextInt(shapes.length)];

        List<BlockPos> blocks = switch (shape) {
            case SPHERE -> generateSphere();
            case CUBE -> generateCube();
            case CONE -> generateCone();
        };

        amount = blocks.size();
        blocksByDistance = blocks.stream()
                .parallel()
                .collect(Collectors.groupingByConcurrent(this::distance));

        maxDistance = blocksByDistance.keySet().stream()
                .mapToInt(Integer::intValue)
                .max().orElse(-1);

        state = switch (random.nextInt(11)) {
            case 0 -> Blocks.DIAMOND_BLOCK.getDefaultState();
            case 1 -> Blocks.GOLD_BLOCK.getDefaultState();
            case 2 -> Blocks.EMERALD_BLOCK.getDefaultState();
            case 3 -> Blocks.IRON_BLOCK.getDefaultState();
            case 4 -> Blocks.REDSTONE_BLOCK.getDefaultState();
            case 5 -> Blocks.LAPIS_BLOCK.getDefaultState();
            case 6 -> Blocks.COAL_BLOCK.getDefaultState();
            case 7 -> Blocks.AMETHYST_BLOCK.getDefaultState();
            case 8 -> Blocks.NETHERITE_BLOCK.getDefaultState();
            case 9 -> Blocks.SMOOTH_QUARTZ.getDefaultState();
            case 10 -> Blocks.POLISHED_TUFF.getDefaultState();
            default -> throw new IllegalStateException();
        };

        distance = 0;

        gameHandle.getGameScheduler().interval(this, 5);
    }

    @Override
    public void begin(InputInterface input, ChallengeMessenger messenger) {
        TranslationService translations = gameHandle.getTranslations();
        messenger.task(translations.translateText("game.ap2.guess_it.shape." + shape.name().toLowerCase(Locale.ROOT)));

        input.expectInput().validateInt(translations);
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(amount);
        result.grantClosest3(gameHandle.getParticipants().getAsSet(), amount, choices::getInt);
    }

    @Override
    public void run(RunningTask info) {
        if (distance > maxDistance) {
            info.cancel();
            return;
        }

        int d = distance++;
        var blocks = blocksByDistance.get(d);

        if (blocks == null) return;

        for (BlockPos pos : blocks) {
            modifier.setBlockState(pos, state);
        }
    }

    private int distance(BlockPos pos) {
        int dx = pos.getX() - centerX;
        int dy = pos.getY() - centerY;
        int dz = pos.getZ() - centerZ;

        if (shape == Shape.SPHERE) {
            return (int) Math.floor(Math.sqrt(dx * dx + dy * dy + dz * dz));
        }

        return Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
    }

    @NotNull
    private List<BlockPos> generateSphere() {
        int minRadius = 4;
        int maxRadius = Math.min(stage.getHeight() / 2, stage.getRadius());

        int radius = Math.min(maxRadius, minRadius + random.nextInt(Math.max(1, stage.getRadius() - minRadius)));

        var iter = BlockPos.iterate(
                centerX - radius, centerY - radius, centerZ - radius,
                centerX + radius, centerY + radius, centerZ + radius);

        List<BlockPos> blocks = new ArrayList<>();

        float cx = centerX + 0.5f, cy = centerY + 0.5f, cz = centerZ + 0.5f;
        float radiusSq = radius * radius;

        for (BlockPos pos : iter) {
            float x = pos.getX() + 0.5f, y = pos.getY() + 0.5f, z = pos.getZ() + 0.5f;
            float dx = x - cx, dy = y - cy, dz = z - cz;

            if (dx * dx + dy * dy + dz * dz < radiusSq) {
                blocks.add(pos.toImmutable());
            }
        }

        return blocks;
    }

    @NotNull
    private List<BlockPos> generateCube() {
        int minRadius = 4;
        int maxRadius = (int) Math.floor(Math.sin(Math.PI * 0.25) * stage.getRadius());

        int radius = minRadius + random.nextInt(Math.max(1, maxRadius - minRadius));

        var iter = BlockPos.iterate(
                centerX - radius, centerY - radius, centerZ - radius,
                centerX + radius, centerY + radius, centerZ + radius);

        List<BlockPos> blocks = new ArrayList<>();

        for (BlockPos pos : iter) {
            blocks.add(pos.toImmutable());
        }

        return blocks;
    }

    @NotNull
    private List<BlockPos> generateCone() {
        int minRadius = 4;
        int minHeight = 10;

        int maxHeight = stage.getHeight();
        int height = minHeight + random.nextInt(Math.max(1, maxHeight - minHeight));

        int maxRadius = Math.min(height / 2, stage.getRadius());
        int radius = minRadius + random.nextInt(Math.max(1, maxRadius - minRadius));

        int halfHeight = height / 2;
        int remainder = height % 2;

        int bottomY = centerY - halfHeight - remainder;
        int topY = centerY + halfHeight;

        var iter = BlockPos.iterate(
                centerX - radius, bottomY, centerZ - radius,
                centerX + radius, topY, centerZ + radius);

        List<BlockPos> blocks = new ArrayList<>();

        Vec3d coneTip = new Vec3d(centerX + 0.5, topY + 0.5, centerZ + 0.5);
        Vec3d dir = new Vec3d(0, -1, 0);

        for (BlockPos pos : iter) {
            Vec3d point = pos.toCenterPos();

            if (isInCone(point, coneTip, dir, radius, height)) {
                blocks.add(pos.toImmutable());
            }
        }

        return blocks;
    }

    private static boolean isInCone(Vec3d p, Vec3d x, Vec3d dir, float radius, float height) {
        Vec3d px = p.subtract(x);
        double cone_dist = px.dotProduct(dir);
        double cone_radius = (cone_dist / height) * radius;
        double orth_distance = px.subtract(dir.multiply(cone_dist)).length();

        return orth_distance < cone_radius;
    }

    private enum Shape {
        SPHERE,
        CUBE,
        CONE
    }
}
