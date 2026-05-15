package work.lclpnet.ap2.game.apocalypse_survival.util;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.gaco.ds.IndexedSet;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.game.map.MapUtils;

import java.util.*;

public class MobDensityManager {

    private static final double CELL_SIZE = 25;
    private final Random random;
    private final double centerX, centerZ;
    private final int cellsX, cellsZ;
    private final int[] cells;
    private final IndexedSet<Integer> cellsWithLeast;
    private final Map<Mob, MobPos> mobs = new HashMap<>();
    private int minMobCount = 0;

    public MobDensityManager(GameMap map, Random random) {
        this.random = random;

        Vec3 spawn = MapUtils.getSpawnPosition(map);
        Number radiusNum = map.requireProperty("bounding-box-radius");

        centerX = spawn.x();
        centerZ = spawn.z();

        double radius = radiusNum.doubleValue();

        // determine cell count
        cellsX = cellsZ = 2 * Math.max(1, (int) Math.ceil(radius / CELL_SIZE));

        // initialize mob count to zero
        cells = new int[cellsX * cellsZ];

        Arrays.fill(cells, 0);

        // initialize all cells as cell with the least mobs (zero)
        cellsWithLeast = new IndexedSet<>(cellsX * cellsZ);

        for (int i = 0; i < cellsX * cellsZ; i++) {
            cellsWithLeast.add(i);
        }
    }

    public int cellWithLeastMobs() {
        if (cellsWithLeast.isEmpty()) {
            throw new IllegalStateException("Least mob cells list is empty, this shouldn't happen");
        }

        return cellsWithLeast.get(random.nextInt(cellsWithLeast.size()));
    }

    public OptionalInt cellAt(double x, double z) {
        int cellX = (int) Math.floor(x / CELL_SIZE) + cellsX / 2;
        int cellZ = (int) Math.floor(z / CELL_SIZE) + cellsZ / 2;

        if (cellX < 0 || cellX >= cellsX || cellZ < 0 || cellZ >= cellsZ) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(cellX + cellZ * cellsX);
    }

    public double getX(int cell) {
        return centerX + CELL_SIZE * ((cell % cellsX) - cellsX * 0.5);
    }

    public double getZ(int cell) {
        return centerZ + CELL_SIZE * (Math.floorDiv(cell,  cellsX) - cellsZ * 0.5);
    }

    public void startTracking(Mob mob) {
        MobPos mobPos = new MobPos(mob);
        mobs.put(mob, mobPos);

        updateMobPos(mobPos);
    }

    public void stopTracking(Mob mob) {
        MobPos mobPos = mobs.remove(mob);

        if (mobPos == null) return;

        removeFromCell(mobPos);
    }

    @Nullable
    public Vec3 startGuarding(PathfinderMob mob) {
        MobPos mobPos = mobs.get(mob);

        if (mobPos == null) return null;  // not tracked

        Vec3 guardPos = findGuardPos(mob);

        if (guardPos == null) return null;

        // override mob position, so that not every mob is headed towards the same area
        mobPos.guardPos = guardPos;
        updateMobPos(mobPos);

        return guardPos;
    }

    public void stopGuarding(PathfinderMob mob) {
        MobPos mobPos = mobs.get(mob);

        if (mobPos == null) return;

        // use live position again
        mobPos.guardPos = null;
        updateMobPos(mobPos);
    }

    private void updateMobPos(MobPos mobPos) {
        var optCell = cellAt(mobPos.getX(), mobPos.getZ());

        // skip if nothing changed
        if ((optCell.isPresent() && mobPos.hasCell && mobPos.cell == optCell.getAsInt())
            || (optCell.isEmpty() && !mobPos.hasCell)) return;

        removeFromCell(mobPos);

        if (optCell.isEmpty()) {
            mobPos.hasCell = false;
            return;
        }

        addToCell(mobPos);
    }

    private void addToCell(MobPos mobPos) {
        int newMobCount = ++cells[mobPos.cell];

        if (newMobCount != minMobCount + 1) return;

        // cell was among the cells with the least mobs
        cellsWithLeast.remove((Object) mobPos.cell);

        if (!cellsWithLeast.isEmpty()) return;

        // cell was the last cell with the minMobCount; update it
        minMobCount = newMobCount;

        // find all cells with new min count
        for (int i = 0; i < cellsX * cellsZ; i++) {
            if (cells[i] == newMobCount) {
                cellsWithLeast.add(i);
            }
        }
    }

    private void removeFromCell(MobPos mobPos) {
        if (!mobPos.hasCell) return;

        int newMobCount = --cells[mobPos.cell];

        if (newMobCount < minMobCount) {
            // cell is now the single cell with the least mobs
            minMobCount = newMobCount;
            cellsWithLeast.clear();
            cellsWithLeast.add(mobPos.cell);
        } else if (newMobCount == minMobCount) {
            // cell is now among the cells with the least mobs
            cellsWithLeast.add(mobPos.cell);
        }
    }

    @Nullable
    private Vec3 findGuardPos(PathfinderMob mob) {
        int cell = cellWithLeastMobs();

        double x = getX(cell) + 0.5 * CELL_SIZE;
        double z = getZ(cell) + 0.5 * CELL_SIZE;

        return LandRandomPos.getPosTowards(mob, 20, 10, new Vec3(x, mob.getY(), z));
    }

    public void update() {
        for (var mobPos : mobs.values()) {
            updateMobPos(mobPos);
        }
    }

    private static class MobPos {
        final Mob mob;
        Vec3 guardPos = null;
        int cell;
        boolean hasCell = false;

        MobPos(Mob mob) {
            this.mob = mob;
        }

        double getX() {
            if (guardPos != null) {
                return guardPos.x();
            }

            return mob.getX();
        }

        double getZ() {
            if (guardPos != null) {
                return guardPos.z();
            }

            return mob.getZ();
        }
    }
}
