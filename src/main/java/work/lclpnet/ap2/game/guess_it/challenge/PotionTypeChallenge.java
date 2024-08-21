package work.lclpnet.ap2.game.guess_it.challenge;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.GuessItDisplay;
import work.lclpnet.ap2.game.guess_it.util.OptionMaker;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class PotionTypeChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(16);
    private final MiniGameHandle gameHandle;
    private final Random random;
    private final GuessItDisplay display;
    private ItemStack correct = null;
    private int correctOption = -1;

    public PotionTypeChallenge(MiniGameHandle gameHandle, Random random, GuessItDisplay display) {
        this.gameHandle = gameHandle;
        this.random = random;
        this.display = display;
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
        messenger.task(translations.translateText("game.ap2.guess_it.potion_type"));

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

                    var potionEntry = Registries.POTION.getEntry(potion);
                    stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(potionEntry));

                    return stack;
                })
                .toList();

        correctOption = random.nextInt(options.size());
        correct = options.get(correctOption);

        display.displayItem(correct);

        input.expectSelection(options.stream()
                .map(TextUtil::getVanillaName)
                .toArray(Text[]::new));
    }

    private static Set<Potion> getPotions() {
        var allPotions = Registries.POTION.stream().collect(Collectors.toSet());

        allPotions.remove(Potions.AWKWARD.value());
        allPotions.remove(Potions.MUNDANE.value());
        allPotions.remove(Potions.THICK.value());
        allPotions.remove(Potions.WATER.value());

        // filter multiple length of the same status effect
        Set<Set<RegistryEntry<StatusEffect>>> effects = new HashSet<>();

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

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(TextUtil.getVanillaName(correct));
        result.grantIfCorrect(gameHandle.getParticipants(), correctOption, choices::getOption);
    }
}
