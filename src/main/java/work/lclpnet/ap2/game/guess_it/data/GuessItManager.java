package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.challenge.*;
import work.lclpnet.ap2.game.guess_it.util.GuessItDisplay;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.*;

public class GuessItManager {

    private final Random random;
    private final Set<Challenge> challenges = new LinkedHashSet<>();
    private final List<Challenge> queue = new ArrayList<>();

    public GuessItManager(MiniGameHandle gameHandle, ServerWorld world, Random random, BlockShape blockShape,
                          WorldModifier modifier, SoundSubtitles soundSubtitles, DebugController debugController) {
        this.random = random;

        var stageRadiusHeight = validateStage(blockShape);

        GuessItDisplay display = new GuessItDisplay(world, modifier, blockShape);

        challenges.add(new MathsChallenge(gameHandle, random));
        challenges.add(new DayTimeChallenge(gameHandle, world, random));
        challenges.add(new MobCountSingleChallenge(gameHandle, world, random, blockShape, modifier));
        challenges.add(new MobCountMultiChallenge(gameHandle, world, random, blockShape, modifier));
        challenges.add(new DistinctMobCountChallenge(gameHandle, world, random, blockShape, modifier));
        challenges.add(new SoundChallenge(gameHandle, world, random, soundSubtitles));
        challenges.add(new CakeBitesChallenge(gameHandle, world, random, blockShape, modifier));
        challenges.add(new PotionTypeChallenge(gameHandle, random, display));
        challenges.add(new FoodAmountChallenge(gameHandle, random, display));
        challenges.add(new ArmorTrimChallenge(gameHandle, world, random, blockShape, modifier));
        challenges.add(new BlockCountChallenge<>(gameHandle, random, stageRadiusHeight, modifier, debugController));
        challenges.add(new RecordChallenge(gameHandle, world, random, display));
        challenges.add(new AreaChallenge(gameHandle, world, random, blockShape, modifier));
        challenges.add(new MinecartChallenge(gameHandle, world, random, blockShape, modifier));
    }

    @SuppressWarnings("unchecked")
    private <S extends BlockShape & BlockShape.WithRadius & BlockShape.WithHeight> S validateStage(BlockShape blockShape) {
        if (!(blockShape instanceof BlockShape.WithRadius)) throw new IllegalArgumentException("Stage with radius is required");
        if (!(blockShape instanceof BlockShape.WithHeight)) throw new IllegalArgumentException("Stage with height is required");
        return (S) blockShape;
    }

    @NotNull
    public Challenge nextChallenge() {
        if (queue.isEmpty()) {
            if (challenges.isEmpty()) {
                throw new IllegalStateException("No challenges registered");
            }

            queue.addAll(challenges);
            Collections.shuffle(queue, random);
        }

        return queue.removeFirst();
    }
}
