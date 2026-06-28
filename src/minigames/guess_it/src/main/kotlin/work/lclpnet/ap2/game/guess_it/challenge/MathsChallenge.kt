package work.lclpnet.ap2.game.guess_it.challenge

import net.minecraft.ChatFormatting.YELLOW
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.guess_it.data.*
import work.lclpnet.ap2.game.guess_it.math.Expression
import work.lclpnet.ap2.game.guess_it.math.Term.add
import work.lclpnet.ap2.game.guess_it.math.Term.div
import work.lclpnet.ap2.game.guess_it.math.Term.mul
import work.lclpnet.ap2.game.guess_it.math.Term.num
import work.lclpnet.ap2.game.guess_it.math.Term.sub
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import java.util.*

class MathsChallenge(
    private val gameHandle: MiniGameHandle,
    private val random: Random
) : Challenge {

    private var correctAnswer = 0

    override fun id() = "maths"

    override val preparationKey = PREPARE_CALCULATE

    override val durationTicks = Ticks.seconds(20)

    override fun begin(input: InputInterface, messenger: ChallengeMessenger) {
        val translations = gameHandle.translations

        input.expectInput()
            .validateInt(translations)
            .onlyOnce()

        val term = randomTerm()
        correctAnswer = term.evaluate()

        messenger.task(translations.translateText("calculate.exercise", styled(term.stringify(), YELLOW)))
    }

    override fun evaluate(choices: PlayerChoices, result: ChallengeResult) {
        result.correctAnswer = correctAnswer
        result.grantIfCorrect(gameHandle.participants, correctAnswer, choices::getInt)
    }

    private fun randomTerm(): Expression {
        val expressions = arrayOf(
            add(range(-100, 100), add(range(1, 50), range(20, 80))),       // a + b + c
            add(range(-500, 500), range(1, 1000)),                                      // a + b
            add(range(1, 100), sub(range(50, 200), range(50, 200))),       // a + b - c
            add(range(1, 250), mul(range(2, 10), range(2, 10))),           // a + b * c
            sub(range(-100, 100), range(1, 100)),                                       // a - b
            sub(range(-100, 100), mul(range(0, 20), range(0, 5))),         // a - b * c
            mul(range(1, 10), add(range(-10, 20), range(1, 10))),          // a * (b + c)
            mul(range(1, 5), mul(range(2, 10), range(2, 4))),              // a * b * c
            divCommon(5, 20, 2, 10),                                   // a / b
            mul(range(2, 5), divCommon(1, 5, 1, 50))      // a * (b / c)
        )

        return expressions[random.nextInt(expressions.size)]
    }

    private fun range(min: Int, max: Int): Expression {
        val n = random(min, max)

        return num(n)
    }

    private fun divCommon(bMin: Int, bMax: Int, cMin: Int, cMax: Int): Expression {
        val b = random(bMin, bMax)
        val c = random(cMin, cMax)

        return div(num(b * c), num(b))
    }

    private fun random(min: Int, max: Int): Int {
        val lo = minOf(min, max)
        val hi = maxOf(min, max)

        return lo + random.nextInt(hi - lo + 1)
    }
}
