package work.lclpnet.ap2.game.guess_it.challenge;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.GiantEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.ArmorMaterials;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.item.equipment.trim.ArmorTrimMaterial;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.IndexedIterable;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.OptionMaker;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.Objects;
import java.util.Random;

public class ArmorTrimChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(14);
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Random random;
    private final BlockShape blockShape;
    private final WorldModifier modifier;
    private RegistryEntry<ArmorTrimPattern> correct = null;
    private RegistryEntry<ArmorTrimMaterial> material = null;
    private ArmorMaterial armorMaterial = null;
    private int correctOption = -1;

    public ArmorTrimChallenge(MiniGameHandle gameHandle, ServerWorld world, Random random, BlockShape blockShape, WorldModifier modifier) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.blockShape = blockShape;
        this.modifier = modifier;
    }

    @Override
    public String getPreparationKey() {
        return GuessItConstants.PREPARE_GUESS;
    }

    @Override
    public int getDurationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void begin(InputInterface input, ChallengeMessenger messenger) {
        Translations translations = gameHandle.getTranslations();
        messenger.task(translations.translateText("game.ap2.guess_it.armor_trim"));

        var patterns = getTrimPatterns();
        var opts = OptionMaker.createOptions(patterns, 4, random);

        correctOption = random.nextInt(4);

        correct = opts.get(correctOption);

        material = ItemHelper.getRandomTrimMaterial(world.getRegistryManager(), random);

        armorMaterial = switch (random.nextInt(6)) {
            case 0 -> ArmorMaterials.LEATHER;
            case 1 -> ArmorMaterials.CHAIN;
            case 2 -> ArmorMaterials.IRON;
            case 3 -> ArmorMaterials.GOLD;
            case 4 -> ArmorMaterials.DIAMOND;
            case 5 -> ArmorMaterials.NETHERITE;
            default -> throw new IllegalStateException();
        };

        spawnGiants();

        input.expectSelection(opts.stream()
                .map(TextUtil::getVanillaName)
                .toArray(Text[]::new));
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(TextUtil.getVanillaName(correct));
        result.grantIfCorrect(gameHandle.getParticipants(), correctOption, choices::getOption);
    }

    private void spawnGiants() {
        Vec3d pos = Vec3d.ofBottomCenter(blockShape.origin());

        int spacing = 7;
        spawnGiant(pos.add(spacing, 0, 0), -90);
        spawnGiant(pos.add(-spacing, 0, 0), 90);
        spawnGiant(pos.add(0, 0, spacing), 0);
        spawnGiant(pos.add(0, 0, -spacing), 180);
    }

    private void spawnGiant(Vec3d pos, float yaw) {
        GiantEntity giant = new GiantEntity(EntityType.GIANT, world);
        giant.setPersistent();
        giant.setAiDisabled(true);
        giant.setHeadYaw(yaw);
        giant.setPos(pos.getX(), pos.getY(), pos.getZ());

        ItemStack helmet = new ItemStack(Objects.requireNonNull(ItemHelper.getHelmet(armorMaterial)));
        helmet.set(DataComponentTypes.TRIM, new ArmorTrim(material, correct));
        helmet.set(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.TRIM, true));
        giant.equipStack(EquipmentSlot.HEAD, helmet);

        ItemStack chestPlate = new ItemStack(Objects.requireNonNull(ItemHelper.getChestPlate(armorMaterial)));
        chestPlate.set(DataComponentTypes.TRIM, new ArmorTrim(material, correct));
        chestPlate.set(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.TRIM, true));
        giant.equipStack(EquipmentSlot.CHEST, chestPlate);

        ItemStack leggings = new ItemStack(Objects.requireNonNull(ItemHelper.getLeggings(armorMaterial)));
        leggings.set(DataComponentTypes.TRIM, new ArmorTrim(material, correct));
        leggings.set(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.TRIM, true));
        giant.equipStack(EquipmentSlot.LEGS, leggings);

        ItemStack boots = new ItemStack(Objects.requireNonNull(ItemHelper.getBoots(armorMaterial)));
        boots.set(DataComponentTypes.TRIM, new ArmorTrim(material, correct));
        boots.set(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.TRIM, true));
        giant.equipStack(EquipmentSlot.FEET, boots);

        modifier.spawnEntity(giant);
    }

    private IndexedIterable<RegistryEntry<ArmorTrimPattern>> getTrimPatterns() {
        return world.getRegistryManager().getOrThrow(RegistryKeys.TRIM_PATTERN).getIndexedEntries();
    }
}
