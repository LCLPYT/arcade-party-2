package work.lclpnet.ap2.impl.util.scoreboard;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
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
    private final Objective vanillaObjective;
    private final Map<String, CustomScoreboardEntry> entries = new HashMap<>();
    private Component title;

    public CustomObjective(String name, Component title, ObjectiveCriteria.RenderType renderType, NumberFormat numberFormat) {
        this.name = name;
        this.title = title;

        this.vanillaObjective = new Objective(null, name, ObjectiveCriteria.DUMMY, title,
                renderType, false, numberFormat);
    }

    Objective vanillaObjective() {
        return vanillaObjective;
    }

    public String name() {
        return name;
    }

    public Component display() {
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

    public void setTitle(Component title) {
        this.title = Objects.requireNonNull(title);
    }

    public void setEntry(String holder, CustomScoreboardEntry entry) {
        entries.put(holder, entry);
    }

    public Optional<CustomScoreboardEntry> getEntry(String holder) {
        return Optional.ofNullable(entries.getOrDefault(holder, null));
    }

    public void add(ServerPlayer player) {
        var packet = new ClientboundSetObjectivePacket(this.vanillaObjective(), ClientboundSetObjectivePacket.METHOD_ADD);
        player.connection.send(packet);
    }

    public void remove(ServerPlayer player) {
        var packet = new ClientboundSetObjectivePacket(this.vanillaObjective(), ClientboundSetObjectivePacket.METHOD_REMOVE);
        player.connection.send(packet);
    }

    public void update(ServerPlayer player) {
        var packet = new ClientboundSetObjectivePacket(this.vanillaObjective(), ClientboundSetObjectivePacket.METHOD_CHANGE);
        player.connection.send(packet);
    }

    public void sendScore(ServerPlayer player, String scoreHolder, int score, Component display, NumberFormat format) {
        var packet = new ClientboundSetScorePacket(scoreHolder, this.name(), score, Optional.ofNullable(display), Optional.ofNullable(format));
        player.connection.send(packet);
    }

    public void syncScore(ServerPlayer player, String holder) {
        CustomScoreboardEntry entry = entries.getOrDefault(holder, null);

        if (entry == null) return;

        sendScore(player, holder, entry.getScore(), entry.getDisplay(), entry.getNumberFormat());
    }

    public void syncScores(ServerPlayer player) {
        entries.keySet().forEach((holder) -> syncScore(player, holder));
    }

    public void setDisplay(ServerPlayer player, DisplaySlot slot) {
       setDisplay(player, this, slot);
    }

    public void remove(String holder) {
        entries.remove(holder);
    }

    public void clear(ServerPlayer player, String holder) {
        player.connection.send(new ClientboundResetScorePacket(holder, this.vanillaObjective.getName()));
    }

    public static void setDisplay(ServerPlayer player, @Nullable CustomObjective objective, DisplaySlot slot) {
        // could be that ScoreboardObjectiveUpdateS2CPacket with ScoreboardObjectiveUpdateS2CPacket.REMOVE_MODE has to be sent
        var packet = new ClientboundSetDisplayObjectivePacket(slot, objective != null ? objective.vanillaObjective() : null);

        player.connection.send(packet);
    }
}
