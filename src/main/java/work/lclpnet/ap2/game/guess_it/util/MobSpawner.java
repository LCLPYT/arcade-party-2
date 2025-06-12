package work.lclpnet.ap2.game.guess_it.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Unit;
import net.minecraft.util.collection.IndexedIterable;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerDataContainer;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.core.mixin.ShulkerEntityAccessor;
import work.lclpnet.ap2.core.type.ApVariantHolder;
import work.lclpnet.ap2.impl.util.world.SizedSpaceFinder;
import work.lclpnet.kibu.access.entity.GoatEntityAccess;
import work.lclpnet.kibu.access.entity.HorseEntityAccess;
import work.lclpnet.kibu.access.entity.LlamaEntityAccess;
import work.lclpnet.kibu.access.entity.TropicalFishEntityAccess;
import work.lclpnet.kibu.behaviour.entity.VexEntityBehaviour;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.Optional;
import java.util.Random;
import java.util.Set;

public class MobSpawner {

    private final ServerWorld world;
    private final Random random;

    public MobSpawner(ServerWorld world, Random random) {
        this.world = world;
        this.random = random;
    }

    public void spawnEntity(EntityType<?> type, Vec3d pos, WorldModifier modifier) {
        Entity entity = createEntity(type, pos);

        if (entity != null) {
            modifier.spawnEntity(entity);
        }
    }

    @Nullable
    public <T extends Entity> T createEntity(EntityType<T> type, Vec3d pos) {
        T entity = type.create(world, SpawnReason.COMMAND);

        if (entity == null) return null;

        entity.setPosition(pos);

        randomizeEntity(entity);

        return entity;
    }

