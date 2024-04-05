package work.lclpnet.ap2.game.guess_it.challenge;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.VariantHolder;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerDataContainer;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.impl.util.world.SizedSpaceFinder;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.*;

public class MobCountSingleChallenge implements Challenge {

    private static int DURATION_TICKS = Ticks.seconds(16);
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
        input.expectInput().validateInt(gameHandle.getTranslations());

        var type = selectEntityType();

        amount = getRandomAmount(type);

        SizedSpaceFinder spaceFinder = SizedSpaceFinder.create(world, type);
        List<Vec3d> spaces = spaceFinder.findSpaces(stage.iterateGroundPositions());

        if (spaces.isEmpty()) {
            throw new IllegalStateException("There are no spaces that support " + Registries.ENTITY_TYPE.getId(type));
        }

        for (int i = 0; i < amount; i++) {
            Vec3d pos = spaces.get(random.nextInt(spaces.size()));
            spawnMob(type, pos);
        }
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.grantClosest3(gameHandle.getParticipants().getAsSet(), amount, choices::getInt);
    }

    private int getRandomAmount(EntityType<?> type) {
        if (type == EntityType.WARDEN || type == EntityType.ELDER_GUARDIAN || type == EntityType.RAVAGER || type == EntityType.WITHER) {
            return 12 + random.nextInt(20);
        }

        if (type == EntityType.CAMEL || type == EntityType.IRON_GOLEM || type == EntityType.SNIFFER) {
            return 22 + random.nextInt(54);
        }

        return 31 + random.nextInt(102);
    }

    private void spawnMob(EntityType<?> type, Vec3d pos) {
        Entity entity = type.create(world);

        if (entity == null) return;

        entity.setPosition(pos);

        modifyEntity(entity);

        modifier.spawnEntity(entity);
    }

    private EntityType<?> selectEntityType() {
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

    private void modifyEntity(Entity entity) {
        if (random.nextFloat() < 0.005) {
            entity.setCustomName(Text.literal("Dinnerbone"));
        }

        if (entity instanceof MobEntity mob) {
            if (random.nextFloat() < 0.045) {
                mob.setBaby(true);
            }
        }

        if (entity instanceof AbstractHorseEntity horse) {
            if (random.nextFloat() < 0.05f) {
                horse.saddle(null);
            }
        }

        // prevent zombies from burning by equipping an item on the head (could also override burnsInDaylight, like husk)
        if (entity instanceof ZombieEntity zombie) {
            zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.BIRCH_BUTTON));
        }

        if (entity instanceof AxolotlEntity axolotl) {
            randomizeVariant(axolotl, AxolotlEntity.Variant.values());
        }
        else if (entity instanceof RabbitEntity rabbit) {
            if (random.nextFloat() < 0.125f) {  // 1 / 8 chance
                rabbit.setCustomName(Text.literal("Toast"));
            } else {
                randomizeVariant(rabbit, RabbitEntity.RabbitType.values());
            }
        }
        else if (entity instanceof CatEntity cat) {
            randomizeVariant(cat, Registries.CAT_VARIANT);
        }
        else if (entity instanceof SheepEntity sheep) {
            if (random.nextFloat() < 0.01f) {
                sheep.setSheared(true);
            }

            if (random.nextFloat() < 0.01f) {
                sheep.setCustomName(Text.literal("jeb_"));
            } else {
                sheep.setColor(randomElement(DyeColor.values()));
            }
        }
        else if (entity instanceof DonkeyEntity donkey) {
            if (random.nextFloat() < 0.04f) {
                donkey.setHasChest(true);
            }
        }
        else if (entity instanceof FoxEntity fox) {
            randomizeVariant(fox, FoxEntity.Type.values());
        }
        else if (entity instanceof FrogEntity frog) {
            randomizeVariant(frog, Registries.FROG_VARIANT);
        }
        else if (entity instanceof GoatEntity goat) {
            if (random.nextFloat() < 0.05f) {
                goat.setScreaming(true);
            }
        }
        else if (entity instanceof HorseEntity horse) {
            randomizeVariant(horse, HorseColor.values());
        }
        else if (entity instanceof LlamaEntity llama) {
            randomizeVariant(llama, LlamaEntity.Variant.values());

            if (!(entity instanceof TraderLlamaEntity) && random.nextFloat() < 0.6) {
                Registries.ITEM.getEntryList(ItemTags.WOOL_CARPETS).flatMap(e -> {
                    int n = e.size();

                    if (n == 0) return Optional.empty();

                    return e.stream().skip(random.nextInt(n)).findFirst();
                }).ifPresent(item -> {
                    NbtCompound nbt = new NbtCompound();
                    llama.writeCustomDataToNbt(nbt);

                    NbtCompound carpetNbt = new ItemStack(item).writeNbt(new NbtCompound());
                    nbt.put("DecorItem", carpetNbt);
                    llama.readCustomDataFromNbt(nbt);
                });
            }
        }
        else if (entity instanceof SlimeEntity slime) {
            slime.setSize(random.nextInt(5), false);
        }
        else if (entity instanceof MooshroomEntity mooshroom) {
            randomizeVariant(mooshroom, MooshroomEntity.Type.values());
        }
        else if (entity instanceof MuleEntity mule) {
            if (random.nextFloat() < 0.04f) {
                mule.setHasChest(true);
            }
        }
        else if (entity instanceof PandaEntity panda) {
            PandaEntity.Gene gene = randomElement(PandaEntity.Gene.values());
            panda.setMainGene(gene);
            panda.setHiddenGene(gene);
        }
        else if (entity instanceof ParrotEntity parrot) {
            randomizeVariant(parrot, ParrotEntity.Variant.values());
        }
        else if (entity instanceof PhantomEntity phantom) {
            if (random.nextFloat() < 0.35f) {
                phantom.setPhantomSize(random.nextInt(4));
            }
        }
        else if (entity instanceof ShulkerEntity shulker) {
            if (random.nextFloat() < 0.9411765f) {  // 1 / 17 chance to be default color
                shulker.setVariant(Optional.of(randomElement(DyeColor.values())));
            }
        }
        else if (entity instanceof VillagerDataContainer villager) {
            var types = getRegistryEntries(Registries.VILLAGER_TYPE);
            var professions = getRegistryEntries(Registries.VILLAGER_PROFESSION);

            villager.setVillagerData(new VillagerData(randomElement(types), randomElement(professions), 2));
        }
        else if (entity instanceof SnowGolemEntity snowGolem) {
            if (random.nextFloat() < 0.5) {
                snowGolem.setHasPumpkin(false);
            }
        }
        else if (entity instanceof TropicalFishEntity tropicalFish) {
            tropicalFish.setVariant(randomElement(TropicalFishEntity.Variety.values()));
        }
        else if (entity instanceof AbstractPiglinEntity piglin) {
            piglin.setImmuneToZombification(true);
        }
        else if (entity instanceof VexEntity vex) {
            vex.noClip = false;
        }
    }

    private <T> void randomizeVariant(VariantHolder<T> holder, Registry<T> registry) {
        var variants = getRegistryEntries(registry);

        randomizeVariant(holder, variants);
    }

    @NotNull
    private static <T> List<T> getRegistryEntries(Registry<T> registry) {
        return registry.getEntrySet().stream()
                .map(Map.Entry::getValue)
                .toList();
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
}
