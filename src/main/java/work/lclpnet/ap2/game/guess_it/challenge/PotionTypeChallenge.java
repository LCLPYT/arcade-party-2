package work.lclpnet.ap2.game.guess_it.challenge;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.OptionMaker;
import work.lclpnet.ap2.impl.util.ItemStackHelper;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static net.minecraft.util.Formatting.BOLD;
import static net.minecraft.util.Formatting.DARK_GREEN;

public class PotionTypeChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(16);
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Random random;
    private final Stage stage;
    private final WorldModifier modifier;
    private ItemStack correct = null;
    private int correctOption = -1;

    public PotionTypeChallenge(MiniGameHandle gameHandle, ServerWorld world, Random random, Stage stage, WorldModifier modifier) {
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

        var potions = getPotions();

        Item item = switch (random.nextInt(3)) {
            case 0 -> Items.POTION;
            case 1 -> Items.SPLASH_POTION;
            case 2 -> Items.LINGERING_POTION;
            default -> throw new IllegalStateException();
        };

        var options = OptionMaker.createOptions(potions, 4, random).stream()
                .map(potion -> {
                    ItemStack stack = new ItemStack(item);

                    var potionKey = Registries.POTION.getKey(potion).orElseThrow();
                    ItemStackHelper.setPotion(stack, potionKey);

                    return stack;
                })
                .toList();

        correctOption = random.nextInt(options.size());
        correct = options.get(correctOption);

        spawnPotion();

        translations.translateText("game.ap2.guess_it.potion_type")
                .formatted(DARK_GREEN, BOLD)
                .sendTo(PlayerLookup.world(world));

        input.expectSelection(options.stream()
                .map(TextUtil::getVanillaName)
                .toArray(Text[]::new));
    }

    private static Set<Potion> getPotions() {
        var allPotions = Registries.POTION.getEntrySet().stream()
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet());

        allPotions.remove(Potions.AWKWARD);
        allPotions.remove(Potions.MUNDANE);
        allPotions.remove(Potions.THICK);
        allPotions.remove(Potions.WATER);
        allPotions.remove(Potions.EMPTY);

        // filter multiple length of the same status effect
        Set<Set<StatusEffect>> effects = new HashSet<>();

        var it = allPotions.iterator();

        while (it.hasNext()) {
            Potion potion = it.next();

            var effectSet = potion.getEffects().stream()
                    .map(StatusEffectInstance::getEffectType)
                    .collect(Collectors.toSet());

            if (!effects.add(effectSet)) {
                it.remove();
            }
        }

        return allPotions;
    }

    private void spawnPotion() {
        var display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);

        DisplayEntityAccess.setItemStack(display, correct);
        DisplayEntityAccess.setBillboardMode(display, DisplayEntity.BillboardMode.CENTER);

        float scale = 8;

        AffineTransformation transformation = new AffineTransformation(new Matrix4f(
                scale, 0, 0, 0,
                0, scale, 0, 0,
                0, 0, scale, 0,
                0, 0, 0, 1
        ));

        DisplayEntityAccess.setTransformation(display, transformation);

        BlockPos origin = stage.getOrigin();

        double x = origin.getX() + 0.5;
        double y = origin.getY() + scale;
        double z = origin.getZ() + 0.5;

        display.setPos(x, y, z);

        modifier.spawnEntity(display);
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(TextUtil.getVanillaName(correct));
        result.grantIfCorrect(gameHandle.getParticipants(), correctOption, choices::getOption);
    }
}
