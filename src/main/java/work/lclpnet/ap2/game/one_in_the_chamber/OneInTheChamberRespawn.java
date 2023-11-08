package work.lclpnet.ap2.game.one_in_the_chamber;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Random;

import static com.google.common.primitives.Ints.max;
import static com.google.common.primitives.Ints.min;

public class OneInTheChamberRespawn {

    public static void drawGrid(GameMap map, ArrayList<ArrayList<BlockPos>> grid) {

        final int gridLength = Objects.requireNonNull(map.getProperty("map_length"), "map size not configured");
        final int gridWidth = Objects.requireNonNull(map.getProperty("map_width"), "map size not configured");
        ArrayList<Integer> gridCoords = new ArrayList<>();
        BlockPos center = MapUtil.readBlockPos(Objects.requireNonNull(map.getProperty("center"), "map center not configured"));
        int y = center.getY();

        gridCoords.add(center.getX() + gridLength / 6);
        gridCoords.add(center.getZ() + gridWidth / 6);

        int i = 0;

        while (i <= 2) {
            int j = 0;
            while (j <= 2) {
                ArrayList<BlockPos> section = new ArrayList<>();
                section.add(0, new BlockPos((gridCoords.get(0) - j * (gridLength / 3)), y, (gridCoords.get(1)) - i * (gridWidth / 3)));
                section.add(1, new BlockPos((gridCoords.get(0) + gridLength / 3 - j * (gridLength / 3)), y, (gridCoords.get(1) + gridWidth / 3) - i * (gridWidth / 3)));
                grid.add(section);
                j += 1;
            }
            i += 1;
        }
    }

    public static void respawn(ArrayList<BlockPos> spawnPoints, ServerPlayerEntity player, MiniGameHandle gameHandle, ArrayList<ArrayList<BlockPos>> grid) {

        if (grid.isEmpty()) return;

        ArrayList<Integer> playerCounts = new ArrayList<>();
        Random rand = new Random();
        int counter;

        int randomIndex = rand.nextInt(0, spawnPoints.size() - 1);
        BlockPos randomSpawn = spawnPoints.get(randomIndex);

        player.changeGameMode(GameMode.SPECTATOR);
        player.heal(20);
        PlayerInventoryAccess.setSelectedSlot(player, 0);

        for (ArrayList<BlockPos> section : grid) {
            counter = 0;

            for (ServerPlayerEntity participant : gameHandle.getParticipants()) {
                BlockPos pos = participant.getBlockPos();

                if(pos.getX() >= min(section.get(0).getX(), section.get(1).getX()) && pos.getX() <= max(section.get(0).getX(), section.get(1).getX())
                        && pos.getZ() >= min(section.get(0).getZ(), section.get(1).getZ()) && pos.getZ() <= max(section.get(0).getZ(), section.get(1).getZ())) counter += 1;
            }
            playerCounts.add(counter);
        }

        HashMap<Integer,Integer> map = new HashMap<>();
        int i = 0;
        while (i < 9) {
            map.put(i,playerCounts.get(i));
            i+=1;
        }

        System.out.println(playerCounts);

        gameHandle.getScheduler().timeout(() -> {
            player.teleport(randomSpawn.getX() + 0.5,randomSpawn.getY(),randomSpawn.getZ() + 0.5);
            player.changeGameMode(GameMode.ADVENTURE);
        }, 50);
    }
}
