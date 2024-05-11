package work.lclpnet.ap2.game.mimicry.data;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class MimicryManager {

    private final MiniGameHandle gameHandle;
    private final Map<UUID, MimicryRoom> rooms;

    public MimicryManager(MiniGameHandle gameHandle, Map<UUID, MimicryRoom> rooms) {
        this.rooms = rooms;
        this.gameHandle = gameHandle;
    }

    public void eachParticipant(BiConsumer<ServerPlayerEntity, MimicryRoom> action) {
        Participants participants = gameHandle.getParticipants();
        PlayerManager playerManager = gameHandle.getServer().getPlayerManager();

        rooms.forEach((uuid, room) -> {
            if (!participants.isParticipating(uuid)) return;

            ServerPlayerEntity player = playerManager.getPlayer(uuid);

            if (player == null) return;

            action.accept(player, room);
        });
    }
}
