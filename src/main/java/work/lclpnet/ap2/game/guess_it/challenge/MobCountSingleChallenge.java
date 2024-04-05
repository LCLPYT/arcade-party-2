package work.lclpnet.ap2.game.guess_it.challenge;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.MobSpawner;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.ap2.impl.util.world.SizedSpaceFinder;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.FormatWrapper;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.*;

import static net.minecraft.util.Formatting.*;

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
    public void begin(InputInterface input) {
        TranslationService translations = gameHandle.getTranslations();

        input.expectInput().validateInt(translations);

        var type = selectRandomEntityType();

        amount = getRandomAmount(type);

        SizedSpaceFinder spaceFinder = SizedSpaceFinder.create(world, type);
        List<Vec3d> spaces = spaceFinder.findSpaces(stage.iterateGroundPositions());

        if (spaces.isEmpty()) {
            throw new IllegalStateException("There are no spaces that support " + Registries.ENTITY_TYPE.getId(type));
        }

        MobSpawner spawner = new MobSpawner(world, random);

        for (int i = 0; i < amount; i++) {
            Vec3d pos = spaces.get(random.nextInt(spaces.size()));
            spawner.spawnEntity(type, pos, modifier);
        }

        var entityName = TextUtil.getVanillaName(type).formatted(YELLOW);

        translations.translateText("game.ap2.guess_it.mob.guess", entityName, YELLOW)
                .formatted(DARK_GREEN, BOLD)
                .sendTo(PlayerLookup.world(world));
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(amount);

        result.grantClosest3(gameHandle.getParticipants().getAsSet(), amount, choices::getInt);
    }

    private EntityType<?> selectRandomEntityType() {
        Set<EntityType<?>> types = Set.of(
                EntityType.ALLAY, EntityType.RABBIT, EntityType.AXOLOTL, EntityType.BAT, EntityType.BEE,
                EntityType.BLAZE, EntityType.CAMEL, EntityType.CAT, EntityType.CHICKEN, EntityType.COW,
                EntityType.CREEPER, EntityType.SHEEP, EntityType.PIG, EntityType.CAVE_SPIDER, EntityType.SPIDER,
                EntityType.DONKEY, EntityType.DROWNED, EntityType.ELDER_GUARDIAN, EntityType.GUARDIAN,
                EntityType.ENDERMAN, EntityType.ENDERMITE, EntityType.EVOKER, EntityType.ILLUSIONER,
                EntityType.FOX, EntityType.FROG, EntityType.GOAT, EntityType.HOGLIN, EntityType.HORSE,
                EntityType.HUSK, EntityType.SKELETON, EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER,
                EntityType.IRON_GOLEM, EntityType.LLAMA, EntityType.SLIME, EntityType.MAGMA_CUBE,
                EntityType.MOOSHROOM, EntityType.MULE, EntityType.OCELOT, EntityType.PANDA, EntityType.PARROT,
                EntityType.PHANTOM, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.PILLAGER, EntityType.POLAR_BEAR,
                EntityType.RAVAGER, EntityType.ZOMBIE_HORSE, EntityType.SKELETON_HORSE, EntityType.WITHER_SKELETON,
                EntityType.ZOMBIFIED_PIGLIN, EntityType.WOLF, EntityType.SHULKER, EntityType.SILVERFISH,
                EntityType.WANDERING_TRADER, EntityType.VILLAGER, EntityType.VINDICATOR, EntityType.VEX, EntityType.TURTLE,
                EntityType.TRADER_LLAMA, EntityType.STRIDER, EntityType.STRAY, EntityType.SNOW_GOLEM, EntityType.SNIFFER,
                EntityType.DOLPHIN, EntityType.COD, EntityType.SALMON, EntityType.SQUID, EntityType.GLOW_SQUID,
                EntityType.PUFFERFISH, EntityType.WITCH, EntityType.WARDEN, EntityType.ZOGLIN, EntityType.WITHER,
                EntityType.TROPICAL_FISH
        );

        return types.stream()
                .skip(random.nextInt(types.size()))
                .findFirst()
                .orElseThrow();
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
