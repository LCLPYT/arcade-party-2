package work.lclpnet.ap2.game.guess_it.challenge;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.MobRandomizer;
import work.lclpnet.ap2.game.guess_it.util.MobSpawner;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.ap2.impl.util.world.SizedSpaceFinder;
import work.lclpnet.ap2.impl.util.world.stage.Stage;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.List;
import java.util.Random;

import static net.minecraft.util.Formatting.YELLOW;

public class MobCountSingleChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(16);
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Random random;
    private final Stage stage;
    private final WorldModifier modifier;
    private int amount = 0;

    public MobCountSingleChallenge(MiniGameHandle gameHandle, ServerWorld world, Random random, Stage stage, WorldModifier modifier) {
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
    public void begin(InputInterface input, ChallengeMessenger messenger) {
        TranslationService translations = gameHandle.getTranslations();

        input.expectInput().validateInt(translations);

        var randomizer = new MobRandomizer();
        var type = randomizer.selectRandomEntityType(random);

        amount = getRandomAmount(type);

        SizedSpaceFinder spaceFinder = SizedSpaceFinder.create(world, type);
        List<Vec3d> spaces = spaceFinder.findSpaces(stage.groundPositionIterator());

        if (spaces.isEmpty()) {
            throw new IllegalStateException("There are no spaces that support " + Registries.ENTITY_TYPE.getId(type));
        }

        MobSpawner spawner = new MobSpawner(world, random);

        for (int i = 0; i < amount; i++) {
            Vec3d pos = spaces.get(random.nextInt(spaces.size()));
            spawner.spawnEntity(type, pos, modifier);
        }

        var entityName = TextUtil.getVanillaName(type).formatted(YELLOW);

        messenger.task(translations.translateText("game.ap2.guess_it.mob.guess", entityName, YELLOW));
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(amount);

        result.grantClosest3(gameHandle.getParticipants().getAsSet(), amount, choices::getInt);
    }

    private int getRandomAmount(EntityType<?> type) {
        if (type == EntityType.WARDEN || type == EntityType.ELDER_GUARDIAN || type == EntityType.RAVAGER || type == EntityType.WITHER) {
            return 12 + random.nextInt(20);
        }

        if (type == EntityType.CAMEL || type == EntityType.IRON_GOLEM || type == EntityType.SNIFFER) {
            return 22 + random.nextInt(54);
        }

        if (type == EntityType.GUARDIAN || type == EntityType.HOGLIN || type == EntityType.ZOGLIN) {
            return 27 + random.nextInt(78);
        }

        return 31 + random.nextInt(102);
    }
}
