package work.lclpnet.ap2.game.guess_it.util;

import net.minecraft.entity.EntityType;

import java.util.*;

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
                EntityType.ALLAY, EntityType.RABBIT, EntityType.AXOLOTL, EntityType.BAT, EntityType.BEE,
                EntityType.BLAZE, EntityType.CAMEL, EntityType.CAT, EntityType.CHICKEN, EntityType.COW,
                EntityType.CREEPER, EntityType.SHEEP, EntityType.PIG, EntityType.CAVE_SPIDER, EntityType.SPIDER,
                EntityType.DONKEY, EntityType.DROWNED, EntityType.ELDER_GUARDIAN, EntityType.GUARDIAN,
                EntityType.ENDERMAN, EntityType.ENDERMITE, EntityType.EVOKER, EntityType.ILLUSIONER,
                EntityType.FOX, EntityType.FROG, EntityType.GOAT, EntityType.HOGLIN, EntityType.HORSE,
                EntityType.HUSK, EntityType.SKELETON, EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER,
                EntityType.IRON_GOLEM, EntityType.LLAMA, EntityType.SLIME, EntityType.MAGMA_CUBE,
                EntityType.MOOSHROOM, EntityType.MULE, EntityType.OCELOT, EntityType.PANDA, EntityType.PARROT,
                EntityType.PHANTOM, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.PILLAGER,
                EntityType.POLAR_BEAR, EntityType.RAVAGER, EntityType.ZOMBIE_HORSE, EntityType.SKELETON_HORSE,
                EntityType.WITHER_SKELETON, EntityType.ZOMBIFIED_PIGLIN, EntityType.WOLF, EntityType.SHULKER,
                EntityType.SILVERFISH, EntityType.WANDERING_TRADER, EntityType.VILLAGER, EntityType.VINDICATOR,
                EntityType.VEX, EntityType.TURTLE, EntityType.TRADER_LLAMA, EntityType.STRIDER, EntityType.STRAY,
                EntityType.SNOW_GOLEM, EntityType.SNIFFER, EntityType.DOLPHIN, EntityType.COD, EntityType.SALMON,
                EntityType.SQUID, EntityType.GLOW_SQUID, EntityType.PUFFERFISH, EntityType.WITCH, EntityType.WARDEN,
                EntityType.ZOGLIN, EntityType.WITHER, EntityType.TROPICAL_FISH, EntityType.ARMADILLO, EntityType.BREEZE,
                EntityType.BOGGED, EntityType.TADPOLE
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
