package work.lclpnet.ap2.game.jump_and_run.gen;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.json.JSONArray;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.kibu.util.RotationUtil;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public record JumpAssistance(List<Pair<BlockBox, BlockState>> blocks) {

    public static final JumpAssistance EMPTY = new JumpAssistance(List.of());

    public JumpAssistance relativize(Vec3i origin) {
        var transformed = blocks.stream()
                .map(pair -> {
                    BlockBox offsetBox = pair.left().translate(origin.multiply(-1));
                    return Pair.of(offsetBox, pair.right());
                })
                .toList();

        return new JumpAssistance(transformed);
    }

    public JumpAssistance transform(AffineIntMatrix mat4) {
        Matrix3i mat3 = mat4.linearPart();

        var transformed = blocks.stream()
                .map(pair -> {
                    BlockBox rotatedPos = pair.left().transform(mat4);
                    BlockState rotatedState = RotationUtil.rotate(pair.right(), mat3);

                    return Pair.of(rotatedPos, rotatedState);
                })
                .toList();

        return new JumpAssistance(transformed);
    }

    public void forEach(BiConsumer<BlockState, BlockPos> action) {
        for (var block : blocks()) {
            BlockBox box = block.left();
            BlockState state = block.right();

            for (BlockPos pos : box) {
                action.accept(state, pos);
            }
        }
    }

    public static JumpAssistance fromJson(JSONArray json, Logger logger) {
        List<Pair<BlockBox, BlockState>> blocks = new ArrayList<>(json.length());

        for (Object entry : json) {
            if (!(entry instanceof JSONArray array)) {
                logger.warn("Invalid entry {}. Expected JsonArray", entry);
                continue;
            }

            if (array.length() < 2) {
                logger.warn("Array too small, expected at least two elements");
                continue;
            }

            JSONArray positional = array.getJSONArray(0);

            if (positional.isEmpty()) {
                logger.warn("Empty positional array for assistance entry {}", entry);
                continue;
            }

            BlockBox box;

            if (positional.get(0) instanceof JSONArray) {
                box = MapUtil.readBox(positional);
            } else {
                BlockPos pos = MapUtil.readBlockPos(positional);
                box = BlockBox.of(pos);
            }

            BlockState state = MapUtil.readBlockState(array.getString(1));

            blocks.add(Pair.of(box, state));
        }

        return new JumpAssistance(blocks);
    }
}
