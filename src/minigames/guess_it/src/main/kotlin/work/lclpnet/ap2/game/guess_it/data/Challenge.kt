package work.lclpnet.ap2.game.guess_it.data

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.math.Transformation
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Brightness
import net.minecraft.world.entity.Display
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import work.lclpnet.ap2.game.guess_it.util.DynamicEntityModifier
import work.lclpnet.gaco.dynamic_entities.TranslatedTextDisplay
import work.lclpnet.kibu.translate.Translations

interface Challenge {
    fun id(): String

    val preparationKey: String

    val durationTicks: Int

    fun begin(input: InputInterface, messenger: ChallengeMessenger)

    fun evaluate(choices: PlayerChoices, result: ChallengeResult)

    fun destroy() {}

    fun prepare() {}

    fun shouldPlayBeginSound(): Boolean {
        return true
    }

    fun init(init: Any?) {}

    fun provideInitCommand(node: LiteralArgumentBuilder<CommandSourceStack>, init: Initializer) {}

    fun addHint(
        dynamicEntities: DynamicEntityModifier,
        world: ServerLevel,
        translations: Translations,
        pos: Vec3,
        key: String
    ) {
        val label = TranslatedTextDisplay(world, translations)

        val controller = label.controller()
        controller.billboardMode = Display.BillboardConstraints.CENTER
        controller.transformation = Transformation(Matrix4f().scale(3f))
        controller.position = pos
        controller.text = translations.translateText(key).withStyle(ChatFormatting.GREEN)
        controller.brightness = Brightness(15, 15)

        dynamicEntities.spawn(label)
    }

    fun interface Initializer {
        fun accept(ctx: CommandContext<CommandSourceStack>, config: Any?)
    }
}
