package work.lclpnet.ap2.game.guess_it.challenge

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.guess_it.data.*
import work.lclpnet.ap2.game.guess_it.util.GuessItDisplay
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.translate.text.LocalizedFormat
import java.util.*
import kotlin.math.roundToInt

class FoodAmountChallenge(
    private val gameHandle: MiniGameHandle,
    private val random: Random,
    private val display: GuessItDisplay
) : Challenge {

    private var amount = 0

    override fun id() = "food_amount"

    override val preparationKey = PREPARE_GUESS

    override val durationTicks = Ticks.seconds(17)

    override fun begin(input: InputInterface, messenger: ChallengeMessenger) {
        val translations = gameHandle.translations
        messenger.task(translations.translateText("food_amount"))

        input.expectInput().validateFloat(translations, 1)

        val food = selectRandomFood()
        val foodComponent = food.components().get(DataComponents.FOOD)
            ?: throw IllegalStateException("Item has no food component")

        amount = foodComponent.nutrition()

        display.displayItem(ItemStack(food))
    }

    override fun evaluate(choices: PlayerChoices, result: ChallengeResult) {
        result.correctAnswer = LocalizedFormat.format("%.1f", amount * 0.5)
        result.grantClosest3(gameHandle.participants.asSet, amount) { player ->
            choices.getFloat(player)?.let { (it * 2).roundToInt() }
        }
    }

    private fun selectRandomFood(): Item {
        val food = BuiltInRegistries.ITEM.filter { it.components().has(DataComponents.FOOD) }

        return food[random.nextInt(food.size)]
    }
}
