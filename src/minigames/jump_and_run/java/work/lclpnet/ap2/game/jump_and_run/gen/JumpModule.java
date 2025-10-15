package work.lclpnet.ap2.game.jump_and_run.gen;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.util.effect.ApEffect;
import work.lclpnet.ap2.impl.util.effect.ApEffects;

import java.util.Set;

public record JumpModule(String path, Data data) {

    public static @Nullable JumpModule fromJson(JSONObject json, Logger logger) {
        String path = json.optString("path", null);

        if (path == null) {
            logger.error("Path not configured for jump module");
            return null;
        }

        float value = json.optNumber("value", 1f).floatValue();
        float weight = json.optNumber("weight", 1f).floatValue();

        Set<ApEffect> effects = ApEffects.fromJson(json.optJSONArray("effects", new JSONArray()), logger);

        return new JumpModule(path, new Data(value, 0, weight, effects));
    }

    public record Data(float estimatedMinutes, int stackingMargin, float weight, Set<ApEffect> effects) {}
}
