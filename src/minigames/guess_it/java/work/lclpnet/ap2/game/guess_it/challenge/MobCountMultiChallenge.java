package work.lclpnet.ap2.game.guess_it.challenge;

import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.MobRandomizer;
import work.lclpnet.ap2.game.guess_it.util.MobSpawner;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.gaco.ds.IndexedSet;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static net.minecraft.util.Formatting.YELLOW;
import static work.lclpnet.ap2.impl.util.world.PositionUtil.findGroundPositions;

public class MobCountMultiChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(16);
    static final int MIN_BUDGET = 43, RANDOM_BUDGET = 97;
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Random random;
    private final BlockShape blockShape;
    private final WorldModifier modifier;
    private final IndexedSet<UUID> mannequinUuids;
    private int amount = 0;

    public MobCountMultiChallenge(MiniGameHandle gameHandle, ServerWorld world, Random random, BlockShape blockShape, WorldModifier modifier, IndexedSet<UUID> mannequinUuids) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.blockShape = blockShape;
        this.modifier = modifier;
        this.mannequinUuids = mannequinUuids;
    }

    @Override
    public String id() {
        return "mob_count_multi";
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

        input.expectInput().validateInt(translations);

        int budget = MIN_BUDGET + random.nextInt(RANDOM_BUDGET);
        amount = random.nextInt((int) Math.floor(budget * 0.2));
        budget -= amount;

        var types = MobRandomizer.getDefaultTypes();
        int typeCount = types.size();

        if (typeCount < 2) {
            throw new IllegalStateException("There must be at least two entity types");
        }

        List<Vec3d> spaces = MobSpawner.findSpawns(world, types).findSpaces(findGroundPositions(blockShape, world));

        if (spaces.isEmpty()) {
            throw new IllegalStateException("No spawn spaces found");
        }

        var searched = types.stream().skip(random.nextInt(typeCount)).findFirst().orElseThrow();
        types.remove(searched);

        types = MobRandomizer.trimTypes(types, random, 10);

        MobRandomizer randomizer = new MobRandomizer(types);
        MobSpawner spawner = new MobSpawner(world, random, mannequinUuids);

        for (int i = 0; i < amount; i++) {
            Vec3d spawn = spaces.get(random.nextInt(spaces.size()));
            spawner.spawnEntity(searched, spawn, modifier);
        }

        while (budget > 0) {
            var type = randomizer.selectRandomEntityType(random);
            int cost = getCost(type);

            budget -= cost;

            Vec3d spawn = spaces.get(random.nextInt(spaces.size()));
            spawner.spawnEntity(type, spawn, modifier);
        }

        var entityName = TextUtil.getVanillaName(searched).formatted(YELLOW);

        messenger.task(translations.translateText("game.ap2.guess_it.mob.guess", entityName, YELLOW));
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(amount);

        result.grantClosest3(gameHandle.getParticipants().getAsSet(), amount, choices::getInt);
    }

    static int getCost(EntityType<?> type) {
        if (type == EntityType.GIANT) {
            return 10;
        }

        if (type == EntityType.WARDEN || type == EntityType.ELDER_GUARDIAN || type == EntityType.RAVAGER
                || type == EntityType.WITHER || type == EntityType.GHAST || type == EntityType.HAPPY_GHAST) {
            return 5;
        }

        if (type == EntityType.CAMEL || type == EntityType.IRON_GOLEM || type == EntityType.SNIFFER) {
            return 3;
        }

        if (type == EntityType.GUARDIAN || type == EntityType.HOGLIN || type == EntityType.ZOGLIN) {
            return 2;
        }

        return 1;
    }
}
