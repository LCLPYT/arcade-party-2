package work.lclpnet.ap2.game.pig_race.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.util.ScoreboardUtil;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.ap2.impl.util.scoreboard.DynamicScoreHandle;
import work.lclpnet.ap2.impl.util.scoreboard.DynamicScoreboardObjective;
import work.lclpnet.ap2.impl.util.scoreboard.ScoreboardLayout;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.minecraft.ChatFormatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class PRScoreboard {

    private final MiniGameHandle gameHandle;
    private final PRProgress progress;
    private final DynamicTranslatedPlayerBossBar bossBar;

    private final Set<String> prevHolders = new HashSet<>();
    private final Set<String> holderRemoval = new HashSet<>();

    private DynamicScoreboardObjective objective;
    private @Nullable DynamicScoreHandle roundHandle;

    public PRScoreboard(MiniGameHandle gameHandle, PRProgress progress, DynamicTranslatedPlayerBossBar bossBar) {
        this.gameHandle = gameHandle;
        this.progress = progress;
        this.bossBar = bossBar;
    }

    public void setup() {
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        objective = ScoreboardUtil.setupDynamicSidebar(scoreboardManager, gameHandle.getGameInfo().getTitleKey());

        if (progress.getRounds() <= 1) return;

        TranslatedText text = gameHandle.getTranslations().translateText("game.ap2.pig_race.round").formatted(GREEN);
        roundHandle = objective.createDynamicText(text, ScoreboardLayout.TOP);

        objective.createNewline(ScoreboardLayout.TOP);
    }

    public void addScoreboardRanking() {
        objective.createText(gameHandle.getTranslations().translateText("ap2.ranking").formatted(YELLOW, BOLD));

        var separator = Component.literal(ApConstants.SCOREBOARD_SEPARATOR_SM).withStyle(DARK_GREEN, STRIKETHROUGH);
        objective.createText(separator);

        for (ServerPlayer player : gameHandle.getParticipants()) {
            updateRoundDisplay(player);
            objective.add(player);
        }
    }

    public void updateRanking() {
        holderRemoval.addAll(prevHolders);

        List<ServerPlayer> ranking = progress.getRanking();

        for (int i = 0, len = ranking.size(); i < len; i++) {
            ServerPlayer player = ranking.get(i);
            String holder = player.getScoreboardName();

            prevHolders.add(holder);
            holderRemoval.remove(holder);

            objective.setScore(holder, len - i);
            objective.setNumberFormat(holder, BlankFormat.INSTANCE);
            objective.setDisplayName(holder, Component.literal("#" + (i + 1) + " ").withStyle(YELLOW)
                    .append(Component.literal(holder).withStyle(GREEN)));
        }

        for (String holder : holderRemoval) {
            objective.removeEntry(holder);
            prevHolders.remove(holder);
        }

        holderRemoval.clear();
    }

    public void updateRoundDisplay(ServerPlayer player) {
        int rounds = progress.getRounds();

        if (rounds <= 1) return;

        int round = progress.getRound(player);

        var roundHandle = this.roundHandle;

        if (roundHandle != null) {
            var fmt = new FixedFormat(Component.literal("%s/%s".formatted(round, rounds)).withStyle(YELLOW));

            roundHandle.setNumberFormat(player, fmt);
        }

        bossBar.setArgument(player, 0, styled(round, YELLOW));
    }
}
