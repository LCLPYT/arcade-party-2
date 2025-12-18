package work.lclpnet.ap2.game.guess_it.util;

import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.equine.*;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.FrogVariant;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.pig.PigVariant;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.skeleton.Bogged;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerDataHolder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.core.mixin.ShulkerAccessor;
import work.lclpnet.ap2.core.type.ApVariantHolder;
import work.lclpnet.ap2.impl.util.world.SizedSpaceFinder;
import work.lclpnet.gaco.ds.IndexedSet;
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
import java.util.UUID;

public class MobSpawner {

    private final ServerLevel world;
    private final Random random;
    private final IndexedSet<UUID> mannequinUuids;

    public MobSpawner(ServerLevel world, Random random, IndexedSet<UUID> mannequinUuids) {
        this.world = world;
        this.random = random;
        this.mannequinUuids = mannequinUuids;
    }

    public void spawnEntity(EntityType<?> type, Vec3 pos, WorldModifier modifier) {
        Entity entity = createEntity(type, pos);

        if (entity != null) {
            modifier.spawnEntity(entity);
        }
    }

    @Nullable
    public <T extends Entity> T createEntity(EntityType<T> type, Vec3 pos) {
        T entity = type.create(world, EntitySpawnReason.COMMAND);

        if (entity == null) return null;

        entity.setPos(pos);

        randomizeEntity(entity);

        return entity;
    }

    @SuppressWarnings("unchecked")
    public void randomizeEntity(Entity entity) {
        entity.absSnapRotationTo(random.nextFloat() * 360, random.nextFloat() * 180 - 90);

        if (random.nextFloat() < 0.005) {
            entity.setCustomName(Component.literal("Dinnerbone"));
        }

        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();

            if (random.nextFloat() < 0.045) {
                mob.setBaby(true);
            }
        }

        if (entity instanceof AbstractHorse horse) {
            if (random.nextFloat() < 0.05f) {
                horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
            }
        }

