package work.lclpnet.ap2.impl.game;

import com.google.common.collect.Iterables;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.GameMode;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.util.DeathMessages;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public class PseudoElimination {

    private final Participants participants;
    private final DeathMessages deathMessages;
    private final ServerWorld world;
    private final Set<UUID> toEliminate = new HashSet<>();

    public PseudoElimination(MiniGameHandle handle, ServerWorld world) {
        this(handle.getParticipants(), handle.getDeathMessages(), world);
    }

    public PseudoElimination(Participants participants, DeathMessages deathMessages, ServerWorld world) {
        this.participants = participants;
        this.deathMessages = deathMessages;
        this.world = world;
    }

    public synchronized boolean isEliminated(ServerPlayerEntity player) {
        return toEliminate.contains(player.getUuid());
    }

    public boolean isParticipating(ServerPlayerEntity player) {
        return participants.isParticipating(player) && !isEliminated(player);
    }

    public synchronized void commit() {
        stream().forEach(participants::remove);

        toEliminate.clear();
    }

    public synchronized boolean eliminate(ServerPlayerEntity player) {
        if (isEliminated(player) || !participants.isParticipating(player)) return false;

        double x = player.getX(), y = player.getY(), z = player.getZ();

        world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1f, 0f);
        world.spawnParticles(ParticleTypes.LAVA, x, y, z, 100, 0.5, 0.5, 0.5, 0.2);

        deathMessages.getDeathMessage(player, null)
                .sendTo(PlayerLookup.all(world.getServer()));

        player.changeGameMode(GameMode.SPECTATOR);

        toEliminate.add(player.getUuid());

        return true;
    }

    public Stream<ServerPlayerEntity> stream() {
        return toEliminate.stream()
                .map(participants::getParticipant)
                .flatMap(Optional::stream);
    }

    public synchronized int size() {
        return toEliminate.size();
    }

    public Stream<ServerPlayerEntity> streamParticipants() {
        return participants.stream().filter(player -> !isEliminated(player));
    }

    public Iterable<ServerPlayerEntity> iterateParticipants() {
        return Iterables.filter(participants, player -> !isEliminated(player));
    }
}
