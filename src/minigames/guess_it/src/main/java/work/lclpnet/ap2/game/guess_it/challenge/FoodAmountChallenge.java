package work.lclpnet.ap2.game.guess_it.challenge;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import work.lclpnet.ap2.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.GuessItDisplay;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;
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
    public String id() {
        return "food_amount";
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
        messenger.task(translations.translateText("food_amount"));

        input.expectInput().validateFloat(translations, 1);

        Item food = selectRandomFood();
        FoodProperties foodComponent = food.components().get(DataComponents.FOOD);

        if (foodComponent == null) {
            throw new IllegalStateException("Item has no food component");
        }

        amount = foodComponent.nutrition();

        ItemStack stack = new ItemStack(food);
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
        var food = BuiltInRegistries.ITEM.stream()
                .filter(item -> item.components().has(DataComponents.FOOD))
                .collect(Collectors.toSet());

        return food.stream().skip(random.nextInt(food.size())).findFirst().orElseThrow();
    }
}
