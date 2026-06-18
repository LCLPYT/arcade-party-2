package work.lclpnet.ap2.game.guess_it.challenge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.ap2.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.MobRandomizer;
import work.lclpnet.ap2.game.guess_it.util.MobSpawner;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.ap2.impl.util.world.SizedSpaceFinder;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.gaco.ds.IndexedSet;
import work.lclpnet.game.util.WorldModifier;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static net.minecraft.ChatFormatting.YELLOW;
import static work.lclpnet.ap2.impl.util.world.PositionUtil.findGroundPositions;

public class MobCountSingleChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(16);
    private final MiniGameHandle gameHandle;
    private final ServerLevel world;
    private final Random random;
    private final BlockShape blockShape;
    private final WorldModifier modifier;
    private final IndexedSet<UUID> mannequinUuids;
    private int amount = 0;

    public MobCountSingleChallenge(MiniGameHandle gameHandle, ServerLevel world, Random random, BlockShape blockShape, WorldModifier modifier, IndexedSet<UUID> mannequinUuids) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.blockShape = blockShape;
        this.modifier = modifier;
        this.mannequinUuids = mannequinUuids;
    }

    @Override
    public String id() {
        return "mob_count_single";
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

        var randomizer = new MobRandomizer();
        var type = randomizer.selectRandomEntityType(random);

        amount = getRandomAmount(type);

        SizedSpaceFinder spaceFinder = SizedSpaceFinder.create(world, type);
        List<Vec3> spaces = spaceFinder.findSpaces(findGroundPositions(blockShape, world));

        if (spaces.isEmpty()) {
            throw new IllegalStateException("There are no spaces that support " + BuiltInRegistries.ENTITY_TYPE.getKey(type));
        }

        MobSpawner spawner = new MobSpawner(world, random, mannequinUuids);

        for (int i = 0; i < amount; i++) {
            Vec3 pos = spaces.get(random.nextInt(spaces.size()));
            spawner.spawnEntity(type, pos, modifier);
        }

        var entityName = TextUtil.getVanillaName(type).withStyle(YELLOW);

        messenger.task(translations.translateText("mob.guess", entityName, YELLOW));
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
