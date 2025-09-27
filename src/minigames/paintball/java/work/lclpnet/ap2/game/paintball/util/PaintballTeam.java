package work.lclpnet.ap2.game.paintball.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.json.JSONObject;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;
import work.lclpnet.ap2.api.game.team.TeamKeyable;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.gaco.core.api.Partial;
import work.lclpnet.gaco.ds.BlockBox;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

public record PaintballTeam(Vec3d spawn, float yaw, BlockBox baseBounds, DyeTeamKey templateColor, DyeTeamKey key) implements TeamKeyable {

    public Set<ServerPlayerEntity> participants(TeamManager teamManager, Participants participants) {
        return teamManager.getTeam(this)
                .map(team -> team.getParticipatingPlayers(participants))
                .orElseGet(Set::of);
    }

    public static Partial<PaintballTeam, DyeTeamKey> fromJson(JSONObject json) {
        Vec3d spawn = MapUtil.readCenteredVec3d(json.getJSONArray("spawn"));
        float yaw = MapUtil.readAngle(json.optFloat("yaw", 0));
        BlockBox baseBounds = MapUtil.readBox(json.getJSONArray("base-bounds"));

        String teamId = json.getString("template-color");

        DyeTeamKey templateColor = Optional.ofNullable(DyeTeamKey.byId(teamId))
                .orElseThrow(() -> new NoSuchElementException("Unknown team template-color \"%s\"".formatted(teamId)));

        return key -> new PaintballTeam(spawn, yaw, baseBounds, templateColor, key);
    }
}
