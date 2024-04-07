package work.lclpnet.ap2.game.guess_it.challenge;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.GiantEntity;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.ItemStack;
import net.minecraft.item.trim.ArmorTrimMaterial;
import net.minecraft.item.trim.ArmorTrimPattern;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.OptionMaker;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.ap2.impl.util.ItemStackHelper;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.Objects;
import java.util.Random;
import java.util.Set;

import static net.minecraft.util.Formatting.BOLD;
import static net.minecraft.util.Formatting.DARK_GREEN;

public class ArmorTrimChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(16);
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Random random;
    private final Stage stage;
    private final WorldModifier modifier;
    private RegistryKey<ArmorTrimPattern> correct = null;
    private RegistryKey<ArmorTrimMaterial> material = null;
    private ArmorMaterials armorMaterial = null;
    private int correctOption = -1;

    public ArmorTrimChallenge(MiniGameHandle gameHandle, ServerWorld world, Random random, Stage stage, WorldModifier modifier) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.stage = stage;
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
    public void begin(InputInterface input) {
        TranslationService translations = gameHandle.getTranslations();

        translations.translateText("game.ap2.guess_it.armor_trim")
                .formatted(DARK_GREEN, BOLD)
                .sendTo(PlayerLookup.world(world));

        var patterns = getTrimPatterns();
        var opts = OptionMaker.createOptions(patterns, 4, random);

        correctOption = random.nextInt(4);

        correct = opts.get(correctOption);

        material = selectRandomTrimMaterial();

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
        Vec3d pos = Vec3d.ofBottomCenter(stage.getOrigin());

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

        ItemStack helmet = new ItemStack(ItemHelper.getHelmet(armorMaterial));
        ItemStackHelper.setArmorTrim(helmet, correct, material);
        giant.equipStack(EquipmentSlot.HEAD, helmet);

        ItemStack chestPlate = new ItemStack(Objects.requireNonNull(ItemHelper.getChestPlate(armorMaterial)));
        ItemStackHelper.setArmorTrim(chestPlate, correct, material);
        giant.equipStack(EquipmentSlot.CHEST, chestPlate);

        ItemStack leggings = new ItemStack(Objects.requireNonNull(ItemHelper.getLeggings(armorMaterial)));
        ItemStackHelper.setArmorTrim(leggings, correct, material);
        giant.equipStack(EquipmentSlot.LEGS, leggings);

        ItemStack boots = new ItemStack(Objects.requireNonNull(ItemHelper.getBoots(armorMaterial)));
        ItemStackHelper.setArmorTrim(boots, correct, material);
        giant.equipStack(EquipmentSlot.FEET, boots);

        modifier.spawnEntity(giant);
    }

    private RegistryKey<ArmorTrimMaterial> selectRandomTrimMaterial() {
        var keys = world.getRegistryManager().get(RegistryKeys.TRIM_MATERIAL).getKeys();

        if (keys.isEmpty()) {
            throw new IllegalStateException("No trim materials found");
        }

        return keys.stream().skip(random.nextInt(keys.size())).findFirst().orElseThrow();
    }

    private Set<RegistryKey<ArmorTrimPattern>> getTrimPatterns() {
        return world.getRegistryManager().get(RegistryKeys.TRIM_PATTERN).getKeys();
    }
}
