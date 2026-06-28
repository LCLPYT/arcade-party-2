package work.lclpnet.ap2.game.guess_it.challenge

import net.minecraft.ChatFormatting.YELLOW
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.guess_it.data.*
import work.lclpnet.ap2.game.guess_it.util.MobRandomizer
import work.lclpnet.ap2.game.guess_it.util.MobSpawner
import work.lclpnet.ap2.impl.util.TextUtil
import work.lclpnet.ap2.impl.util.world.PositionUtil.findGroundPositions
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.gaco.ds.IndexedSet
import work.lclpnet.game.util.WorldModifier
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*
import kotlin.math.floor

class MobCountMultiChallenge(
    private val gameHandle: MiniGameHandle,
    private val world: ServerLevel,
    private val random: Random,
    private val blockShape: BlockShape,
    private val modifier: WorldModifier,
    private val mannequinUuids: IndexedSet<UUID>
) : Challenge {

    private var amount = 0

    override fun id() = "mob_count_multi"

    override val preparationKey = PREPARE_ESTIMATE

    override val durationTicks = Ticks.seconds(16)

    override fun begin(input: InputInterface, messenger: ChallengeMessenger) {
        val translations = gameHandle.translations

        input.expectInput().validateInt(translations)

        var budget = MIN_BUDGET + random.nextInt(RANDOM_BUDGET)
        amount = random.nextInt(floor(budget * 0.2).toInt())
        budget -= amount

        val types = MobRandomizer.defaultTypes.toMutableSet()
        val typeCount = types.size

        if (typeCount < 2) {
            throw IllegalStateException("There must be at least two entity types")
        }

        val spaces = MobSpawner.findSpawns(world, types).findSpaces(findGroundPositions(blockShape, world))

        if (spaces.isEmpty()) {
            throw IllegalStateException("No spawn spaces found")
        }

        val searched = types.stream().skip(random.nextInt(typeCount).toLong()).findFirst().orElseThrow()
        types.remove(searched)

        val trimmed = MobRandomizer.trimTypes(types, random, 10)

        val randomizer = MobRandomizer(trimmed)
        val spawner = MobSpawner(world, random, mannequinUuids)

        repeat(amount) {
            val spawn = spaces[random.nextInt(spaces.size)]
            spawner.spawnEntity(searched, spawn, modifier)
        }

        while (budget > 0) {
            val type = randomizer.selectRandomEntityType(random)
            val cost = getCost(type)

            budget -= cost

            val spawn = spaces[random.nextInt(spaces.size)]
            spawner.spawnEntity(type, spawn, modifier)
        }

        val entityName = TextUtil.getVanillaName(searched).withStyle(YELLOW)

        messenger.task(translations.translateText("mob.guess", entityName, YELLOW))
    }

    override fun evaluate(choices: PlayerChoices, result: ChallengeResult) {
        result.correctAnswer = amount

        result.grantClosest3(gameHandle.participants.asSet, amount, choices::getInt)
    }

    companion object {
        const val MIN_BUDGET = 43
        const val RANDOM_BUDGET = 97

        fun getCost(type: EntityType<*>): Int {
            if (type == EntityTypes.GIANT) {
                return 10
            }

            if (type == EntityTypes.WARDEN || type == EntityTypes.ELDER_GUARDIAN || type == EntityTypes.RAVAGER
                || type == EntityTypes.WITHER || type == EntityTypes.GHAST || type == EntityTypes.HAPPY_GHAST) {
                return 5
            }

            if (type == EntityTypes.CAMEL || type == EntityTypes.IRON_GOLEM || type == EntityTypes.SNIFFER) {
                return 3
            }

            if (type == EntityTypes.GUARDIAN || type == EntityTypes.HOGLIN || type == EntityTypes.ZOGLIN) {
                return 2
            }

            return 1
        }
    }
}
