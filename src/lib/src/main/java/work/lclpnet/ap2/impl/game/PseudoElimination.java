package work.lclpnet.ap2.impl.game;

import com.google.common.collect.Iterables;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.player.Participants;
import work.lclpnet.ap2.impl.util.DeathMessages;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public class PseudoElimination {

    private final Participants participants;
    private final DeathMessages deathMessages;
    private final ServerLevel world;
    private final Set<UUID> toEliminate = new HashSet<>();

    public PseudoElimination(MiniGameHandle handle, ServerLevel world) {
        this(handle.getParticipants(), handle.getDeathMessages(), world);
    }

    public PseudoElimination(Participants participants, DeathMessages deathMessages, ServerLevel world) {
        this.participants = participants;
        this.deathMessages = deathMessages;
        this.world = world;
    }

    public synchronized boolean isEliminated(ServerPlayer player) {
        return toEliminate.contains(player.getUUID());
    }

    public boolean isParticipating(ServerPlayer player) {
        return participants.isParticipating(player) && !isEliminated(player);
    }

    public synchronized void commit() {
        stream().forEach(participants::remove);

        toEliminate.clear();
    }

    public synchronized boolean eliminate(ServerPlayer player) {
        if (isEliminated(player) || !participants.isParticipating(player)) return false;

        double x = player.getX(), y = player.getY(), z = player.getZ();

        world.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1f, 0f);
        world.sendParticles(ParticleTypes.LAVA, x, y, z, 100, 0.5, 0.5, 0.5, 0.2);

        deathMessages.getDeathMessage(player, null)
                .sendTo(PlayerLookup.all(world.getServer()));

        player.setGameMode(GameType.SPECTATOR);

        toEliminate.add(player.getUUID());

        return true;
    }

    public Stream<ServerPlayer> stream() {
        return toEliminate.stream()
                .map(participants::getParticipant)
                .flatMap(Optional::stream);
    }

    public synchronized int size() {
        return toEliminate.size();
    }

    public Stream<ServerPlayer> streamParticipants() {
        return participants.stream().filter(player -> !isEliminated(player));
    }

    public Iterable<ServerPlayer> iterateParticipants() {
        return Iterables.filter(participants, player -> !isEliminated(player));
    }
}
