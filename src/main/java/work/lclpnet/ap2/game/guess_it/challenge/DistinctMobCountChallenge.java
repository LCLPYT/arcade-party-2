package work.lclpnet.ap2.game.guess_it.challenge;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.MobRandomizer;
import work.lclpnet.ap2.game.guess_it.util.MobSpawner;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.List;
import java.util.Random;

import static net.minecraft.util.Formatting.BOLD;
import static net.minecraft.util.Formatting.DARK_GREEN;

public class DistinctMobCountChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(21);
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Random random;
    private final Stage stage;
    private final WorldModifier modifier;
    private int amount = 0;

    public DistinctMobCountChallenge(MiniGameHandle gameHandle, ServerWorld world, Random random, Stage stage, WorldModifier modifier) {
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
    public void begin(InputInterface input) {
        TranslationService translations = gameHandle.getTranslations();

        input.expectInput().validateInt(translations);

        var types = MobRandomizer.getDefaultTypes();
        int minAmount = 4;
        amount = minAmount + random.nextInt(Math.min(9, types.size() - minAmount + 1));

        types = MobRandomizer.trimTypes(types, random, amount);

        List<Vec3d> spaces = MobSpawner.findSpawns(world, types).findSpaces(stage.iterateGroundPositions());

        if (spaces.isEmpty()) {
            throw new IllegalStateException("No spawn spaces found");
        }

        MobRandomizer randomizer = new MobRandomizer(types);
        MobSpawner spawner = new MobSpawner(world, random);

        int budget = MobCountMultiChallenge.MIN_BUDGET + random.nextInt(MobCountMultiChallenge.RANDOM_BUDGET);

        while (budget > 0) {
            var type = randomizer.selectRandomEntityType(random);
            int cost = MobCountMultiChallenge.getCost(type);

            budget -= cost;

            Vec3d pos = spaces.get(random.nextInt(spaces.size()));
            spawner.spawnEntity(type, pos, modifier);
        }

        translations.translateText("game.ap2.guess_it.mob_types.guess")
                .formatted(DARK_GREEN, BOLD)
                .sendTo(PlayerLookup.world(world));
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(amount);

        result.grantClosest3(gameHandle.getParticipants().getAsSet(), amount, choices::getInt);
    }
}
