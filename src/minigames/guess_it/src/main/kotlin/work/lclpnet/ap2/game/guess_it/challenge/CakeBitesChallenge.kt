package work.lclpnet.ap2.game.guess_it.challenge

import com.mojang.math.Transformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CakeBlock
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.guess_it.data.*
import work.lclpnet.ap2.game.guess_it.util.DynamicEntityModifier
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.game.util.WorldModifier
import work.lclpnet.kibu.access.entity.DisplayEntityAccess
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*

class CakeBitesChallenge(
    private val gameHandle: MiniGameHandle,
    private val world: ServerLevel,
    private val random: Random,
    private val blockShape: BlockShape,
    private val modifier: WorldModifier,
    private val dynamicEntities: DynamicEntityModifier
) : Challenge {

    private var amount = 0

    override fun id() = "cake_bites"

    override val preparationKey = PREPARE_ESTIMATE

    override val durationTicks = Ticks.seconds(14)

    override fun begin(input: InputInterface, messenger: ChallengeMessenger) {
        val translations = gameHandle.translations
        messenger.task(translations.translateText("cake_bites"))

        input.expectInput().validateInt(translations)

        amount = random.nextInt(7)

        createCake()

        val origin = blockShape.origin()

        addHint(
            dynamicEntities,
            world,
            gameHandle.translations,
            Vec3(origin.x + 0.5, origin.y + 4.5, origin.z + 0.5),
            "cake_bites.hint"
        )
    }

    private fun createCake() {
        val display = Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, world)
        DisplayEntityAccess.setBlockState(display, Blocks.CAKE.defaultBlockState().setValue(CakeBlock.BITES, amount))

        val scale = 7f

        DisplayEntityAccess.setTransformation(display, Transformation(Matrix4f().scale(7f)))

        val origin = blockShape.origin()
        val x = origin.x + 0.5 - scale * 0.5
        val y = origin.y.toDouble()
        val z = origin.z + 0.5 - scale * 0.5

        display.setPosRaw(x, y, z)

        modifier.spawnEntity(display)
    }

    override fun evaluate(choices: PlayerChoices, result: ChallengeResult) {
        result.correctAnswer = amount
        result.grantClosest3(gameHandle.participants.asSet, amount, choices::getInt)
    }
}
