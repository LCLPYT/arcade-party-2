package work.lclpnet.ap2.game.red_light_green_light;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.json.JSONObject;
import work.lclpnet.ap2.impl.map.MapUtil;

import java.util.EnumSet;

public record TrafficLight(BlockPos red, BlockPos yellow, BlockPos green) {

    public void set(EnumSet<Status> status, ServerLevel world) {
        BlockState off = Blocks.BLACK_CONCRETE.defaultBlockState();

        if (status.contains(Status.RED)) {
            world.setBlockAndUpdate(red, Blocks.RED_CONCRETE.defaultBlockState());
        } else {
            world.setBlockAndUpdate(red, off);
        }

        if (status.contains(Status.YELLOW)) {
            world.setBlockAndUpdate(yellow, Blocks.YELLOW_CONCRETE.defaultBlockState());
        } else {
            world.setBlockAndUpdate(yellow, off);
        }

        if (status.contains(Status.GREEN)) {
            world.setBlockAndUpdate(green, Blocks.LIME_CONCRETE.defaultBlockState());
        } else {
            world.setBlockAndUpdate(green, off);
        }
    }

    public static TrafficLight fromJson(JSONObject json) {
        BlockPos red = MapUtil.readBlockPos(json.getJSONArray("red"));
        BlockPos yellow = MapUtil.readBlockPos(json.getJSONArray("yellow"));
        BlockPos green = MapUtil.readBlockPos(json.getJSONArray("green"));

        return new TrafficLight(red, yellow, green);
    }

    public enum Status {
        RED, YELLOW, GREEN
    }
}
