package work.lclpnet.ap2.game.apocalypse_survival.util;

import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.QuadTree;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.HashMap;
import java.util.Map;

public class MobDensityManager {

    private final QuadTree<MobPos> mobPositions;
    private final Map<MobEntity, MobPos> mobs = new HashMap<>();

    public MobDensityManager(GameMap map) {
        Vec3d spawn = MapUtils.getSpawnPosition(map);
        Number radiusNum = map.requireProperty("bounding-box-radius");

        double x = spawn.getX();
        double z = spawn.getZ();
        double radius = radiusNum.doubleValue();

        mobPositions = new QuadTree<>(x - radius, z - radius, 2 * radius, 2 * radius, 8, MobPos::getPos);
    }

    public void startTracking(MobEntity mob) {
        MobPos mobPos = new MobPos(mob);
        mobs.put(mob, mobPos);
        mobPositions.add(mobPos);
    }

    public void stopTracking(MobEntity mob) {
        MobPos mobPos = mobs.remove(mob);

        if (mobPos != null) {
            mobPositions.remove(mobPos);
        }
    }

    @Nullable
    public Vec3d startGuarding(PathAwareEntity mob) {
        MobPos mobPos = mobs.get(mob);

        if (mobPos == null) {
            // not tracked
            return null;
        }

        Vec3d guardPos = findGuardPos(mob);

        if (guardPos == null) {
            return null;
        }

        // override mob position, so that not every mob is headed towards the same area
        mobPos.guardPos = guardPos;
        mobPositions.update(mobPos);

        return guardPos;
    }

    public void stopGuarding(PathAwareEntity mob) {
        MobPos mobPos = mobs.get(mob);

        if (mobPos == null) return;

        // use live position again
        mobPos.guardPos = null;
        mobPositions.update(mobPos);
    }

    @Nullable
    private Vec3d findGuardPos(PathAwareEntity mob) {
        // find quadrant with the least mobs or use root node
        QuadTree.INode<MobPos> leastMobs = mobPositions.getRoot();
        int least = Integer.MAX_VALUE;

        var it = leastMobs.children();

        while (it.hasNext()) {
            var child = it.next();
            int count = child.count();

            if (count < least) {
                leastMobs = child;
                least = count;
            }
        }

        // calc node center coordinates
        double x = leastMobs.x() + leastMobs.width() * 0.5;
        double z = leastMobs.z() + leastMobs.level() * 0.5;

//        System.out.println(mobPositions.getRoot().count() + " :: " + node);

        return FuzzyTargeting.findTo(mob, 20, 10, new Vec3d(x, mob.getY(), z));
    }

    public void update() {
        for (var mobPos : mobs.values()) {
//            if (mobPos.mob.getTarget() != null) {
//                // use navigation target as guardPos
//                Path currentPath = mobPos.mob.getNavigation().getCurrentPath();
//
//                if (currentPath != null) {
//                    mobPos.guardPos = currentPath.getTarget().toCenterPos();
//                } else {
//                    mobPos.guardPos = null;
//                }
//            }

            mobPositions.update(mobPos);
        }
    }

    private static class MobPos {
        final MobEntity mob;
        Vec3d guardPos = null;

        MobPos(MobEntity mob) {
            this.mob = mob;
        }

        Vec3d getPos() {
            if (guardPos != null) {
                return guardPos;
            }

            return mob.getPos();
        }
    }
}
