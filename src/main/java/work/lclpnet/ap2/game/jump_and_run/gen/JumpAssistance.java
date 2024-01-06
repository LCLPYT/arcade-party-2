package work.lclpnet.ap2.game.jump_and_run.gen;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.json.JSONArray;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.kibu.util.RotationUtil;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.ArrayList;
import java.util.List;

public record JumpAssistance(List<Pair<BlockPos, BlockState>> blocks) {

    public static final JumpAssistance EMPTY = new JumpAssistance(List.of());

    public JumpAssistance relativize(Vec3i origin) {
        var transformed = blocks.stream()
                .map(pair -> {
                    BlockPos offsetPos = pair.left().subtract(origin);
                    return Pair.of(offsetPos, pair.right());
                })
                .toList();

        return new JumpAssistance(transformed);
    }

    public JumpAssistance transform(AffineIntMatrix mat4) {
        Matrix3i mat3 = mat4.linearPart();

        var transformed = blocks.stream()
                .map(pair -> {
                    BlockPos rotatedPos = mat4.transform(pair.left());
                    BlockState rotatedState = RotationUtil.rotate(pair.right(), mat3);

                    return Pair.of(rotatedPos, rotatedState);
                })
                .toList();

        return new JumpAssistance(transformed);
    }

    public static JumpAssistance fromJson(JSONArray json, Logger logger) {
        List<Pair<BlockPos, BlockState>> blocks = new ArrayList<>(json.length());

        for (Object entry : json) {
            if (!(entry instanceof JSONArray array)) {
                logger.warn("Invalid entry {}. Expected JsonArray", entry);
                continue;
            }

            if (array.length() < 2) {
                logger.warn("Array too small, expected at least two elements");
                continue;
            }

            BlockPos pos = MapUtil.readBlockPos(array.getJSONArray(0));
            BlockState state = MapUtil.readBlockState(array.getString(1));

            blocks.add(Pair.of(pos, state));
        }

        return new JumpAssistance(blocks);
    }
}
