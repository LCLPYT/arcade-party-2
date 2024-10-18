package work.lclpnet.ap2.game.maze_scape.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MSTargetManager {

    private final MSStruct struct;
    private final Participants participants;
    private final Map<UUID, UUID> playerTargetedBy = new HashMap<>();

    public MSTargetManager(MSStruct struct, Participants participants) {
        this.struct = struct;
        this.participants = participants;
    }

    @Nullable
    public ServerPlayerEntity findNearestTarget(MobEntity mob) {
        var mobNode = struct.nodeAt(mob.getPos());

        if (mobNode == null) return null;

        var distanceCalculator = struct.distanceCalculator();

        Object2IntMap<ServerPlayerEntity> distances = new Object2IntOpenHashMap<>();
        int minDistance = Integer.MAX_VALUE;

        for (ServerPlayerEntity player : participants) {
            // skip players already targeted by other mobs
            if (playerTargetedBy.containsKey(player.getUuid())) continue;

            var playerNode = struct.nodeAt(player.getPos());

            if (playerNode == null) continue;

            int distance = distanceCalculator.distance(mobNode, playerNode);

            distances.put(player, distance);

            if (distance < minDistance) {
                minDistance = distance;
            }
        }

        EntityNavigation navigation = mob.getNavigation();
        int minLength = Integer.MAX_VALUE;
        ServerPlayerEntity nearest = null;

        for (ServerPlayerEntity player : participants) {
            int distance = distances.getOrDefault(player, Integer.MAX_VALUE);

            // only consider players that are within minimum distance + 1
            if (distance > minDistance + 1) continue;

            Path path = navigation.findPathTo(player, 0);

            if (path == null) continue;

            int length = path.getLength();

            if (length < minLength) {
                minLength = length;
                nearest = player;
            }
        }

        return nearest;
    }

    public void assignTarget(MobEntity mob, ServerPlayerEntity player) {
        mob.setTarget(player);
        playerTargetedBy.put(player.getUuid(), mob.getUuid());
    }
}
