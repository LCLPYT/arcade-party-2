package work.lclpnet.ap2.game.guess_it.challenge;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.util.world.AdjacentBlocks;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.OptionMaker;
import work.lclpnet.ap2.impl.util.BlockHelper;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

import static net.minecraft.util.Formatting.BOLD;
import static net.minecraft.util.Formatting.DARK_GREEN;

public class AreaChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(16);
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Random random;
    private final Stage stage;
    private final WorldModifier modifier;
    private Areas areas = null;

    public AreaChallenge(MiniGameHandle gameHandle, ServerWorld world, Random random, Stage stage, WorldModifier modifier) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.stage = stage;
        this.modifier = modifier;
    }

    @Override
    public String getPreparationKey() {
        return GuessItConstants.PREPARE_GUESS;
    }

    @Override
    public int getDurationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void begin(InputInterface input) {
        gameHandle.getTranslations().translateText("game.ap2.guess_it.area")
                .formatted(DARK_GREEN, BOLD)
                .sendTo(PlayerLookup.world(world));

        var opts = OptionMaker.createOptions(Set.of(DyeColor.values()), 4, random);

        Function<DyeColor, Block> blockFunction = switch (random.nextInt(3)) {
            case 0 -> BlockHelper::getWool;
            case 1 -> BlockHelper::getConcrete;
            case 2 -> BlockHelper::getConcretePowder;
            default -> throw new IllegalStateException();
        };

        var blockStates = opts.stream()
                .map(blockFunction)
                .map(Block::getDefaultState)
                .toList();

        randomizeArea(blockStates);

        input.expectSelection(blockStates.stream()
                .map(TextUtil::getVanillaName)
                .toArray(Text[]::new));
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(areas.getBiggestAreaText());

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            var optChoice = choices.getOption(player);

            if (optChoice.isEmpty()) continue;

            int i = optChoice.getAsInt();

            if (areas.isMaxCount(i)) {
                result.grant(player, 3);
            }
        }
    }

    private void randomizeArea(List<BlockState> blockStates) {
        Set<BlockPos> open = new HashSet<>();

        var it = stage.groundPositionIterator();

        while (it.hasNext()) {
            BlockPos pos = it.next();
            open.add(pos.toImmutable());
        }

        List<BlockPos> startingPoints = OptionMaker.createOptions(open, 4, random);

        areas = new Areas(open, blockStates, startingPoints);

        while (areas.isBuilding()) {
            areas.stepBuild();
        }

        areas.evaluate();
    }

    private class Areas {
        private final List<BlockState> blockStates;
        private final Propagation[] props = new Propagation[4];
        private int maxCount = 0;

        public Areas(Set<BlockPos> open, List<BlockState> blockStates, List<BlockPos> startingPoints) {
            this.blockStates = blockStates;

            startingPoints.forEach(open::remove);

            AdjacentBlocks adjacent = new SimpleAdjacentBlocks(open::contains, 0);

            for (int i = 0; i < 4; i++) {
                BlockPos start = startingPoints.get(i);
                BlockState state = blockStates.get(i);

                Propagation propagation = new Propagation(adjacent, start, open::remove, pos -> modifier.setBlockState(pos, state));

                props[i] = propagation;
            }
        }

        public boolean isBuilding() {
            for (Propagation prop : props) {
                if (prop.hasNext()) {
                    return true;
                }
            }

            return false;
        }

        public void stepBuild() {
            for (Propagation prop : props) {
                prop.propagate();
            }
        }

        public void evaluate() {
            maxCount = Arrays.stream(props)
                    .mapToInt(Propagation::getCount)
                    .max().orElse(0);
        }

        public boolean isMaxCount(int index) {
            if (index < 0 || index >= props.length) {
                return false;
            }

            return props[index].getCount() == maxCount;
        }

        public Text getBiggestAreaText() {
            return IntStream.range(0, props.length)
                    .filter(i -> props[i].getCount() == maxCount)
                    .mapToObj(blockStates::get)
                    .reduce(Text.empty(), (text, state) -> {
                        if (!text.getString().isEmpty()) {
                            text.append(", ");
                        }

                        return text.append(TextUtil.getVanillaName(state));
                    }, MutableText::append);
        }
    }

    private static class Propagation {

        private final AdjacentBlocks adjacent;
        private final Consumer<BlockPos> reserve;
        private final Consumer<BlockPos> action;
        private List<BlockPos> queue = new ArrayList<>();
        private List<BlockPos> next = new ArrayList<>();
        private int count = 0;

        public Propagation(AdjacentBlocks adjacent, BlockPos start, Consumer<BlockPos> reserve, Consumer<BlockPos> action) {
            this.adjacent = adjacent;
            this.reserve = reserve;
            this.action = action;
            queue.add(start);
        }

        public void propagate() {
            for (BlockPos pos : queue) {
                action.accept(pos);

                for (BlockPos adjPos : adjacent.iterate(pos)) {
                    reserve.accept(adjPos);
                    next.add(adjPos.toImmutable());
                }
            }

            count += queue.size();
            queue.clear();

            // flip lists
            List<BlockPos> swp = queue;
            queue = next;
            next = swp;
        }

        public boolean hasNext() {
            return !queue.isEmpty();
        }

        public int getCount() {
            return count;
        }
    }
}