        if (entity instanceof Axolotl axolotl) {
            randomizeVariant((ApVariantHolder<Axolotl.Variant>) axolotl, Axolotl.Variant.values());
        } else if (entity instanceof Rabbit rabbit) {
            if (random.nextFloat() < 0.125f) {  // 1 / 8 chance
                rabbit.setCustomName(Component.literal("Toast"));
            } else {
                randomizeVariant((ApVariantHolder<Rabbit.Variant>) rabbit, Rabbit.Variant.values());
            }
        } else if (entity instanceof Cat cat) {
            var catTypes = world.registryAccess().lookupOrThrow(Registries.CAT_VARIANT);
            randomizeVariant((ApVariantHolder<Holder<CatVariant>>) cat, catTypes);
        } else if (entity instanceof Sheep sheep) {
            if (random.nextFloat() < 0.01f) {
                sheep.setSheared(true);
            }

            if (random.nextFloat() < 0.01f) {
                sheep.setCustomName(Component.literal("jeb_"));
            } else {
                sheep.setColor(randomElement(DyeColor.values()));
            }
        } else if (entity instanceof Donkey donkey) {
            if (random.nextFloat() < 0.04f) {
                donkey.setChest(true);
            }
        } else if (entity instanceof Fox fox) {
            randomizeVariant((ApVariantHolder<Fox.Variant>) fox, Fox.Variant.values());
        } else if (entity instanceof Frog frog) {
            var frogTypes = world.registryAccess().lookupOrThrow(Registries.FROG_VARIANT);
            randomizeVariant((ApVariantHolder<Holder<FrogVariant>>) frog, frogTypes);
        } else if (entity instanceof Goat goat) {
            if (random.nextFloat() < 0.05f) {
                goat.setScreamingGoat(true);
            }

            if (random.nextFloat() < 0.1f) {
                GoatEntityAccess.setLeftHorn(goat, false);
            }

            if (random.nextFloat() < 0.1f) {
                GoatEntityAccess.setRightHorn(goat, false);
            }
        } else if (entity instanceof Horse horse) {
            net.minecraft.world.entity.animal.equine.Variant color = randomElement(net.minecraft.world.entity.animal.equine.Variant.values());
            Markings marking = randomElement(Markings.values());

            HorseEntityAccess.setVariant(horse, color, marking);
        } else if (entity instanceof Llama llama) {
            randomizeVariant((ApVariantHolder<Llama.Variant>) llama, Llama.Variant.values());

            if (!(entity instanceof TraderLlama) && random.nextFloat() < 0.6) {
                DyeColor color = randomElement(DyeColor.values());

                LlamaEntityAccess.setCarpetColor(llama, color);
            }
        } else if (entity instanceof Slime slime) {
            slime.setSize(random.nextInt(5), false);
        } else if (entity instanceof MushroomCow mooshroom) {
            randomizeVariant((ApVariantHolder<MushroomCow.Variant>) mooshroom, MushroomCow.Variant.values());
        } else if (entity instanceof Mule mule) {
            if (random.nextFloat() < 0.04f) {
                mule.setChest(true);
            }
        } else if (entity instanceof Panda panda) {
            Panda.Gene gene = randomElement(Panda.Gene.values());
            panda.setMainGene(gene);
            panda.setHiddenGene(gene);
        } else if (entity instanceof Parrot parrot) {
            randomizeVariant((ApVariantHolder<Parrot.Variant>) parrot, Parrot.Variant.values());
        } else if (entity instanceof Phantom phantom) {
            if (random.nextFloat() < 0.35f) {
                phantom.setPhantomSize(random.nextInt(4));
            }
        } else if (entity instanceof Shulker shulker) {
            if (random.nextFloat() < 0.9411765f) {  // 1 / 17 chance to be default color
                ((ShulkerAccessor) shulker).invokeSetVariant(Optional.of(randomElement(DyeColor.values())));
            }
        } else if (entity instanceof VillagerDataHolder villager) {
            var types = BuiltInRegistries.VILLAGER_TYPE.asHolderIdMap();
            var professions = BuiltInRegistries.VILLAGER_PROFESSION.asHolderIdMap();

            villager.setVillagerData(new VillagerData(randomElement(types), randomElement(professions), 2));
        } else if (entity instanceof SnowGolem snowGolem) {
            if (random.nextFloat() < 0.5) {
                snowGolem.setPumpkin(false);
            }
        } else if (entity instanceof TropicalFish tropicalFish) {
            var variety = randomElement(TropicalFish.Pattern.values());
            DyeColor baseColor = randomElement(DyeColor.values());
            DyeColor patternColor = randomElement(DyeColor.values());

            TropicalFishEntityAccess.setVariant(tropicalFish, variety, baseColor, patternColor);
        } else if (entity instanceof AbstractPiglin piglin) {
            piglin.setImmuneToZombification(true);
        } else if (entity instanceof Vex vex) {
            VexEntityBehaviour.setForceClipping(vex, true);
        } else if (entity instanceof Warden warden) {
            // prevent warden from digging into the ground
            var brain = warden.getBrain();
            brain.setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, Ticks.minutes(10));
        } else if (entity instanceof Wolf wolf) {
            randomizeVariant((ApVariantHolder<Holder<WolfVariant>>) wolf, world.registryAccess().lookupOrThrow(Registries.WOLF_VARIANT));
        } else if (entity instanceof Bogged bogged) {
            if (random.nextFloat() < 0.2) {
                bogged.setSheared(true);
            }
        } else if (entity instanceof Pig pig) {
            var pigTypes = world.registryAccess().lookupOrThrow(Registries.PIG_VARIANT);
            randomizeVariant((ApVariantHolder<Holder<PigVariant>>) pig, pigTypes);
        } else if (entity instanceof Cow cow) {
            var cowTypes = world.registryAccess().lookupOrThrow(Registries.COW_VARIANT);
            randomizeVariant((ApVariantHolder<Holder<CowVariant>>) cow, cowTypes);
        } else if (entity instanceof Chicken chicken) {
            var chickenTypes = world.registryAccess().lookupOrThrow(Registries.CHICKEN_VARIANT);
            randomizeVariant((ApVariantHolder<Holder<ChickenVariant>>) chicken, chickenTypes);
        } else if (entity instanceof HappyGhast happyGhast) {
            happyGhast.setBaby(random.nextFloat() < 0.6);
        } else if (entity instanceof CopperGolem copperGolem) {
            copperGolem.setWeatherState(randomElement(WeatheringCopper.WeatherState.values()));
        } else if (entity instanceof Mannequin mannequin) {
            if (!mannequinUuids.isEmpty()) {
                UUID uuid = mannequinUuids.get(random.nextInt(mannequinUuids.size()));

                mannequin.setComponent(DataComponents.PROFILE, ResolvableProfile.createUnresolved(uuid));
            }
        }
    }

    private <T> void randomizeVariant(ApVariantHolder<Holder<T>> holder, Registry<T> registry) {
        var variants = registry.asHolderIdMap();

        randomizeVariant(holder, variants);
    }

    private <T> void randomizeVariant(ApVariantHolder<T> holder, IdMap<T> variants) {
        T variant = randomElement(variants);
        holder.ap2$setVariant(variant);
    }

    private <T> void randomizeVariant(ApVariantHolder<T> holder, T[] variants) {
        T variant = randomElement(variants);
        holder.ap2$setVariant(variant);
    }

    private <T> T randomElement(IdMap<T> variants) {
        if (variants.size() <= 0) {
            throw new IllegalStateException("Empty variants");
        }

        return variants.byId(random.nextInt(variants.size()));
    }

    private <T> T randomElement(T[] variants) {
        if (variants.length == 0) {
            throw new IllegalStateException("Empty variants");
        }

        return variants[random.nextInt(variants.length)];
    }

    public static SizedSpaceFinder findSpawns(ServerLevel world, Set<EntityType<?>> types) {
        float maxWidth = (float) types.stream().mapToDouble(type -> type.getDimensions().width()).max().orElse(1);
        float maxHeight = (float) types.stream().mapToDouble(type -> type.getDimensions().height()).max().orElse(2);

        return new SizedSpaceFinder(world, maxWidth, maxHeight, maxWidth);
    }
}
