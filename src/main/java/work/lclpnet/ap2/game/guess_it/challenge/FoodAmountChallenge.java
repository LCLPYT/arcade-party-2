package work.lclpnet.ap2.game.guess_it.challenge;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.GuessItDisplay;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.LocalizedFormat;

import java.util.OptionalInt;
import java.util.Random;
import java.util.stream.Collectors;

import static net.minecraft.util.Formatting.BOLD;
import static net.minecraft.util.Formatting.DARK_GREEN;

public class FoodAmountChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(17);
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Random random;
    private final GuessItDisplay display;
    private int amount = 0;

    public FoodAmountChallenge(MiniGameHandle gameHandle, ServerWorld world, Random random, GuessItDisplay display) {
        this.gameHandle = gameHandle;
        this.world = world;
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
    public void begin(InputInterface input) {
        TranslationService translations = gameHandle.getTranslations();

        input.expectInput().validateFloat(translations, 1);

        Item food = selectRandomFood();
        ItemStack stack = new ItemStack(food);

        FoodComponent foodComponent = food.getFoodComponent(stack);

        if (foodComponent == null) {
            throw new IllegalStateException("Item has no food component");
        }

        amount = foodComponent.getHunger();

        display.displayItem(stack);

        translations.translateText("game.ap2.guess_it.food_amount")
                .formatted(DARK_GREEN, BOLD)
                .sendTo(PlayerLookup.world(world));
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
                .filter(Item::isFood)
                .collect(Collectors.toSet());

        return food.stream().skip(random.nextInt(food.size())).findFirst().orElseThrow();
    }
}
