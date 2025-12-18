package work.lclpnet.ap2.game.paintball.util;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.TeamRef;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.ap2.impl.util.title.TitleAnimation;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class PaintballResultAnimation implements TitleAnimation {

    private static final int 
            DURATION_TICKS = Ticks.seconds(5), 
            FINAL_DELAY_TICKS = Ticks.seconds(1);

    private final MinecraftServer server;
    private final Translations translations;
    private final Runnable callback;
    private final List<Entry> entries;
    private final double[] targetValues;
    private final double maxPercent;
    private int time = 0;

    public PaintballResultAnimation(Iterable<TeamRef> teams, IntScoreDataContainer<Team, TeamRef> data,
                                    MinecraftServer server, Translations translations, Runnable callback) {
        this.server = server;
        this.translations = translations;
        this.callback = callback;

        entries = new ArrayList<>();

        for (TeamRef team : teams) {
            entries.add(new Entry(team.key(), data.getScore(team)));
        }

        final int total = entries.stream().mapToInt(e -> e.score).sum();

        for (Entry entry : entries) {
            entry.byTotal(total);
        }

        targetValues = entries.stream().mapToDouble(e -> e.percent).toArray();

        maxPercent = Arrays.stream(targetValues).max().orElse(0);
    }

    @Override
    public boolean tick() {
        int t = time++;
        
        if (t <= DURATION_TICKS) {
            updateTitle(t);

            playAnimationSound(t);

            if (t == DURATION_TICKS) {
                playFinalSound();
            }
        }

        return t >= DURATION_TICKS + FINAL_DELAY_TICKS;
    }

    private void playAnimationSound(double t) {
        float progress = (float) max(0.d, min(1.d, t / DURATION_TICKS));

        final float minPitch = 0.5f, maxPitch = 1.6f;
        float pitch = minPitch + progress * (maxPitch - minPitch);

        SoundHelper.playSound(server, SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 0.2f, pitch);
    }

    private void playFinalSound() {
        SoundHelper.playSound(server, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.NEUTRAL, 0.5f, 2.f);
    }

    private void updateTitle(double t) {
        double progress = max(0.d, min(1.d, t / DURATION_TICKS));

        double lerpedValue = maxPercent * progress;

        double[] interpolated = Arrays.stream(targetValues)
                .map(val -> min(val, lerpedValue) * 100)
                .toArray();

        for (ServerPlayer player : PlayerLookup.all(server)) {
            Locale locale = translations.getLocale(player);
            Component msg = createMessage(interpolated, locale);

            Title.get(player).title(Component.empty(), msg);
        }
    }

    private Component createMessage(double[] interpolated, Locale locale) {
        MutableComponent root = Component.empty();
        int i = 0;

        final int totalChars = 35;
        final int occupied = interpolated.length * 5;  // strlen("__._%") = 5
        final int spacerLen = interpolated.length > 1 ? (totalChars - occupied) / (interpolated.length - 1) : 0;

        String spacer = " ".repeat(spacerLen);

        for (Entry entry : entries) {
            if (i > 0) {
                root.append(Component.literal(spacer));
            }

            String str = String.format(locale, "%.1f%%", interpolated[i]);
            root.append(Component.literal(str).withColor(entry.key.color()));
            i++;
        }

        return root;
    }

    @Override
    public void begin() {
        for (ServerPlayer player : players()) {
            Title.get(player).times(0, 20, 0);
        }
    }

    @Override
    public void destroy() {
        for (ServerPlayer player : players()) {
            Title.get(player).resetTimes();
        }

        callback.run();
    }

    private Iterable<? extends ServerPlayer> players() {
        return PlayerLookup.all(server);
    }

    private static class Entry {
        private final TeamKey key;
        private final int score;
        private double percent = 0;

        private Entry(TeamKey key, int score) {
            this.key = key;
            this.score = score;
        }

        public void byTotal(int total) {
            percent = total == 0 ? 0 : (double) score / total;
        }
    }
}
