package work.lclpnet.ap2.game.guess_it.util;

import net.minecraft.world.entity.EntityType;

import java.util.*;

import static net.minecraft.world.entity.EntityTypes.*;

public class MobRandomizer {

    private final Set<EntityType<?>> types;

    public MobRandomizer() {
        this(getDefaultTypes());
    }

    public MobRandomizer(Set<EntityType<?>> types) {
        if (types.isEmpty()) {
            throw new IllegalArgumentException("Entity types cannot be empty");
        }

        this.types = Collections.unmodifiableSet(types);
    }

    public EntityType<?> selectRandomEntityType(Random random) {
        return types.stream()
                .skip(random.nextInt(types.size()))
                .findFirst()
                .orElseThrow();
    }

    public static Set<EntityType<?>> getDefaultTypes() {
        return new HashSet<>(Set.of(
                ALLAY,
                ARMADILLO,
                AXOLOTL,
                BAT,
                BEE,
                BLAZE,
                BOGGED,
                BREEZE,
                CAMEL,
                CAT,
                CAVE_SPIDER,
                CHICKEN,
                COD,
                COPPER_GOLEM,
                COW,
                CREAKING,
                CREEPER,
                DOLPHIN,
                DONKEY,
                DROWNED,
                ELDER_GUARDIAN,
                ENDERMAN,
                ENDERMITE,
                EVOKER,
                FOX,
                FROG,
                GHAST,
                GIANT,
                GLOW_SQUID,
                GOAT,
                GUARDIAN,
                HAPPY_GHAST,
                HOGLIN,
                HORSE,
                HUSK,
                ILLUSIONER,
                IRON_GOLEM,
                LLAMA,
                MAGMA_CUBE,
                MANNEQUIN,
                MOOSHROOM,
                MULE,
                OCELOT,
                PANDA,
                PARROT,
                PHANTOM,
                PIG,
                PIGLIN,
                PIGLIN_BRUTE,
                PILLAGER,
                POLAR_BEAR,
                PUFFERFISH,
                RABBIT,
                RAVAGER,
                SALMON,
                SHEEP,
                SHULKER,
                SILVERFISH,
                SKELETON,
                SKELETON_HORSE,
                SLIME,
                SNIFFER,
                SNOW_GOLEM,
                SPIDER,
                SQUID,
                STRAY,
                STRIDER,
                SULFUR_CUBE,
                TADPOLE,
                TRADER_LLAMA,
                TROPICAL_FISH,
                TURTLE,
                VEX,
                VILLAGER,
                VINDICATOR,
                WANDERING_TRADER,
                WARDEN,
                WITCH,
                WITHER,
                WITHER_SKELETON,
                WOLF,
                ZOGLIN,
                ZOMBIE,
                ZOMBIE_HORSE,
                ZOMBIE_VILLAGER,
                ZOMBIFIED_PIGLIN
        ));
    }

    public static Set<EntityType<?>> trimTypes(Set<EntityType<?>> types, Random random, int amount) {
        List<EntityType<?>> list = new ArrayList<>(types);

        while (list.size() > amount) {
            list.remove(random.nextInt(list.size()));
        }

        return new HashSet<>(list);
    }
}
