package work.lclpnet.ap2.impl.util.handler;

import com.google.common.collect.Iterables;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.kibu.access.entity.EntityAccess;
import work.lclpnet.kibu.access.network.packet.TeamS2CPacketAccess;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VisibilityManager {

    private final Team team;
    private final Map<UUID, Visibility> visibilities = new HashMap<>();

    public VisibilityManager(Team team) {
        this.team = team;
    }

    public void toggleVisibilityFor(ServerPlayerEntity player) {
        Visibility next = getVisibilityFor(player).next();

        setVisibilityFor(player, next);
    }

    public void setVisibilityFor(ServerPlayerEntity player, Visibility visibility) {
        if (visibilities.put(player.getUuid(), visibility) == visibility) return;

        applyVisibility(player, visibility);
    }

    public void updateVisibility(ServerPlayerEntity player) {
        applyVisibility(player, getVisibilityFor(player));
    }

    private void applyVisibility(ServerPlayerEntity player, Visibility visibility) {
        if (visibility == Visibility.VISIBLE) {
            makeOthersVisibleFor(player, true);
            return;
        }

        makeOthersVisibleFor(player, false);
        setPartiallyVisibleFor(player, visibility == Visibility.PARTIALLY_VISIBLE);
    }

    private void setPartiallyVisibleFor(ServerPlayerEntity player, boolean partial) {
        var packet = TeamS2CPacketAccess.modifyTeam(TeamS2CPacket.updateTeam(team, false),
                team -> TeamS2CPacketAccess.withShowFriendlyInvisibles(team, partial));

        player.networkHandler.sendPacket(packet);
    }

    public Visibility getVisibilityFor(ServerPlayerEntity player) {
        return visibilities.getOrDefault(player.getUuid(), Visibility.VISIBLE);
    }

    private void makeOthersVisibleFor(ServerPlayerEntity player, boolean visible) {
        for (ServerPlayerEntity other : others(player)) {
            byte flags = other.getDataTracker().get(EntityAccess.FLAGS);
            flags = EntityAccess.setFlag(flags, EntityAccess.INVISIBLE_FLAG_INDEX, !visible);

            var entry = DataTracker.SerializedEntry.of(EntityAccess.FLAGS, flags);
            var packet = new EntityTrackerUpdateS2CPacket(other.getId(), List.of(entry));
            player.networkHandler.sendPacket(packet);
        }
    }

    private Iterable<ServerPlayerEntity> others(ServerPlayerEntity player) {
        return Iterables.filter(player.getServerWorld().getPlayers(), other -> other != player);
    }
}
