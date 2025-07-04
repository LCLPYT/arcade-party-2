package work.lclpnet.ap2.game.guess_it.challenge;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.Shapes;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.world.stage.BlockShape;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.floor;

public class BlockCountChallenge<S extends BlockShape & BlockShape.WithRadius & BlockShape.WithHeight> implements Challenge, SchedulerAction {

    private static final boolean DEBUG_SHAPES = false;
    private static final int DURATION_TICKS = Ticks.seconds(20);

    private final MiniGameHandle gameHandle;
    private final Random random;
    private final S stage;
    private final WorldModifier modifier;
    private final DebugController debugController;
    private int amount = 0;
    private int distance = 0;
    private Map<Integer, List<BlockPos>> blocksByDistance = null;
    private BlockPos center = null;
    private int maxDistance = -1;
    private BlockState state = null;
    private Shapes.Shape shape = null;

    public BlockCountChallenge(MiniGameHandle gameHandle, Random random, S stage, WorldModifier modifier, DebugController debugController) {
        this.gameHandle = gameHandle;
        this.random = random;
        this.stage = stage;
        this.modifier = modifier;
        this.debugController = debugController;
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
        center = stage.center();
        shape = Shapes.getRandomShape(random, stage);

        List<BlockPos> blocks = new ArrayList<>();

        for (BlockPos pos : shape.bounds()) {
            if (shape.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                blocks.add(pos.toImmutable());
            }
        }

        amount = blocks.size();
        blocksByDistance = blocks.stream()
                .parallel()
                .filter(stage::contains)
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

        if (DEBUG_SHAPES) {
            debugController.exclusive("shape", shape::debug);
        }
    }

    @Override
    public void destroy() {
        if (DEBUG_SHAPES) {
            debugController.exclusive("shape", d -> {});
        }
    }

    @Override
    public void begin(InputInterface input, ChallengeMessenger messenger) {
        Translations translations = gameHandle.getTranslations();

        String name = shape.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        messenger.task(translations.translateText("game.ap2.guess_it.shape." + name));

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
        int dx = pos.getX() - center.getX();
        int dy = pos.getY() - center.getY();
        int dz = pos.getZ() - center.getZ();

        return (int) floor(shape.distance(dx, dy, dz));
    }
}
