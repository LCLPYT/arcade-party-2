package work.lclpnet.ap2.impl.base;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import work.lclpnet.ap2.api.base.ParticipantListener;
import work.lclpnet.ap2.api.base.PlayerManager;

import java.util.*;
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
    private boolean prepare = false;
    private boolean finale = false;
    private ParticipantListener listener = null;

    public PlayerManagerImpl(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public Set<ServerPlayer> getAsSet() {
        try {
            readLock.lock();

            return PlayerLookup.all(server).stream()
                    .filter(player -> participants.contains(player.getUUID()))
                    .collect(Collectors.toUnmodifiableSet());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean isParticipating(UUID uuid) {
        Objects.requireNonNull(uuid);

        try {
            readLock.lock();

            return participants.contains(uuid);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean offer(ServerPlayer player) {
        Objects.requireNonNull(player);

        try {
            readLock.lock();

            if (!prepare || finale) return false;
        } finally {
            readLock.unlock();
        }

        try {
            writeLock.lock();

            participants.add(player.getUUID());
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void startPreparation() {
        setPreparation(true);
        reset();
    }

    @Override
    public void startMiniGame() {
        setPreparation(false);
        reset();
    }

    private void setPreparation(boolean prepare) {
        try {
            writeLock.lock();

            this.prepare = prepare;
        } finally {
            writeLock.unlock();
        }
    }

    private void reset() {
        try {
            writeLock.lock();

            if (finale) {
                // remove finalists who left
                participants.removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
            } else {
                addAllPlayers();
            }
        } finally {
            writeLock.unlock();
        }
    }

    private void addAllPlayers() {
        // clear old entries in case the player has left
        participants.clear();

        PlayerLookup.all(server).stream()
                .map(ServerPlayer::getUUID)
                .filter(uuid -> !permanentSpectators.contains(uuid))
                .forEach(participants::add);
    }

    @Override
    public void enterFinale(Set<? extends ServerPlayer> finalists) {
        try {
            writeLock.lock();

            finale = true;
            participants.clear();

            finalists.stream()
                    .map(ServerPlayer::getUUID)
                    .forEach(participants::add);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean isPermanentSpectator(ServerPlayer player) {
        try {
            readLock.lock();

            return permanentSpectators.contains(player.getUUID());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void addPermanentSpectator(ServerPlayer player) {
        try {
            writeLock.lock();

            permanentSpectators.add(player.getUUID());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removePermanentSpectator(ServerPlayer player) {
        try {
            writeLock.lock();

            permanentSpectators.remove(player.getUUID());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void remove(ServerPlayer player) {
        try {
            writeLock.lock();

            if (participants.remove(player.getUUID()) && listener != null) {
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

    @Override
    public Optional<ServerPlayer> getParticipant(UUID uuid) {
        if (!participants.contains(uuid)) {
            return Optional.empty();
        }

        return Optional.ofNullable(server.getPlayerList().getPlayer(uuid));
    }

    @Override
    public void leaveFinale() {
        try {
            writeLock.lock();

            finale = false;

            addAllPlayers();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean isFinale() {
        return finale;
    }
}
