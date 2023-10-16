package work.lclpnet.ap2.impl;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import work.lclpnet.ap2.Prototype;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static work.lclpnet.lobby.config.ConfigUtil.getVec3d;

@Prototype
public class MapUtils {

    @NotNull
    public static Vec3d getSpawnPosition(GameMap gameMap) {
        if (gameMap.getProperty("spawn") instanceof JSONArray array) {
            return getVec3d(array);
        }

        throw missingProperty("spawn");
    }

    @NotNull
    public static List<Vec3d> getSpawnPositions(GameMap gameMap) {
        if (!(gameMap.getProperty("spawns") instanceof JSONArray array)) {
            throw missingProperty("spawns");
        }

        List<Vec3d> spawns = new ArrayList<>();

        for (Object element : array) {
            if (element instanceof JSONArray elemArray) {
                spawns.add(getVec3d(elemArray));
            }
        }

        return spawns;
    }

    @NotNull
    public static Map<String, Vec3d> getNamedSpawnPositions(GameMap gameMap) {
        if (!(gameMap.getProperty("spawns") instanceof JSONObject object)) {
            throw missingProperty("spawns");
        }

        Map<String, Vec3d> spawns = new Object2ObjectOpenHashMap<>();

        for (String key : object.keySet()) {
            Object value = object.get(key);

            if (value instanceof JSONArray elemArray) {
                spawns.put(key, getVec3d(elemArray));
            }
        }

        return spawns;
    }

    private static IllegalStateException missingProperty(String property) {
        return new IllegalStateException("Property \"%s\" is undefined".formatted(property));
    }
}
