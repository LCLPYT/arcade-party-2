package work.lclpnet.ap2.game.guess_it.challenge

import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.guess_it.data.*
import work.lclpnet.ap2.game.guess_it.util.MobRandomizer
import work.lclpnet.ap2.game.guess_it.util.MobSpawner
import work.lclpnet.ap2.impl.util.world.PositionUtil.findGroundPositions
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.gaco.ds.IndexedSet
import work.lclpnet.game.util.WorldModifier
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*

class DistinctMobCountChallenge(
    private val gameHandle: MiniGameHandle,
    private val world: ServerLevel,
    private val random: Random,
    private val blockShape: BlockShape,
    private val modifier: WorldModifier,
    private val mannequinUuids: IndexedSet<UUID>
) : Challenge {

    private var amount = 0

    override fun id() = "distinct_mob_count"

    override val preparationKey = PREPARE_ESTIMATE

    override val durationTicks = Ticks.seconds(21)

    override fun begin(input: InputInterface, messenger: ChallengeMessenger) {
        val translations = gameHandle.translations
        messenger.task(translations.translateText("mob_types.guess"))

        input.expectInput().validateInt(translations)

        val defaultTypes = MobRandomizer.defaultTypes
        val minAmount = 4

        amount = minAmount + random.nextInt(minOf(9, defaultTypes.size - minAmount + 1))

        val types = MobRandomizer.trimTypes(defaultTypes.toMutableSet(), random, amount)

        val spaces = MobSpawner.findSpawns(world, types).findSpaces(findGroundPositions(blockShape, world))

        if (spaces.isEmpty()) {
            throw IllegalStateException("No spawn spaces found")
        }

        val randomizer = MobRandomizer(types)
        val spawner = MobSpawner(world, random, mannequinUuids)

        var budget = MobCountMultiChallenge.MIN_BUDGET + random.nextInt(MobCountMultiChallenge.RANDOM_BUDGET)

        while (budget > 0) {
            val type = randomizer.selectRandomEntityType(random)
            val cost = MobCountMultiChallenge.getCost(type)

            budget -= cost

            val pos = spaces[random.nextInt(spaces.size)]
            spawner.spawnEntity(type, pos, modifier)
        }
    }

    override fun evaluate(choices: PlayerChoices, result: ChallengeResult) {
        result.correctAnswer = amount

        result.grantClosest3(gameHandle.participants.asSet, amount, choices::getInt)
    }
}
