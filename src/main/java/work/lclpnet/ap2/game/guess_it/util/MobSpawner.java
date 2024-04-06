package work.lclpnet.ap2.game.guess_it.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.VariantHolder;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Unit;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.world.SizedSpaceFinder;
import work.lclpnet.kibu.access.entity.*;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.*;

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
    public Entity createEntity(EntityType<?> type, Vec3d pos) {
        Entity entity = type.create(world);

        if (entity == null) return null;

        entity.setPosition(pos);

        randomizeEntity(entity);

        return entity;
    }

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
                horse.saddle(null);
            }
        }

        if (entity instanceof AxolotlEntity axolotl) {
            randomizeVariant(axolotl, AxolotlEntity.Variant.values());
        } else if (entity instanceof RabbitEntity rabbit) {
            if (random.nextFloat() < 0.125f) {  // 1 / 8 chance
                rabbit.setCustomName(Text.literal("Toast"));
            } else {
                randomizeVariant(rabbit, RabbitEntity.RabbitType.values());
            }
        } else if (entity instanceof CatEntity cat) {
            randomizeVariant(cat, Registries.CAT_VARIANT);
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
            randomizeVariant(fox, FoxEntity.Type.values());
        } else if (entity instanceof FrogEntity frog) {
            randomizeVariant(frog, Registries.FROG_VARIANT);
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
            randomizeVariant(llama, LlamaEntity.Variant.values());

            if (!(entity instanceof TraderLlamaEntity) && random.nextFloat() < 0.6) {
                DyeColor color = randomElement(DyeColor.values());

                LlamaEntityAccess.setCarpetColor(llama, color);
            }
        } else if (entity instanceof SlimeEntity slime) {
            slime.setSize(random.nextInt(5), false);
        } else if (entity instanceof MooshroomEntity mooshroom) {
            randomizeVariant(mooshroom, MooshroomEntity.Type.values());
        } else if (entity instanceof MuleEntity mule) {
            if (random.nextFloat() < 0.04f) {
                mule.setHasChest(true);
            }
        } else if (entity instanceof PandaEntity panda) {
            PandaEntity.Gene gene = randomElement(PandaEntity.Gene.values());
            panda.setMainGene(gene);
            panda.setHiddenGene(gene);
        } else if (entity instanceof ParrotEntity parrot) {
            randomizeVariant(parrot, ParrotEntity.Variant.values());
        } else if (entity instanceof PhantomEntity phantom) {
            if (random.nextFloat() < 0.35f) {
                phantom.setPhantomSize(random.nextInt(4));
            }
        } else if (entity instanceof ShulkerEntity shulker) {
            if (random.nextFloat() < 0.9411765f) {  // 1 / 17 chance to be default color
                shulker.setVariant(Optional.of(randomElement(DyeColor.values())));
            }
        } else if (entity instanceof VillagerDataContainer villager) {
            var types = getRegistryEntries(Registries.VILLAGER_TYPE);
            var professions = getRegistryEntries(Registries.VILLAGER_PROFESSION);

            villager.setVillagerData(new VillagerData(randomElement(types), randomElement(professions), 2));
        } else if (entity instanceof SnowGolemEntity snowGolem) {
            if (random.nextFloat() < 0.5) {
                snowGolem.setHasPumpkin(false);
            }
        } else if (entity instanceof TropicalFishEntity tropicalFish) {
            var variety = randomElement(TropicalFishEntity.Variety.values());
            DyeColor baseColor = randomElement(DyeColor.values());
            DyeColor patternColor = randomElement(DyeColor.values());

            TropicalFishEntityAccess.setVariant(tropicalFish, variety, baseColor, patternColor);
        } else if (entity instanceof AbstractPiglinEntity piglin) {
            piglin.setImmuneToZombification(true);
        } else if (entity instanceof VexEntity vex) {
            //noinspection UnstableApiUsage
            VexEntityAccess.setForceClipping(vex, true);
        } else if (entity instanceof WardenEntity warden) {
            // prevent warden from digging into the ground
            var brain = warden.getBrain();
//            warden.setPersistent();
            brain.remember(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, Ticks.minutes(10));
        }
    }

    private <T> void randomizeVariant(VariantHolder<T> holder, Registry<T> registry) {
        var variants = getRegistryEntries(registry);

        randomizeVariant(holder, variants);
    }

    private <T> void randomizeVariant(VariantHolder<T> holder, List<T> variants) {
        T variant = randomElement(variants);
        holder.setVariant(variant);
    }

    private <T> void randomizeVariant(VariantHolder<T> holder, T[] variants) {
        T variant = randomElement(variants);
        holder.setVariant(variant);
    }

    private <T> T randomElement(List<T> variants) {
        if (variants.isEmpty()) {
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

    @NotNull
    private static <T> List<T> getRegistryEntries(Registry<T> registry) {
        return registry.getEntrySet().stream()
                .map(Map.Entry::getValue)
                .toList();
    }

    public static SizedSpaceFinder findSpawns(ServerWorld world, Set<EntityType<?>> types) {
        float maxWidth = (float) types.stream().mapToDouble(type -> type.getDimensions().width).max().orElse(1);
        float maxHeight = (float) types.stream().mapToDouble(type -> type.getDimensions().height).max().orElse(2);

        return new SizedSpaceFinder(world, maxWidth, maxHeight, maxWidth);
    }
}
