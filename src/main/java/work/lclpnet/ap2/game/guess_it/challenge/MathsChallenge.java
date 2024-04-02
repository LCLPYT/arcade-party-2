package work.lclpnet.ap2.game.guess_it.challenge;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.math.Expression;
import work.lclpnet.ap2.game.guess_it.math.Term;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;

import java.util.Random;

import static net.minecraft.util.Formatting.BOLD;
import static net.minecraft.util.Formatting.YELLOW;
import static work.lclpnet.ap2.game.guess_it.math.Term.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class MathsChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(20);
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Random random;
    private int correctAnswer = 0;

    public MathsChallenge(MiniGameHandle gameHandle, ServerWorld world, Random random) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
    }

    @Override
    public String getPreparationKey() {
        return GuessItConstants.PREPARE_CALCULATE;
    }

    @Override
    public int getDurationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void begin(InputInterface input) {
        TranslationService translations = gameHandle.getTranslations();

        input.expectInput()
                .validateInt(translations)
                .onlyOnce();

        Expression term = randomTerm();
        correctAnswer = term.evaluate();

        translations.translateText("game.ap2.guess_it.calculate.exercise", styled(term.stringify(), YELLOW))
                .formatted(Formatting.DARK_GREEN, BOLD)
                .sendTo(PlayerLookup.world(world));
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(correctAnswer);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            var optChoice = choices.getInt(player);

            if (optChoice.isEmpty()) continue;

            int i = optChoice.getAsInt();

            // 3 points, if the answer is correct
            if (i == correctAnswer) {
                result.grant(player, 3);
            }
        }
    }

    private Expression randomTerm() {
        Expression[] expressions = new Expression[] {
                add(range(-100, 100), add(range(1, 50), range(20, 80))),        // a + b + c
                add(range(-500, 500), range(1, 1000)),                          // a + b
                add(range(1, 100), sub(range(50, 200), range(50, 200))),        // a + b - c
                add(range(1, 250), mul(range(2, 10), range(2, 10))),            // a + b * c
                sub(range(-100, 100), range(1, 100)),                           // a - b
                sub(range(-100, 100), mul(range(0, 20), range(0, 5))),          // a - b * c
                mul(range(1, 10), add(range(-10, 20), range(1, 10))),           // a * (b + c)
                mul(range(1, 5), mul(range(2, 10), range(2, 4))),               // a * b * c
                divCommon(5, 20, 2, 10),                 // a / b
                mul(range(2, 5), divCommon(1, 5, 1, 50)) // a * (b / c)
        };

        return expressions[random.nextInt(expressions.length)];
    }

    private Expression range(int min, int max) {
        int n = random(min, max);

        return Term.num(n);
    }

    private Expression divCommon(int bMin, int bMax, int cMin, int cMax) {
        int b = random(bMin, bMax);
        int c = random(cMin, cMax);

        return div(num(b * c), num(b));
    }

    private int random(int min, int max) {
        int _min = Math.min(min, max);
        max = Math.max(min, max);

        return _min + random.nextInt(max - _min + 1);
    }
}
