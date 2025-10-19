package work.lclpnet.ap2.impl.util.scoreboard;

import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreResetS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Simple controller of a custom objective, providing networking bindings for higher level scoreboard APIs.
 */
public final class CustomObjective {

    private final String name;
    private final ScoreboardObjective vanillaObjective;
    private final Map<String, CustomScoreboardEntry> entries = new HashMap<>();
    private Text title;

    public CustomObjective(String name, Text title, ScoreboardCriterion.RenderType renderType, NumberFormat numberFormat) {
        this.name = name;
        this.title = title;

        this.vanillaObjective = new ScoreboardObjective(null, name, ScoreboardCriterion.DUMMY, title,
                renderType, false, numberFormat);
    }

    ScoreboardObjective vanillaObjective() {
        return vanillaObjective;
    }

    public String name() {
        return name;
    }

    public Text display() {
        return title;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (CustomObjective) obj;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.title, that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, title);
    }

    @Override
    public String toString() {
        return "Objective[name=%s, title=%s]".formatted(name, title);
    }

    public void setTitle(Text title) {
        this.title = Objects.requireNonNull(title);
    }

    public void setEntry(String holder, CustomScoreboardEntry entry) {
        entries.put(holder, entry);
    }

    public Optional<CustomScoreboardEntry> getEntry(String holder) {
        return Optional.ofNullable(entries.getOrDefault(holder, null));
    }

    public void add(ServerPlayerEntity player) {
        var packet = new ScoreboardObjectiveUpdateS2CPacket(this.vanillaObjective(), ScoreboardObjectiveUpdateS2CPacket.ADD_MODE);
        player.networkHandler.sendPacket(packet);
    }

    public void remove(ServerPlayerEntity player) {
        var packet = new ScoreboardObjectiveUpdateS2CPacket(this.vanillaObjective(), ScoreboardObjectiveUpdateS2CPacket.REMOVE_MODE);
        player.networkHandler.sendPacket(packet);
    }

    public void update(ServerPlayerEntity player) {
        var packet = new ScoreboardObjectiveUpdateS2CPacket(this.vanillaObjective(), ScoreboardObjectiveUpdateS2CPacket.UPDATE_MODE);
        player.networkHandler.sendPacket(packet);
    }

    public void sendScore(ServerPlayerEntity player, String scoreHolder, int score, Text display, NumberFormat format) {
        var packet = new ScoreboardScoreUpdateS2CPacket(scoreHolder, this.name(), score, Optional.ofNullable(display), Optional.ofNullable(format));
        player.networkHandler.sendPacket(packet);
    }

    public void syncScore(ServerPlayerEntity player, String holder) {
        CustomScoreboardEntry entry = entries.getOrDefault(holder, null);

        if (entry == null) return;

        sendScore(player, holder, entry.getScore(), entry.getDisplay(), entry.getNumberFormat());
    }

    public void syncScores(ServerPlayerEntity player) {
        entries.keySet().forEach((holder) -> syncScore(player, holder));
    }

    public void setDisplay(ServerPlayerEntity player, ScoreboardDisplaySlot slot) {
       setDisplay(player, this, slot);
    }

    public void remove(String holder) {
        entries.remove(holder);
    }

    public void clear(ServerPlayerEntity player, String holder) {
        player.networkHandler.sendPacket(new ScoreboardScoreResetS2CPacket(holder, this.vanillaObjective.getName()));
    }

    public static void setDisplay(ServerPlayerEntity player, @Nullable CustomObjective objective, ScoreboardDisplaySlot slot) {
        // could be that ScoreboardObjectiveUpdateS2CPacket with ScoreboardObjectiveUpdateS2CPacket.REMOVE_MODE has to be sent
        var packet = new ScoreboardDisplayS2CPacket(slot, objective != null ? objective.vanillaObjective() : null);

        player.networkHandler.sendPacket(packet);
    }
}
