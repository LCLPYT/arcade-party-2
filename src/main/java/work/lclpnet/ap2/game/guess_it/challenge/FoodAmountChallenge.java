package work.lclpnet.ap2.game.guess_it.challenge;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.GuessItDisplay;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.LocalizedFormat;

import java.util.OptionalInt;
import java.util.Random;
import java.util.stream.Collectors;

public class FoodAmountChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(17);
    private final MiniGameHandle gameHandle;
    private final Random random;
    private final GuessItDisplay display;
    private int amount = 0;

    public FoodAmountChallenge(MiniGameHandle gameHandle, Random random, GuessItDisplay display) {
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
        messenger.task(translations.translateText("game.ap2.guess_it.food_amount"));

        input.expectInput().validateFloat(translations, 1);

        Item food = selectRandomFood();
        ItemStack stack = new ItemStack(food);

        FoodComponent foodComponent = food.getComponents().get(DataComponentTypes.FOOD);

        if (foodComponent == null) {
            throw new IllegalStateException("Item has no food component");
        }

        amount = foodComponent.nutrition();

        display.displayItem(stack);
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(LocalizedFormat.format("%.1f", amount * 0.5));
        result.grantClosest3(gameHandle.getParticipants().getAsSet(), amount, player -> choices.getFloat(player)
                .map(f -> Math.round(f * 2))
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty));
    }

    private Item selectRandomFood() {
        var food = Registries.ITEM.stream()
                .filter(item -> item.getComponents().contains(DataComponentTypes.FOOD))
                .collect(Collectors.toSet());

        return food.stream().skip(random.nextInt(food.size())).findFirst().orElseThrow();
    }
}
