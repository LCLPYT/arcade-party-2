package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.challenge.*;
import work.lclpnet.ap2.game.guess_it.util.GuessItDisplay;
import work.lclpnet.ap2.impl.util.world.stage.Stage;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.*;

public class GuessItManager {

    private final Random random;
    private final Set<Challenge> challenges = new LinkedHashSet<>();
    private final List<Challenge> queue = new ArrayList<>();

    public GuessItManager(MiniGameHandle gameHandle, ServerWorld world, Random random, Stage stage,
                          WorldModifier modifier, SoundSubtitles soundSubtitles) {
        this.random = random;

        var stageRadiusHeight = validateStage(stage);

        GuessItDisplay display = new GuessItDisplay(world, modifier, stage);

        challenges.add(new MathsChallenge(gameHandle, random));
        challenges.add(new DayTimeChallenge(gameHandle, world, random));
        challenges.add(new MobCountSingleChallenge(gameHandle, world, random, stage, modifier));
        challenges.add(new MobCountMultiChallenge(gameHandle, world, random, stage, modifier));
        challenges.add(new DistinctMobCountChallenge(gameHandle, world, random, stage, modifier));
        challenges.add(new SoundChallenge(gameHandle, world, random, soundSubtitles));
        challenges.add(new CakeBitesChallenge(gameHandle, world, random, stage, modifier));
        challenges.add(new PotionTypeChallenge(gameHandle, random, display));
        challenges.add(new FoodAmountChallenge(gameHandle, random, display));
        challenges.add(new ArmorTrimChallenge(gameHandle, world, random, stage, modifier));
        challenges.add(new BlockCountChallenge<>(gameHandle, random, stageRadiusHeight, modifier));
        challenges.add(new RecordChallenge(gameHandle, world, random, display));
        challenges.add(new AreaChallenge(gameHandle, world, random, stage, modifier));
        challenges.add(new MinecartChallenge(gameHandle, world, random, stage, modifier));
    }

    @SuppressWarnings("unchecked")
    private <S extends Stage & Stage.WithRadius & Stage.WithHeight> S validateStage(Stage stage) {
        if (!(stage instanceof Stage.WithRadius)) throw new IllegalArgumentException("Stage with radius is required");
        if (!(stage instanceof Stage.WithHeight)) throw new IllegalArgumentException("Stage with height is required");
        return (S) stage;
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
