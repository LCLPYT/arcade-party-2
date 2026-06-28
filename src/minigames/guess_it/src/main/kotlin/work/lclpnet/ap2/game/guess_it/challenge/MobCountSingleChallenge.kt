package work.lclpnet.ap2.game.guess_it.challenge

import net.minecraft.ChatFormatting.YELLOW
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.guess_it.data.*
import work.lclpnet.ap2.game.guess_it.util.MobRandomizer
import work.lclpnet.ap2.game.guess_it.util.MobSpawner
import work.lclpnet.ap2.impl.util.TextUtil
import work.lclpnet.ap2.impl.util.world.PositionUtil.findGroundPositions
import work.lclpnet.ap2.impl.util.world.SizedSpaceFinder
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.gaco.ds.IndexedSet
import work.lclpnet.game.util.WorldModifier
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*

class MobCountSingleChallenge(
    private val gameHandle: MiniGameHandle,
    private val world: ServerLevel,
    private val random: Random,
    private val blockShape: BlockShape,
    private val modifier: WorldModifier,
    private val mannequinUuids: IndexedSet<UUID>
) : Challenge {

    private var amount = 0

    override fun id() = "mob_count_single"

    override val preparationKey = PREPARE_ESTIMATE

    override val durationTicks = Ticks.seconds(16)

    override fun begin(input: InputInterface, messenger: ChallengeMessenger) {
        val translations = gameHandle.translations

        input.expectInput().validateInt(translations)

        val randomizer = MobRandomizer()
        val type = randomizer.selectRandomEntityType(random)

        amount = getRandomAmount(type)

        val spaceFinder = SizedSpaceFinder.create(world, type)
        val spaces = spaceFinder.findSpaces(findGroundPositions(blockShape, world))

        if (spaces.isEmpty()) {
            throw IllegalStateException("There are no spaces that support " + BuiltInRegistries.ENTITY_TYPE.getKey(type))
        }

        val spawner = MobSpawner(world, random, mannequinUuids)

        repeat(amount) {
            val pos = spaces[random.nextInt(spaces.size)]
            spawner.spawnEntity(type, pos, modifier)
        }

        val entityName = TextUtil.getVanillaName(type).withStyle(YELLOW)

        messenger.task(translations.translateText("mob.guess", entityName, YELLOW))
    }

    override fun evaluate(choices: PlayerChoices, result: ChallengeResult) {
        result.correctAnswer = amount

        result.grantClosest3(gameHandle.participants.asSet, amount, choices::getInt)
    }

    private fun getRandomAmount(type: EntityType<*>): Int {
        if (type == EntityTypes.WARDEN || type == EntityTypes.ELDER_GUARDIAN || type == EntityTypes.RAVAGER || type == EntityTypes.WITHER) {
            return 12 + random.nextInt(20)
        }

        if (type == EntityTypes.CAMEL || type == EntityTypes.IRON_GOLEM || type == EntityTypes.SNIFFER) {
            return 22 + random.nextInt(54)
        }

        if (type == EntityTypes.GUARDIAN || type == EntityTypes.HOGLIN || type == EntityTypes.ZOGLIN) {
            return 27 + random.nextInt(78)
        }

        return 31 + random.nextInt(102)
    }
}
