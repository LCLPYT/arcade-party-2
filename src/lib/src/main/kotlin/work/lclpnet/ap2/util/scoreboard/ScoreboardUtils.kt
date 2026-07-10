package work.lclpnet.ap2.util.scoreboard

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.world.scores.DisplaySlot
import work.lclpnet.ap2.ApConstants

fun setupTranslatedSidebarObjective(
    scoreboardManager: CustomScoreboardManager,
    titleTranslationKey: String,
): TranslatedScoreboardObjective {
    val objective = scoreboardManager.translateObjective("score", titleTranslationKey)
        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)

    objective.setSlot(DisplaySlot.SIDEBAR)

    objective.setDisplayName { holder ->
        Component.literal(holder).withStyle(ChatFormatting.GREEN)
    }

    objective.setNumberFormat(StyledFormat.PLAYER_LIST_DEFAULT)

    // separators at top and bottom
    val separator = Component.literal(ApConstants.SCOREBOARD_SEPARATOR)
        .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.STRIKETHROUGH, ChatFormatting.BOLD)

    objective.createText(separator, ScoreboardLayout.TOP)
    objective.createText(separator, ScoreboardLayout.BOTTOM)

    return objective
}

fun setupDynamicSidebarObjective(scoreboardManager: CustomScoreboardManager, titleTranslationKey: String): DynamicScoreboardObjective {
    val title = scoreboardManager.translations.translateText(titleTranslationKey)
        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)

    val objective = scoreboardManager.createDynamicObjective("score") { player ->
        title.translateFor(player)
    }

    objective.setSlot(DisplaySlot.SIDEBAR)

    objective.defaultDisplay = { _, holder ->
        Component.literal(holder).withStyle(ChatFormatting.GREEN)
    }

    objective.defaultNumberFormat = StyledFormat.PLAYER_LIST_DEFAULT

    // separators at top and bottom
    val separator = Component.literal(ApConstants.SCOREBOARD_SEPARATOR)
        .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.STRIKETHROUGH, ChatFormatting.BOLD)

    objective.createText(separator, ScoreboardLayout.TOP)
    objective.createText(separator, ScoreboardLayout.BOTTOM)

    return objective
}
