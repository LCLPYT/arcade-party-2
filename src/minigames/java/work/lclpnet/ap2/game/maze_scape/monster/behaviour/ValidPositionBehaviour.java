package work.lclpnet.ap2.game.maze_scape.monster.behaviour;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;

import static work.lclpnet.ap2.game.maze_scape.monster.behaviour.UnstuckBehaviour.teleport;

public class ValidPositionBehaviour implements MonsterBehaviour {

    private final MSManager manager;
    private final Logger logger;

    public ValidPositionBehaviour(MSManager manager, Logger logger) {
        this.manager = manager;
        this.logger = logger;
    }

    @Override
    public void tick(MobEntity mob) {
        var node = manager.struct().nodeAt(mob.getX(), mob.getY(), mob.getZ());

        if (node == null) {
            teleportToDistantPos(mob);
            return;
        }

        OrientedStructurePiece oriented = node.oriented();

        if (oriented == null) {
            teleportToDistantPos(mob);
            return;
        }

        if (oriented.isPitAt(mob.getBlockX(), mob.getBlockY(), mob.getBlockZ())) {
            Vec3d spawn = oriented.spawn();

            if (spawn == null) {
                teleportToDistantPos(mob);
                return;
            }

            teleport(mob, spawn);
        }
    }

    private void teleportToDistantPos(Entity entity) {
        var spawns = manager.spawns();

        if (spawns == null) {
            logger.error("Could not find distant position");
            return;
        }

        teleport(entity, spawns.get());
    }
}
