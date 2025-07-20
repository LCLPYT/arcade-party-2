package work.lclpnet.ap2.game.paintball.util;

import com.jme3.math.Vector3f;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.object.PhysicsBlockDisplayObject;
import work.lclpnet.ap2.impl.scene.simulation.SceneRigidBody;
import work.lclpnet.kibu.physics.impl.bullet.math.Convert;

public class PaintGunManager {

    private final Scene scene;
    private final PaintManager paintManager;
    private final PaintballTeams teams;

    public PaintGunManager(Scene scene, PaintManager paintManager, PaintballTeams teams) {
        this.scene = scene;
        this.paintManager = paintManager;
        this.teams = teams;
    }

    public void shoot(ServerPlayerEntity player) {
        BlockState state = teams.teamOf(player)
                .map(PaintballTeam::key)
                .map(paintManager::getPaintBulletState)
                .orElse(null);

        if (state == null) return;

        Vec3d dir = player.getRotationVector();
        Vec3d pos = player.getEyePos().add(dir.multiply(1));

        var obj = new PhysicsBlockDisplayObject(state, player.getWorld());
        obj.position.set(pos.getX(), pos.getY(), pos.getZ());

        SceneRigidBody rigidBody = obj.getRigidBody();

        if (rigidBody == null) return;

        rigidBody.setLinearVelocity(Convert.toBullet(dir.multiply(10)));
        rigidBody.setAngularVelocity(new Vector3f());
        rigidBody.setPhysicsLocation(Convert.toBullet(pos));

        scene.add(obj);
    }
}
