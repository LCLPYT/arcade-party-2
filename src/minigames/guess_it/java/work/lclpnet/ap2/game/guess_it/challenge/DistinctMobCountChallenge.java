package work.lclpnet.ap2.game.guess_it.challenge;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.MobRandomizer;
import work.lclpnet.ap2.game.guess_it.util.MobSpawner;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.gaco.ds.IndexedSet;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static work.lclpnet.ap2.impl.util.world.PositionUtil.findGroundPositions;

public class DistinctMobCountChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(21);
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Random random;
    private final BlockShape blockShape;
    private final WorldModifier modifier;
    private final IndexedSet<UUID> mannequinUuids;
    private int amount = 0;

    public DistinctMobCountChallenge(MiniGameHandle gameHandle, ServerWorld world, Random random, BlockShape blockShape, WorldModifier modifier, IndexedSet<UUID> mannequinUuids) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.blockShape = blockShape;
        this.modifier = modifier;
        this.mannequinUuids = mannequinUuids;
    }

    @Override
    public String id() {
        return "distinct_mob_count";
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
        messenger.task(translations.translateText("game.ap2.guess_it.mob_types.guess"));

        input.expectInput().validateInt(translations);

        var types = MobRandomizer.getDefaultTypes();
        int minAmount = 4;
        amount = minAmount + random.nextInt(Math.min(9, types.size() - minAmount + 1));

        types = MobRandomizer.trimTypes(types, random, amount);

        List<Vec3d> spaces = MobSpawner.findSpawns(world, types).findSpaces(findGroundPositions(blockShape, world));

        if (spaces.isEmpty()) {
            throw new IllegalStateException("No spawn spaces found");
        }

        MobRandomizer randomizer = new MobRandomizer(types);
        MobSpawner spawner = new MobSpawner(world, random, mannequinUuids);

        int budget = MobCountMultiChallenge.MIN_BUDGET + random.nextInt(MobCountMultiChallenge.RANDOM_BUDGET);

        while (budget > 0) {
            var type = randomizer.selectRandomEntityType(random);
            int cost = MobCountMultiChallenge.getCost(type);

            budget -= cost;

            Vec3d pos = spaces.get(random.nextInt(spaces.size()));
            spawner.spawnEntity(type, pos, modifier);
        }
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(amount);

        result.grantClosest3(gameHandle.getParticipants().getAsSet(), amount, choices::getInt);
    }
}
