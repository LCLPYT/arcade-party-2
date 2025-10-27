package work.lclpnet.ap2.game.pig_race.util;

import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.scoreboard.number.FixedNumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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

import static net.minecraft.util.Formatting.*;
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

        var separator = Text.literal(ApConstants.SCOREBOARD_SEPARATOR_SM).formatted(DARK_GREEN, STRIKETHROUGH);
        objective.createText(separator);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            updateRoundDisplay(player);
            objective.add(player);
        }
    }

    public void updateRanking() {
        holderRemoval.addAll(prevHolders);

        List<ServerPlayerEntity> ranking = progress.getRanking();

        for (int i = 0, len = ranking.size(); i < len; i++) {
            ServerPlayerEntity player = ranking.get(i);
            String holder = player.getNameForScoreboard();

            prevHolders.add(holder);
            holderRemoval.remove(holder);

            objective.setScore(holder, len - i);
            objective.setNumberFormat(holder, BlankNumberFormat.INSTANCE);
            objective.setDisplayName(holder, Text.literal("#" + (i + 1) + " ").formatted(YELLOW)
                    .append(Text.literal(holder).formatted(GREEN)));
        }

        for (String holder : holderRemoval) {
            objective.removeEntry(holder);
            prevHolders.remove(holder);
        }

        holderRemoval.clear();
    }

    public void updateRoundDisplay(ServerPlayerEntity player) {
        int rounds = progress.getRounds();

        if (rounds <= 1) return;

        int round = progress.getRound(player);

        var roundHandle = this.roundHandle;

        if (roundHandle != null) {
            var fmt = new FixedNumberFormat(Text.literal("%s/%s".formatted(round, rounds)).formatted(YELLOW));

            roundHandle.setNumberFormat(player, fmt);
        }

        bossBar.setArgument(player, 0, styled(round, YELLOW));
    }
}
