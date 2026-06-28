package work.lclpnet.ap2.game.guess_it.challenge

import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.guess_it.data.*
import work.lclpnet.ap2.game.guess_it.util.GuessItDisplay
import work.lclpnet.ap2.game.guess_it.util.OptionMaker
import work.lclpnet.ap2.impl.util.TextUtil
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*

class PotionTypeChallenge(
    private val gameHandle: MiniGameHandle,
    private val random: Random,
    private val display: GuessItDisplay
) : Challenge {

    private var correct: ItemStack? = null
    private var correctOption = -1

    override fun id() = "potion_type"

    override val preparationKey = PREPARE_GUESS

    override val durationTicks = Ticks.seconds(15)

    override fun begin(input: InputInterface, messenger: ChallengeMessenger) {
        val translations = gameHandle.translations
        messenger.task(translations.translateText("potion_type"))

        val potions = getPotions()

        val item = when (random.nextInt(3)) {
            0 -> Items.POTION
            1 -> Items.SPLASH_POTION
            2 -> Items.LINGERING_POTION
            else -> throw IllegalStateException()
        }

        val options = OptionMaker.createOptions(potions, 4, random).map { potion ->
            val stack = ItemStack(item)

            val potionEntry = BuiltInRegistries.POTION.wrapAsHolder(potion)
            stack.set(DataComponents.POTION_CONTENTS, PotionContents(potionEntry))

            stack
        }

        correctOption = random.nextInt(options.size)
        correct = options[correctOption]

        display.displayItem(correct!!)

        val names: List<Component> = options.map { TextUtil.getVanillaName(it) }
        input.expectSelection(*names.toTypedArray())
    }

    override fun evaluate(choices: PlayerChoices, result: ChallengeResult) {
        result.correctAnswer = TextUtil.getVanillaName(correct!!)
        result.grantIfCorrect(gameHandle.participants, correctOption, choices::getOption)
    }

    private fun getPotions(): Set<Potion> {
        val allPotions = BuiltInRegistries.POTION.toMutableSet()

        allPotions.remove(Potions.AWKWARD.value())
        allPotions.remove(Potions.MUNDANE.value())
        allPotions.remove(Potions.THICK.value())
        allPotions.remove(Potions.WATER.value())

        // filter multiple length of the same status effect
        val effects = HashSet<Set<Holder<MobEffect>>>()

        val iterator = allPotions.iterator()

        while (iterator.hasNext()) {
            val potion = iterator.next()

            val effectSet = potion.effects.map { it.effect }.toSet()

            if (!effects.add(effectSet)) {
                iterator.remove()
            }
        }

        return allPotions
    }
}
