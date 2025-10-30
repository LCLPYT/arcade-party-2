package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.challenge.*;
import work.lclpnet.ap2.game.guess_it.util.GuessItDisplay;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.gaco.ds.IndexedSet;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.*;

public class GuessItManager {

    private final Random random;
    private final Map<String, Challenge> challenges = new HashMap<>();
    private final List<Challenge> queue = new ArrayList<>();
    private final List<ChallengeInit> priority = new ArrayList<>();

    public GuessItManager(MiniGameHandle gameHandle, ServerWorld world, Random random, BlockShape blockShape,
                          WorldModifier modifier, SoundSubtitles soundSubtitles, DebugController debugController,
                          IndexedSet<UUID> mannequinUuids) {

        this.random = random;

        var stageRadiusHeight = validateStage(blockShape);

        GuessItDisplay display = new GuessItDisplay(world, modifier, blockShape);

        registerChallenge(new MathsChallenge(gameHandle, random));
        registerChallenge(new DayTimeChallenge(gameHandle, world, random));
        registerChallenge(new MobCountSingleChallenge(gameHandle, world, random, blockShape, modifier, mannequinUuids));
        registerChallenge(new MobCountMultiChallenge(gameHandle, world, random, blockShape, modifier, mannequinUuids));
        registerChallenge(new DistinctMobCountChallenge(gameHandle, world, random, blockShape, modifier, mannequinUuids));
        registerChallenge(new SoundChallenge(gameHandle, world, random, soundSubtitles));
        registerChallenge(new CakeBitesChallenge(gameHandle, world, random, blockShape, modifier));
        registerChallenge(new PotionTypeChallenge(gameHandle, random, display));
        registerChallenge(new FoodAmountChallenge(gameHandle, random, display));
        registerChallenge(new ArmorTrimChallenge(gameHandle, world, random, blockShape, modifier));
        registerChallenge(new BlockCountChallenge<>(gameHandle, random, stageRadiusHeight, modifier, debugController));
        registerChallenge(new RecordChallenge(gameHandle, world, random, display));
        registerChallenge(new AreaChallenge(gameHandle, world, random, blockShape, modifier));
        registerChallenge(new MinecartChallenge(gameHandle, world, random, blockShape, modifier));
    }

    private void registerChallenge(Challenge challenge) {
        if (challenges.containsKey(challenge.id())) {
            throw new IllegalStateException("Duplicate challenge id " + challenge.id());
        }

        challenges.put(challenge.id(), challenge);
    }

    @SuppressWarnings("unchecked")
    private <S extends BlockShape & BlockShape.WithRadius & BlockShape.WithHeight> S validateStage(BlockShape blockShape) {
        if (!(blockShape instanceof BlockShape.WithRadius)) throw new IllegalArgumentException("Stage with radius is required");
        if (!(blockShape instanceof BlockShape.WithHeight)) throw new IllegalArgumentException("Stage with height is required");
        return (S) blockShape;
    }

    @NotNull
    public ChallengeInit nextChallenge() {
        if (!priority.isEmpty()) {
            return priority.removeFirst();
        }

        if (queue.isEmpty()) {
            if (challenges.isEmpty()) {
                throw new IllegalStateException("No challenges registered");
            }

            queue.addAll(challenges.values());
            Collections.shuffle(queue, random);
        }

        return new ChallengeInit(queue.removeFirst(), null);
    }

    public void pushChallenge(Challenge challenge, @Nullable Object init) {
        priority.add(new ChallengeInit(challenge, init));
    }

    public Collection<Challenge> getChallenges() {
        return new ArrayList<>(challenges.values());
    }

    public record ChallengeInit(Challenge challenge, @Nullable Object init) {}
}
