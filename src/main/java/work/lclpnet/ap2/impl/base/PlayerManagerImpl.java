package work.lclpnet.ap2.impl.base;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.ParticipantListener;
import work.lclpnet.ap2.api.base.PlayerManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class PlayerManagerImpl implements PlayerManager {

    private final MinecraftServer server;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> permanentSpectators = new HashSet<>();
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();
    private boolean finale = false;
    private ParticipantListener listener = null;

    public PlayerManagerImpl(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public Set<ServerPlayerEntity> getParticipants() {
        try {
            readLock.lock();

            return PlayerLookup.all(server).stream()
                    .filter(player -> participants.contains(player.getUuid()))
                    .collect(Collectors.toUnmodifiableSet());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void reset() {
        try {
            writeLock.lock();

            if (finale) {
                // remove finalists who left
                participants.removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
            } else {
                // clear old entries in case the player has left
                participants.clear();

                // add everyone to the participants set
                PlayerLookup.all(server).stream()
                        .map(ServerPlayerEntity::getUuid)
                        .filter(uuid -> !permanentSpectators.contains(uuid))
                        .forEach(participants::add);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void enterFinale(Set<? extends ServerPlayerEntity> finalists) {
        try {
            writeLock.lock();

            finale = true;
            participants.clear();

            finalists.stream()
                    .map(ServerPlayerEntity::getUuid)
                    .forEach(participants::add);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void addPermanentSpectator(ServerPlayerEntity player) {
        try {
            writeLock.lock();

            permanentSpectators.add(player.getUuid());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removePermanentSpectator(ServerPlayerEntity player) {
        try {
            writeLock.lock();

            permanentSpectators.remove(player.getUuid());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeParticipant(ServerPlayerEntity player) {
        try {
            writeLock.lock();

            if (participants.remove(player.getUuid()) && listener != null) {
                listener.participantRemoved(player);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void bind(ParticipantListener listener) {
        this.listener = listener;
    }
}
