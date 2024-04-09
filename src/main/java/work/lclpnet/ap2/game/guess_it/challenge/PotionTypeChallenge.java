package work.lclpnet.ap2.game.guess_it.challenge;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.GuessItDisplay;
import work.lclpnet.ap2.game.guess_it.util.OptionMaker;
import work.lclpnet.ap2.impl.util.ItemStackHelper;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;

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
        TranslationService translations = gameHandle.getTranslations();
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

                    var potionKey = Registries.POTION.getKey(potion).orElseThrow();
                    ItemStackHelper.setPotion(stack, potionKey);

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

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(TextUtil.getVanillaName(correct));
        result.grantIfCorrect(gameHandle.getParticipants(), correctOption, choices::getOption);
    }
}
