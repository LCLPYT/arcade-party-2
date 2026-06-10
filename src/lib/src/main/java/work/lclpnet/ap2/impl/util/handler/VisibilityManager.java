package work.lclpnet.ap2.impl.util.handler;

import com.google.common.collect.Iterables;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import work.lclpnet.kibu.access.entity.EntityAccess;
import work.lclpnet.kibu.access.network.packet.TeamS2CPacketAccess;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VisibilityManager {

    private final PlayerTeam team;
    private final Map<UUID, Visibility> visibilities = new HashMap<>();
    private final Visibility defaultVisibility;

    public VisibilityManager(PlayerTeam team, Visibility defaultVisibility) {
        this.team = team;
        this.defaultVisibility = defaultVisibility;
    }

    public void toggleVisibilityFor(ServerPlayer player) {
        Visibility next = getVisibilityFor(player).next();

        setVisibilityFor(player, next);
    }

    public void setVisibilityFor(ServerPlayer player, Visibility visibility) {
        if (visibilities.put(player.getUUID(), visibility) == visibility) return;

        applyVisibility(player, visibility);
    }

    public void updateVisibility(ServerPlayer player) {
        applyVisibility(player, getVisibilityFor(player));
    }

    public void updateVisibilityOf(Entity entity) {
        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            updateVisibilityOf(entity, player);
        }
    }

    private void updateVisibilityOf(Entity entity, ServerPlayer player) {
        Visibility visibility = getVisibilityFor(player);
        makeVisibleFor(entity, player, visibility == Visibility.VISIBLE);
    }

    public void onStartTracking(Entity entity, ServerPlayer player) {
        // Only entities that belong to the visibility team are subject to per-player visibility.
        // Skip the player itself and their own vehicle, those must stay visible to the player.
        // This is needed because entities (e.g. vehicles) may be created after the initial visibility setup,
        // at a point where no player tracks them yet, so their visibility has to be applied once tracking begins.
        if (entity == player || entity.getTeam() != team || entity.hasIndirectPassenger(player)) {
            return;
        }

        updateVisibilityOf(entity, player);
    }

    private void applyVisibility(ServerPlayer player, Visibility visibility) {
        if (visibility == Visibility.VISIBLE) {
            makeOthersVisibleFor(player, true);
            return;
        }

        makeOthersVisibleFor(player, false);
        setPartiallyVisibleFor(player, visibility == Visibility.PARTIALLY_VISIBLE);
    }

    private void setPartiallyVisibleFor(ServerPlayer player, boolean partial) {
        var packet = TeamS2CPacketAccess.modifyTeam(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, false),
                team -> TeamS2CPacketAccess.withShowFriendlyInvisibles(team, partial));

        player.connection.send(packet);
    }

    public Visibility getVisibilityFor(ServerPlayer player) {
        return visibilities.getOrDefault(player.getUUID(), defaultVisibility);
    }

    private void makeOthersVisibleFor(ServerPlayer player, boolean visible) {
        for (Entity other : others(player)) {
            makeVisibleFor(other, player, visible);
        }
    }

    private void makeVisibleFor(Entity other, ServerPlayer player, boolean visible) {
        byte flags = other.getEntityData().get(EntityAccess.FLAGS);
        flags = EntityAccess.setFlag(flags, EntityAccess.INVISIBLE_FLAG_INDEX, !visible);

        var entry = SynchedEntityData.DataValue.create(EntityAccess.FLAGS, flags);
        var packet = new ClientboundSetEntityDataPacket(other.getId(), List.of(entry));
        player.connection.send(packet);
    }

    private Iterable<? extends Entity> others(ServerPlayer player) {
        return Iterables.filter(player.level().getAllEntities(), other ->
                other != player && other.getTeam() == team && PlayerLookup.tracking(other).contains(player)
                && !other.hasIndirectPassenger(player));
    }
}
