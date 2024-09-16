package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.block.BlockState;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.util.WeightedList;
import work.lclpnet.kibu.util.BlockStateUtils;

import java.util.Random;

public class BlockPalette {

    private final WeightedList<BlockState> states;

    public BlockPalette(WeightedList<BlockState> states) {
        this.states = states;
    }

    public BlockState sample(Random random) {
        return states.getRandomElement(random);
    }

    public static WeightedList<BlockState> parseStatesFromJson(JSONArray array, Logger logger) {
        WeightedList<BlockState> states = new WeightedList<>(array.length());

        int nextIndex = 0;

        for (Object o : array) {
            int i = nextIndex++;

            if (!(o instanceof JSONObject entry)) {
                logger.warn("Invalid palette entry at index {}, expected object but got {}", i, o != null ? o.getClass().getSimpleName() : null);
                continue;
            }

            float weight = entry.optFloat("weight");

            if (Float.isNaN(weight)) {
                logger.error("Weight undefined for palette entry at index {}", i);
                continue;
            }

            if (weight <= 0) {
                logger.error("Negative weight configured for entry at index {}", i);
                continue;
            }

            String str = entry.optString("state");

            if (str == null) {
                logger.error("State undefined for palette entry at index {}", i);
                continue;
            }

            BlockState state = BlockStateUtils.parse(str);

            if (state == null) {
                logger.error("Unknown block state {} for entry at index {}", str, i);
                continue;
            }

            states.add(state, weight);
        }

        return states;
    }
}
