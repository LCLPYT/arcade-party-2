package work.lclpnet.ap2.game.guess_it.challenge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import work.lclpnet.ap2.api.util.world.AdjacentBlocks;
import work.lclpnet.ap2.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.OptionMaker;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.game.util.WorldModifier;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

import static work.lclpnet.ap2.impl.util.world.PositionUtil.findGroundPositions;

public class AreaChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(16);
    private final MiniGameHandle gameHandle;
    private final ServerLevel world;
    private final Random random;
    private final BlockShape blockShape;
    private final WorldModifier modifier;
    private Areas areas = null;

    public AreaChallenge(MiniGameHandle gameHandle, ServerLevel world, Random random, BlockShape blockShape, WorldModifier modifier) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.blockShape = blockShape;
        this.modifier = modifier;
    }

    @Override
    public String id() {
        return "area";
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
    public void begin(InputInterface input, ChallengeMessenger messenger) {
        Translations translations = gameHandle.getTranslations();
        messenger.task(translations.translateText("area"));

        var opts = OptionMaker.createOptions(Set.of(DyeColor.values()), 4, random);

        Function<DyeColor, Block> blockFunction = switch (random.nextInt(3)) {
            case 0 -> Blocks.WOOL::pick;
            case 1 -> Blocks.CONCRETE::pick;
            case 2 -> Blocks.CONCRETE_POWDER::pick;
            default -> throw new IllegalStateException();
        };

        var blockStates = opts.stream()
                .map(blockFunction)
                .map(Block::defaultBlockState)
                .toList();

        randomizeArea(opts, blockStates);

        input.expectSelection(blockStates.stream()
                .map(TextUtil::getVanillaName)
                .toArray(Component[]::new));
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(Component.empty()
                .append(areas.getBiggestAreaText())
                .append(" ")
                .append(areas.getDistributionText()));

        for (ServerPlayer player : gameHandle.getParticipants()) {
            var optChoice = choices.getOption(player);

            if (optChoice.isEmpty()) continue;

            int i = optChoice.getAsInt();

            if (areas.isMaxCount(i)) {
                result.grant(player, 3);
            }
        }
    }

    private void randomizeArea(List<DyeColor> dyeColors, List<BlockState> blockStates) {
        Set<BlockPos> open = new HashSet<>();

        for (BlockPos pos : findGroundPositions(blockShape, world)) {
            open.add(pos.immutable());
        }

        List<BlockPos> startingPoints = OptionMaker.createOptions(open, 4, random);

        areas = new Areas(open, dyeColors, blockStates, startingPoints);

        while (areas.isBuilding()) {
            areas.stepBuild();
        }

        areas.evaluate();
    }

    private class Areas {
        private final List<DyeColor> dyeColors;
        private final List<BlockState> blockStates;
        private final Propagation[] props = new Propagation[4];
        private int maxCount = 0;

        public Areas(Set<BlockPos> open, List<DyeColor> dyeColors, List<BlockState> blockStates, List<BlockPos> startingPoints) {
            this.dyeColors = dyeColors;
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

        public Component getBiggestAreaText() {
            return IntStream.range(0, props.length)
                    .filter(i -> props[i].getCount() == maxCount)
                    .mapToObj(blockStates::get)
                    .reduce(Component.empty(), (text, state) -> {
                        if (!text.getString().isEmpty()) {
                            text.append(", ");
                        }

                        return text.append(TextUtil.getVanillaName(state));
                    }, MutableComponent::append);
        }

        public Component getDistributionText() {
            int[] order = IntStream.range(0, props.length)
                    .boxed()
                    .sorted(Comparator.comparingInt((Integer i) -> props[i].getCount()).reversed())
                    .mapToInt(Integer::intValue)
                    .toArray();

            MutableComponent text = Component.literal("(");

            for (int j = 0; j < order.length; j++) {
                int i = order[j];

                if (j > 0) {
                    text.append(" / ");
                }

                DyeColor dye = dyeColors.get(i);
                int count = props[i].getCount();

                text.append(Component.literal(count + " ")
                        .append(Component.translatable("color.minecraft." + dye.getName()))
                        .withStyle(style -> style.withColor(dye.getTextColor())));
            }

            return text.append(")");
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
                    next.add(adjPos.immutable());
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
