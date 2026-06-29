package work.lclpnet.ap2.game.guess_it.util

import net.minecraft.core.Holder
import net.minecraft.core.IdMap
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Unit
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.animal.axolotl.Axolotl
import net.minecraft.world.entity.animal.chicken.Chicken
import net.minecraft.world.entity.animal.chicken.ChickenVariant
import net.minecraft.world.entity.animal.cow.Cow
import net.minecraft.world.entity.animal.cow.CowVariant
import net.minecraft.world.entity.animal.cow.MushroomCow
import net.minecraft.world.entity.animal.equine.*
import net.minecraft.world.entity.animal.feline.Cat
import net.minecraft.world.entity.animal.feline.CatVariant
import net.minecraft.world.entity.animal.fish.TropicalFish
import net.minecraft.world.entity.animal.fox.Fox
import net.minecraft.world.entity.animal.frog.Frog
import net.minecraft.world.entity.animal.frog.FrogVariant
import net.minecraft.world.entity.animal.goat.Goat
import net.minecraft.world.entity.animal.golem.CopperGolem
import net.minecraft.world.entity.animal.golem.SnowGolem
import net.minecraft.world.entity.animal.happyghast.HappyGhast
import net.minecraft.world.entity.animal.panda.Panda
import net.minecraft.world.entity.animal.parrot.Parrot
import net.minecraft.world.entity.animal.pig.Pig
import net.minecraft.world.entity.animal.pig.PigVariant
import net.minecraft.world.entity.animal.rabbit.Rabbit
import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.entity.animal.wolf.Wolf
import net.minecraft.world.entity.animal.wolf.WolfVariant
import net.minecraft.world.entity.decoration.Mannequin
import net.minecraft.world.entity.monster.Phantom
import net.minecraft.world.entity.monster.Shulker
import net.minecraft.world.entity.monster.Vex
import net.minecraft.world.entity.monster.cubemob.Slime
import net.minecraft.world.entity.monster.piglin.AbstractPiglin
import net.minecraft.world.entity.monster.skeleton.Bogged
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.npc.villager.VillagerData
import net.minecraft.world.entity.npc.villager.VillagerDataHolder
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.level.block.WeatheringCopper
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.core.mixin.entity.ShulkerAccessor
import work.lclpnet.ap2.core.type.ApVariantHolder
import work.lclpnet.ap2.util.world.SizedSpaceFinder
import work.lclpnet.gaco.ds.IndexedSet
import work.lclpnet.game.util.WorldModifier
import work.lclpnet.kibu.access.entity.GoatEntityAccess
import work.lclpnet.kibu.access.entity.HorseEntityAccess
import work.lclpnet.kibu.access.entity.LlamaEntityAccess
import work.lclpnet.kibu.access.entity.TropicalFishEntityAccess
import work.lclpnet.kibu.behaviour.entity.VexEntityBehaviour
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*

