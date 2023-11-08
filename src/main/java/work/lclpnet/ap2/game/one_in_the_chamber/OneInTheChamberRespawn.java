package work.lclpnet.ap2.game.one_in_the_chamber;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;

import static com.google.common.primitives.Ints.max;
import static com.google.common.primitives.Ints.min;

public class OneInTheChamberRespawn {

    public void drawGrid(GameMap map, ArrayList<ArrayList<BlockPos>> grid) {

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

    public void respawn(ArrayList<BlockPos> spawnPoints, ServerPlayerEntity player, MiniGameHandle gameHandle, ArrayList<ArrayList<BlockPos>> grid) {

        if (grid.isEmpty()) return;

        player.changeGameMode(GameMode.SPECTATOR);
        player.heal(20);
        PlayerInventoryAccess.setSelectedSlot(player, 0);

        gameHandle.getScheduler().timeout(() -> {
            BlockPos randomSpawn = chooseRandomSpawn(grid, gameHandle, spawnPoints);
            player.teleport(randomSpawn.getX() + 0.5,randomSpawn.getY(),randomSpawn.getZ() + 0.5);
            player.changeGameMode(GameMode.ADVENTURE);
        }, 50);
    }

    private boolean checkGridSection(BlockPos pos, ArrayList<BlockPos> section) {
        return pos.getX() >= min(section.get(0).getX(), section.get(1).getX()) && pos.getX() <= max(section.get(0).getX(), section.get(1).getX())
                && pos.getZ() >= min(section.get(0).getZ(), section.get(1).getZ()) && pos.getZ() <= max(section.get(0).getZ(), section.get(1).getZ());
    }

    private BlockPos chooseRandomSpawn(ArrayList<ArrayList<BlockPos>> grid, MiniGameHandle gameHandle, ArrayList<BlockPos> spawnPoints) {

        IntList playerCounts = new IntArrayList();
        ArrayList<BlockPos> spawnPointsBySection = new ArrayList<>();
        Random rand = new Random();

        for (ArrayList<BlockPos> section : grid) {
            int counter = 0;

            for (ServerPlayerEntity participant : gameHandle.getParticipants()) {
                if (participant.isSpectator()) continue;
                BlockPos pos = participant.getBlockPos();

                if(checkGridSection(pos, section)) counter += 1;
            }
            playerCounts.add(counter);
        }

        for (ArrayList<BlockPos> section : grid) {

            for (BlockPos spawn : spawnPoints) {
                if(checkGridSection(spawn, section)) spawnPointsBySection.add(spawn);
            }
        }

        int min = playerCounts.intStream().min().orElseThrow();

        int[] minSectors = IntStream.range(0, playerCounts.size())
                .filter(i -> playerCounts.getInt(i) == min)
                .toArray();

        int randomIndex = rand.nextInt(0, minSectors.length - 1);

        int randomSector = minSectors[randomIndex];

        return spawnPointsBySection.get(randomSector);
    }
}


