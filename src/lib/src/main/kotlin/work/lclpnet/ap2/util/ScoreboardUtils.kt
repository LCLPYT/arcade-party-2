package work.lclpnet.ap2.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.world.scores.DisplaySlot
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager
import work.lclpnet.ap2.impl.util.scoreboard.DynamicScoreboardObjective
import work.lclpnet.ap2.impl.util.scoreboard.ScoreboardLayout
import work.lclpnet.ap2.impl.util.scoreboard.TranslatedScoreboardObjective

fun setupSidebar(
    scoreboardManager: CustomScoreboardManager,
    titleTranslationKey: String,
): TranslatedScoreboardObjective {
    val objective = scoreboardManager.translateObjective("score", titleTranslationKey)
        .formatted(ChatFormatting.AQUA, ChatFormatting.BOLD)

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

fun setupDynamicSidebar(scoreboardManager: CustomScoreboardManager, titleTranslationKey: String): DynamicScoreboardObjective {
    val title = scoreboardManager.translations.translateText(titleTranslationKey)
        .formatted(ChatFormatting.AQUA, ChatFormatting.BOLD)

    val objective = scoreboardManager.createDynamicObjective("score") { player ->
        title.translateFor(player)
    }

    objective.setSlot(DisplaySlot.SIDEBAR)

    objective.setDefaultDisplay { _, holder ->
        Component.literal(holder).withStyle(ChatFormatting.GREEN)
    }

    objective.setDefaultNumberFormat(StyledFormat.PLAYER_LIST_DEFAULT)

    // separators at top and bottom
    val separator = Component.literal(ApConstants.SCOREBOARD_SEPARATOR)
        .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.STRIKETHROUGH, ChatFormatting.BOLD)

    objective.createText(separator, ScoreboardLayout.TOP)
    objective.createText(separator, ScoreboardLayout.BOTTOM)

    return objective
}
