package work.lclpnet.ap2.game.guess_it.util

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import java.util.*

class MobRandomizer @JvmOverloads constructor(types: Set<EntityType<*>> = defaultTypes) {
    private val types: Set<EntityType<*>>

    init {
        require(!types.isEmpty()) { "Entity types cannot be empty" }

        this.types = types.toSet()
    }

    fun selectRandomEntityType(random: Random): EntityType<*> {
        return types.stream()
            .skip(random.nextInt(types.size).toLong())
            .findFirst()
            .orElseThrow()!!
    }

    companion object {
        val defaultTypes: Set<EntityType<*>>
            get() = (
                setOf(
                    EntityTypes.ALLAY,
                    EntityTypes.ARMADILLO,
                    EntityTypes.AXOLOTL,
                    EntityTypes.BAT,
                    EntityTypes.BEE,
                    EntityTypes.BLAZE,
                    EntityTypes.BOGGED,
                    EntityTypes.BREEZE,
                    EntityTypes.CAMEL,
                    EntityTypes.CAT,
                    EntityTypes.CAVE_SPIDER,
                    EntityTypes.CHICKEN,
                    EntityTypes.COD,
                    EntityTypes.COPPER_GOLEM,
                    EntityTypes.COW,
                    EntityTypes.CREAKING,
                    EntityTypes.CREEPER,
                    EntityTypes.DOLPHIN,
                    EntityTypes.DONKEY,
                    EntityTypes.DROWNED,
                    EntityTypes.ELDER_GUARDIAN,
                    EntityTypes.ENDERMAN,
                    EntityTypes.ENDERMITE,
                    EntityTypes.EVOKER,
                    EntityTypes.FOX,
                    EntityTypes.FROG,
                    EntityTypes.GHAST,
                    EntityTypes.GIANT,
                    EntityTypes.GLOW_SQUID,
                    EntityTypes.GOAT,
                    EntityTypes.GUARDIAN,
                    EntityTypes.HAPPY_GHAST,
                    EntityTypes.HOGLIN,
                    EntityTypes.HORSE,
                    EntityTypes.HUSK,
                    EntityTypes.ILLUSIONER,
                    EntityTypes.IRON_GOLEM,
                    EntityTypes.LLAMA,
                    EntityTypes.MAGMA_CUBE,
                    EntityTypes.MANNEQUIN,
                    EntityTypes.MOOSHROOM,
                    EntityTypes.MULE,
                    EntityTypes.OCELOT,
                    EntityTypes.PANDA,
                    EntityTypes.PARROT,
                    EntityTypes.PHANTOM,
                    EntityTypes.PIG,
                    EntityTypes.PIGLIN,
                    EntityTypes.PIGLIN_BRUTE,
                    EntityTypes.PILLAGER,
                    EntityTypes.POLAR_BEAR,
                    EntityTypes.PUFFERFISH,
                    EntityTypes.RABBIT,
                    EntityTypes.RAVAGER,
                    EntityTypes.SALMON,
                    EntityTypes.SHEEP,
                    EntityTypes.SHULKER,
                    EntityTypes.SILVERFISH,
                    EntityTypes.SKELETON,
                    EntityTypes.SKELETON_HORSE,
                    EntityTypes.SLIME,
                    EntityTypes.SNIFFER,
                    EntityTypes.SNOW_GOLEM,
                    EntityTypes.SPIDER,
                    EntityTypes.SQUID,
                    EntityTypes.STRAY,
                    EntityTypes.STRIDER,
                    EntityTypes.SULFUR_CUBE,
                    EntityTypes.TADPOLE,
                    EntityTypes.TRADER_LLAMA,
                    EntityTypes.TROPICAL_FISH,
                    EntityTypes.TURTLE,
                    EntityTypes.VEX,
                    EntityTypes.VILLAGER,
                    EntityTypes.VINDICATOR,
                    EntityTypes.WANDERING_TRADER,
                    EntityTypes.WARDEN,
                    EntityTypes.WITCH,
                    EntityTypes.WITHER,
                    EntityTypes.WITHER_SKELETON,
                    EntityTypes.WOLF,
                    EntityTypes.ZOGLIN,
                    EntityTypes.ZOMBIE,
                    EntityTypes.ZOMBIE_HORSE,
                    EntityTypes.ZOMBIE_VILLAGER,
                    EntityTypes.ZOMBIFIED_PIGLIN
                )
            )

        fun trimTypes(types: MutableSet<EntityType<*>>, random: Random, amount: Int): Set<EntityType<*>> {
            val list = ArrayList(types)

            while (list.size > amount) {
                list.removeAt(random.nextInt(list.size))
            }

            return HashSet(list)
        }
    }
}
