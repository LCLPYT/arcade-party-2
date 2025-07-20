package work.lclpnet.ap2.game.paintball.util;

import net.minecraft.util.math.Vec3d;
import org.json.JSONObject;
import work.lclpnet.ap2.api.ds.Partial;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.NoSuchElementException;
import java.util.Optional;

public record PaintballTeam(Vec3d spawn, float yaw, BlockBox baseBounds, DyeTeamKey templateColor, DyeTeamKey key) {

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
