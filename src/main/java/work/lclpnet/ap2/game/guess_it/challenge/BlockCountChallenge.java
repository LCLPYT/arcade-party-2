package work.lclpnet.ap2.game.guess_it.challenge;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.BlockCountShapeManager;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.math.shape.Shape;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.lang.Math.floor;
import static net.minecraft.server.command.CommandManager.argument;

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
    private Shape shape = null;
    private BlockCountShapeManager<S> shapeManager;

    public BlockCountChallenge(MiniGameHandle gameHandle, Random random, S stage, WorldModifier modifier, DebugController debugController) {
        this.gameHandle = gameHandle;
        this.random = random;
        this.stage = stage;
        this.modifier = modifier;
        this.debugController = debugController;
    }

    @Override
    public String id() {
        return "block_count";
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
    public void init(@Nullable Object init) {
        shapeManager = new BlockCountShapeManager<>(random, stage);

        if (init instanceof String shapeStr) {
            shape = shapeManager.getShape(shapeStr);
        }
    }

    @Override
    public void prepare() {
        center = stage.center();

        if (shape == null) {
            shape = shapeManager.getRandomShape();
        }

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

        state = switch (random.nextInt(12)) {
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
            case 10 -> Blocks.CRYING_OBSIDIAN.getDefaultState();
            case 11 -> Blocks.RESIN_BLOCK.getDefaultState();
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
        shape = null;

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

    @Override
    public void provideInitCommand(LiteralArgumentBuilder<ServerCommandSource> node, Initializer init) {
        node.then(argument("shape", StringArgumentType.word())
                .suggests(this::suggestShapes)
                .executes(ctx -> setShape(ctx, init)));
    }

    private CompletableFuture<Suggestions> suggestShapes(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        for (String shape : shapeManager.getShapes()) {
            builder.suggest(shape);
        }

        return builder.buildFuture();
    }

    private int setShape(CommandContext<ServerCommandSource> ctx, Initializer init) {
        String str = StringArgumentType.getString(ctx, "shape");

        if (!shapeManager.getShapes().contains(str)) {
            ctx.getSource().sendError(Text.literal("Unknown shape \"%s\"".formatted(str)));
            return 0;
        }

        init.accept(ctx, str);

        return 1;
    }
}
