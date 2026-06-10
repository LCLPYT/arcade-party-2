package work.lclpnet.ap2.game.guess_it.challenge;

import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.ap2.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.OptionMaker;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.game.util.WorldModifier;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;

import java.util.Objects;
import java.util.Random;

public class ArmorTrimChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(14);
    private final MiniGameHandle gameHandle;
    private final ServerLevel world;
    private final Random random;
    private final BlockShape blockShape;
    private final WorldModifier modifier;
    private Holder<TrimPattern> correct = null;
    private Holder<TrimMaterial> material = null;
    private ArmorMaterial armorMaterial = null;
    private int correctOption = -1;

    public ArmorTrimChallenge(MiniGameHandle gameHandle, ServerLevel world, Random random, BlockShape blockShape, WorldModifier modifier) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.blockShape = blockShape;
        this.modifier = modifier;
    }

    @Override
    public String id() {
        return "armor_trim";
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

        material = ItemHelper.getRandomTrimMaterial(world.registryAccess(), random);

        armorMaterial = switch (random.nextInt(6)) {
            case 0 -> ArmorMaterials.LEATHER;
            case 1 -> ArmorMaterials.CHAINMAIL;
            case 2 -> ArmorMaterials.IRON;
            case 3 -> ArmorMaterials.GOLD;
            case 4 -> ArmorMaterials.DIAMOND;
            case 5 -> ArmorMaterials.NETHERITE;
            default -> throw new IllegalStateException();
        };

        spawnGiants();

        input.expectSelection(opts.stream()
                .map(TextUtil::getVanillaName)
                .toArray(Component[]::new));
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(TextUtil.getVanillaName(correct));
        result.grantIfCorrect(gameHandle.getParticipants(), correctOption, choices::getOption);
    }

    private void spawnGiants() {
        Vec3 pos = Vec3.atBottomCenterOf(blockShape.origin());

        int spacing = 7;
        spawnGiant(pos.add(spacing, 0, 0), -90);
        spawnGiant(pos.add(-spacing, 0, 0), 90);
        spawnGiant(pos.add(0, 0, spacing), 0);
        spawnGiant(pos.add(0, 0, -spacing), 180);
    }

    private void spawnGiant(Vec3 pos, float yaw) {
        Giant giant = new Giant(EntityType.GIANT, world);
        giant.setPersistenceRequired();
        giant.setNoAi(true);
        giant.setYHeadRot(yaw);
        giant.setPosRaw(pos.x(), pos.y(), pos.z());

        ItemStack helmet = new ItemStack(Objects.requireNonNull(ItemHelper.getHelmet(armorMaterial)));
        helmet.set(DataComponents.TRIM, new ArmorTrim(material, correct));
        helmet.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true));
        giant.setItemSlot(EquipmentSlot.HEAD, helmet);

        ItemStack chestPlate = new ItemStack(Objects.requireNonNull(ItemHelper.getChestPlate(armorMaterial)));
        chestPlate.set(DataComponents.TRIM, new ArmorTrim(material, correct));
        chestPlate.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true));
        giant.setItemSlot(EquipmentSlot.CHEST, chestPlate);

        ItemStack leggings = new ItemStack(Objects.requireNonNull(ItemHelper.getLeggings(armorMaterial)));
        leggings.set(DataComponents.TRIM, new ArmorTrim(material, correct));
        leggings.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true));
        giant.setItemSlot(EquipmentSlot.LEGS, leggings);

        ItemStack boots = new ItemStack(Objects.requireNonNull(ItemHelper.getBoots(armorMaterial)));
        boots.set(DataComponents.TRIM, new ArmorTrim(material, correct));
        boots.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true));
        giant.setItemSlot(EquipmentSlot.FEET, boots);

        modifier.spawnEntity(giant);
    }

    private IdMap<Holder<TrimPattern>> getTrimPatterns() {
        return world.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).asHolderIdMap();
    }
}