    @SuppressWarnings("unchecked")
    public void randomizeEntity(Entity entity) {
        if (random.nextFloat() < 0.005) {
            entity.setCustomName(Text.literal("Dinnerbone"));
        }

        if (entity instanceof MobEntity mob) {
            mob.setPersistent();

            if (random.nextFloat() < 0.045) {
                mob.setBaby(true);
            }
        }

        if (entity instanceof AbstractHorseEntity horse) {
            if (random.nextFloat() < 0.05f) {
                horse.equipStack(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
            }
        }

        if (entity instanceof AxolotlEntity axolotl) {
            randomizeVariant((ApVariantHolder<AxolotlEntity.Variant>) axolotl, AxolotlEntity.Variant.values());
        } else if (entity instanceof RabbitEntity rabbit) {
            if (random.nextFloat() < 0.125f) {  // 1 / 8 chance
                rabbit.setCustomName(Text.literal("Toast"));
            } else {
                randomizeVariant((ApVariantHolder<RabbitEntity.Variant>) rabbit, RabbitEntity.Variant.values());
            }
        } else if (entity instanceof CatEntity cat) {
            var catTypes = world.getRegistryManager().getOrThrow(RegistryKeys.CAT_VARIANT);
            randomizeVariant((ApVariantHolder<RegistryEntry<CatVariant>>) cat, catTypes);
        } else if (entity instanceof SheepEntity sheep) {
            if (random.nextFloat() < 0.01f) {
                sheep.setSheared(true);
            }

            if (random.nextFloat() < 0.01f) {
                sheep.setCustomName(Text.literal("jeb_"));
            } else {
                sheep.setColor(randomElement(DyeColor.values()));
            }
        } else if (entity instanceof DonkeyEntity donkey) {
            if (random.nextFloat() < 0.04f) {
                donkey.setHasChest(true);
            }
        } else if (entity instanceof FoxEntity fox) {
            randomizeVariant((ApVariantHolder<FoxEntity.Variant>) fox, FoxEntity.Variant.values());
        } else if (entity instanceof FrogEntity frog) {
            var frogTypes = world.getRegistryManager().getOrThrow(RegistryKeys.FROG_VARIANT);
            randomizeVariant((ApVariantHolder<RegistryEntry<FrogVariant>>) frog, frogTypes);
        } else if (entity instanceof GoatEntity goat) {
            if (random.nextFloat() < 0.05f) {
                goat.setScreaming(true);
            }

            if (random.nextFloat() < 0.1f) {
                GoatEntityAccess.setLeftHorn(goat, false);
            }

            if (random.nextFloat() < 0.1f) {
                GoatEntityAccess.setRightHorn(goat, false);
            }
        } else if (entity instanceof HorseEntity horse) {
            HorseColor color = randomElement(HorseColor.values());
            HorseMarking marking = randomElement(HorseMarking.values());

            HorseEntityAccess.setVariant(horse, color, marking);
        } else if (entity instanceof LlamaEntity llama) {
            randomizeVariant((ApVariantHolder<LlamaEntity.Variant>) llama, LlamaEntity.Variant.values());

            if (!(entity instanceof TraderLlamaEntity) && random.nextFloat() < 0.6) {
                DyeColor color = randomElement(DyeColor.values());

                LlamaEntityAccess.setCarpetColor(llama, color);
            }
        } else if (entity instanceof SlimeEntity slime) {
            slime.setSize(random.nextInt(5), false);
        } else if (entity instanceof MooshroomEntity mooshroom) {
            randomizeVariant((ApVariantHolder<MooshroomEntity.Variant>) mooshroom, MooshroomEntity.Variant.values());
        } else if (entity instanceof MuleEntity mule) {
            if (random.nextFloat() < 0.04f) {
                mule.setHasChest(true);
            }
        } else if (entity instanceof PandaEntity panda) {
            PandaEntity.Gene gene = randomElement(PandaEntity.Gene.values());
            panda.setMainGene(gene);
            panda.setHiddenGene(gene);
        } else if (entity instanceof ParrotEntity parrot) {
            randomizeVariant((ApVariantHolder<ParrotEntity.Variant>) parrot, ParrotEntity.Variant.values());
        } else if (entity instanceof PhantomEntity phantom) {
            if (random.nextFloat() < 0.35f) {
                phantom.setPhantomSize(random.nextInt(4));
            }
        } else if (entity instanceof ShulkerEntity shulker) {
            if (random.nextFloat() < 0.9411765f) {  // 1 / 17 chance to be default color
                ((ShulkerEntityAccessor) shulker).invokeSetColor(Optional.of(randomElement(DyeColor.values())));
            }
        } else if (entity instanceof VillagerDataContainer villager) {
            var types = Registries.VILLAGER_TYPE.getIndexedEntries();
            var professions = Registries.VILLAGER_PROFESSION.getIndexedEntries();

            villager.setVillagerData(new VillagerData(randomElement(types), randomElement(professions), 2));
        } else if (entity instanceof SnowGolemEntity snowGolem) {
            if (random.nextFloat() < 0.5) {
                snowGolem.setHasPumpkin(false);
            }
        } else if (entity instanceof TropicalFishEntity tropicalFish) {
            var variety = randomElement(TropicalFishEntity.Pattern.values());
            DyeColor baseColor = randomElement(DyeColor.values());
            DyeColor patternColor = randomElement(DyeColor.values());

            TropicalFishEntityAccess.setVariant(tropicalFish, variety, baseColor, patternColor);
        } else if (entity instanceof AbstractPiglinEntity piglin) {
            piglin.setImmuneToZombification(true);
        } else if (entity instanceof VexEntity vex) {
            VexEntityBehaviour.setForceClipping(vex, true);
        } else if (entity instanceof WardenEntity warden) {
            // prevent warden from digging into the ground
            var brain = warden.getBrain();
            brain.remember(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, Ticks.minutes(10));
        } else if (entity instanceof WolfEntity wolf) {
            randomizeVariant((ApVariantHolder<RegistryEntry<WolfVariant>>) wolf, world.getRegistryManager().getOrThrow(RegistryKeys.WOLF_VARIANT));
        } else if (entity instanceof BoggedEntity bogged) {
            if (random.nextFloat() < 0.2) {
                bogged.setSheared(true);
            }
        } else if (entity instanceof PigEntity pig) {
            var pigTypes = world.getRegistryManager().getOrThrow(RegistryKeys.PIG_VARIANT);
            randomizeVariant((ApVariantHolder<RegistryEntry<PigVariant>>) pig, pigTypes);
        } else if (entity instanceof CowEntity cow) {
            var cowTypes = world.getRegistryManager().getOrThrow(RegistryKeys.COW_VARIANT);
            randomizeVariant((ApVariantHolder<RegistryEntry<CowVariant>>) cow, cowTypes);
        } else if (entity instanceof ChickenEntity chicken) {
            var chickenTypes = world.getRegistryManager().getOrThrow(RegistryKeys.CHICKEN_VARIANT);
            randomizeVariant((ApVariantHolder<RegistryEntry<ChickenVariant>>) chicken, chickenTypes);
        }
    }

    private <T> void randomizeVariant(ApVariantHolder<RegistryEntry<T>> holder, Registry<T> registry) {
        var variants = registry.getIndexedEntries();

        randomizeVariant(holder, variants);
    }

    private <T> void randomizeVariant(ApVariantHolder<T> holder, IndexedIterable<T> variants) {
        T variant = randomElement(variants);
        holder.ap2$setVariant(variant);
    }

    private <T> void randomizeVariant(ApVariantHolder<T> holder, T[] variants) {
        T variant = randomElement(variants);
        holder.ap2$setVariant(variant);
    }

    private <T> T randomElement(IndexedIterable<T> variants) {
        if (variants.size() <= 0) {
            throw new IllegalStateException("Empty variants");
        }

        return variants.get(random.nextInt(variants.size()));
    }

    private <T> T randomElement(T[] variants) {
        if (variants.length == 0) {
            throw new IllegalStateException("Empty variants");
        }

        return variants[random.nextInt(variants.length)];
    }

    public static SizedSpaceFinder findSpawns(ServerWorld world, Set<EntityType<?>> types) {
        float maxWidth = (float) types.stream().mapToDouble(type -> type.getDimensions().width()).max().orElse(1);
        float maxHeight = (float) types.stream().mapToDouble(type -> type.getDimensions().height()).max().orElse(2);

        return new SizedSpaceFinder(world, maxWidth, maxHeight, maxWidth);
    }
}