class MobSpawner(
    private val world: ServerLevel,
    private val random: Random,
    private val mannequinUuids: IndexedSet<UUID>
) {
    fun spawnEntity(type: EntityType<*>, pos: Vec3, modifier: WorldModifier) {
        val entity: Entity? = createEntity(type, pos)

        if (entity != null) {
            modifier.spawnEntity(entity)
        }
    }

    fun <T : Entity> createEntity(type: EntityType<T>, pos: Vec3): T? {
        val entity = type.create(world, EntitySpawnReason.COMMAND) ?: return null

        entity.setPos(pos)

        randomizeEntity(entity)

        return entity
    }

    @Suppress("UNCHECKED_CAST")
    fun randomizeEntity(entity: Entity) {
        entity.absSnapRotationTo(random.nextFloat() * 360, random.nextFloat() * 180 - 90)

        if (random.nextFloat() < 0.005) {
            entity.customName = Component.literal("Dinnerbone")
        }

        if (entity is Mob) {
            entity.setPersistenceRequired()

            if (random.nextFloat() < 0.045) {
                entity.isBaby = true
            }
        }

        if (entity is AbstractHorse) {
            if (random.nextFloat() < 0.05f) {
                entity.setItemSlot(EquipmentSlot.SADDLE, ItemStack(Items.SADDLE))
            }
        }

        if (entity is Axolotl) {
            randomizeVariant(
                entity as ApVariantHolder<Axolotl.Variant>,
                Axolotl.Variant.entries.toTypedArray()
            )
        } else if (entity is Rabbit) {
            if (random.nextFloat() < 0.125f) {  // 1 / 8 chance
                entity.customName = Component.literal("Toast")
            } else {
                randomizeVariant(
                    entity as ApVariantHolder<Rabbit.Variant>,
                    Rabbit.Variant.entries.toTypedArray()
                )
            }
        } else if (entity is Cat) {
            val catTypes = world.registryAccess().lookupOrThrow(Registries.CAT_VARIANT)
            randomizeVariant(entity as ApVariantHolder<Holder<CatVariant>>, catTypes)
        } else if (entity is Sheep) {
            if (random.nextFloat() < 0.01f) {
                entity.isSheared = true
            }

            if (random.nextFloat() < 0.01f) {
                entity.customName = Component.literal("jeb_")
            } else {
                entity.color = randomElement(DyeColor.entries.toTypedArray())
            }
        } else if (entity is Donkey) {
            if (random.nextFloat() < 0.04f) {
                entity.setChest(true)
            }
        } else if (entity is Fox) {
            randomizeVariant(
                entity as ApVariantHolder<Fox.Variant>,
                Fox.Variant.entries.toTypedArray()
            )
        } else if (entity is Frog) {
            val frogTypes = world.registryAccess().lookupOrThrow(Registries.FROG_VARIANT)
            randomizeVariant(entity as ApVariantHolder<Holder<FrogVariant>>, frogTypes)
        } else if (entity is Goat) {
            if (random.nextFloat() < 0.05f) {
                entity.isScreamingGoat = true
            }

            if (random.nextFloat() < 0.1f) {
                GoatEntityAccess.setLeftHorn(entity, false)
            }

            if (random.nextFloat() < 0.1f) {
                GoatEntityAccess.setRightHorn(entity, false)
            }
        } else if (entity is Horse) {
            val color = randomElement(Variant.entries.toTypedArray())
            val marking = randomElement(Markings.entries.toTypedArray())

            HorseEntityAccess.setVariant(entity, color, marking)
        } else if (entity is Llama) {
            randomizeVariant(
                entity as ApVariantHolder<Llama.Variant>,
                Llama.Variant.entries.toTypedArray()
            )

            if (entity !is TraderLlama && random.nextFloat() < 0.6) {
                val color = randomElement(DyeColor.entries.toTypedArray())

                LlamaEntityAccess.setCarpetColor(entity, color)
            }
        } else if (entity is Slime) {
            entity.setSize(random.nextInt(5), false)
        } else if (entity is MushroomCow) {
            randomizeVariant(
                entity as ApVariantHolder<MushroomCow.Variant>,
                MushroomCow.Variant.entries.toTypedArray()
            )
        } else if (entity is Mule) {
            if (random.nextFloat() < 0.04f) {
                entity.setChest(true)
            }
        } else if (entity is Panda) {
            val gene = randomElement(Panda.Gene.entries.toTypedArray())
            entity.mainGene = gene
            entity.hiddenGene = gene
        } else if (entity is Parrot) {
            randomizeVariant(
                entity as ApVariantHolder<Parrot.Variant>,
                Parrot.Variant.entries.toTypedArray()
            )
        } else if (entity is Phantom) {
            if (random.nextFloat() < 0.35f) {
                entity.phantomSize = random.nextInt(4)
            }
        } else if (entity is Shulker) {
            if (random.nextFloat() < 0.9411765f) {  // 1 / 17 chance to be default color
                val color = randomElement(DyeColor.entries.toTypedArray())
                (entity as ShulkerAccessor).invokeSetVariant(Optional.of(color))
            }
        } else if (entity is VillagerDataHolder) {
            val types = BuiltInRegistries.VILLAGER_TYPE.asHolderIdMap()
            val professions = BuiltInRegistries.VILLAGER_PROFESSION.asHolderIdMap()

            val type = randomElement(types)
            val profession = randomElement(professions)

            if (type != null && profession != null) {
                entity.villagerData = VillagerData(type, profession, 2)
            }
        } else if (entity is SnowGolem) {
            if (random.nextFloat() < 0.5) {
                entity.setPumpkin(false)
            }
        } else if (entity is TropicalFish) {
            val variety = randomElement(TropicalFish.Pattern.entries.toTypedArray())
            val baseColor = randomElement(DyeColor.entries.toTypedArray())
            val patternColor = randomElement(DyeColor.entries.toTypedArray())

            TropicalFishEntityAccess.setVariant(entity, variety, baseColor, patternColor)
        } else if (entity is AbstractPiglin) {
            entity.setImmuneToZombification(true)
        } else if (entity is Vex) {
            VexEntityBehaviour.setForceClipping(entity, true)
        } else if (entity is Warden) {
            // prevent warden from digging into the ground
            val brain = entity.getBrain()
            brain.setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, Ticks.minutes(10).toLong())
        } else if (entity is Wolf) {
            randomizeVariant(
                entity as ApVariantHolder<Holder<WolfVariant>>,
                world.registryAccess().lookupOrThrow(Registries.WOLF_VARIANT)
            )
        } else if (entity is Bogged) {
            if (random.nextFloat() < 0.2) {
                entity.isSheared = true
            }
        } else if (entity is Pig) {
            val pigTypes = world.registryAccess().lookupOrThrow(Registries.PIG_VARIANT)
            randomizeVariant(entity as ApVariantHolder<Holder<PigVariant>>, pigTypes)
        } else if (entity is Cow) {
            val cowTypes = world.registryAccess().lookupOrThrow(Registries.COW_VARIANT)
            randomizeVariant(entity as ApVariantHolder<Holder<CowVariant>>, cowTypes)
        } else if (entity is Chicken) {
            val chickenTypes = world.registryAccess().lookupOrThrow(Registries.CHICKEN_VARIANT)
            randomizeVariant(entity as ApVariantHolder<Holder<ChickenVariant>>, chickenTypes)
        } else if (entity is HappyGhast) {
            entity.isBaby = random.nextFloat() < 0.6
        } else if (entity is CopperGolem) {
            entity.weatherState = randomElement(WeatheringCopper.WeatherState.entries.toTypedArray())
        } else if (entity is Mannequin) {
            if (!mannequinUuids.isEmpty()) {
                val uuid = mannequinUuids.get(random.nextInt(mannequinUuids.size))

                entity.setComponent(
                    DataComponents.PROFILE,
                    ResolvableProfile.createUnresolved(uuid)
                )
            }
        }
    }

    private fun <T : Any> randomizeVariant(holder: ApVariantHolder<Holder<T>>, registry: Registry<T>) {
        val variants: IdMap<Holder<T>> = registry.asHolderIdMap()

        randomizeVariant<Holder<T>>(holder, variants)
    }

    private fun <T : Any> randomizeVariant(holder: ApVariantHolder<T>, variants: IdMap<T>) {
        val variant = randomElement(variants)
        holder.`ap2$setVariant`(variant)
    }

    private fun <T> randomizeVariant(holder: ApVariantHolder<T>, variants: Array<T>) {
        val variant = randomElement(variants)
        holder.`ap2$setVariant`(variant)
    }

    private fun <T : Any> randomElement(variants: IdMap<T>): T? {
        check(variants.size() > 0) { "Empty variants" }

        return variants.byId(random.nextInt(variants.size()))
    }

    private fun <T> randomElement(variants: Array<T>): T {
        check(variants.isNotEmpty()) { "Empty variants" }

        return variants[random.nextInt(variants.size)]
    }

    companion object {
        fun findSpawns(world: ServerLevel, types: Set<EntityType<*>>): SizedSpaceFinder {
            val maxWidth = types.maxOf { it.dimensions.width }
            val maxHeight = types.maxOf { it.dimensions.height }

            return SizedSpaceFinder(world, maxWidth, maxHeight, maxWidth)
        }
    }
}
