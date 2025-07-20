package work.lclpnet.ap2.game.paintball.util;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.simulation.SceneRigidBody;
import work.lclpnet.ap2.impl.util.math.MathUtil;

import java.util.Random;

import static work.lclpnet.kibu.physics.impl.bullet.math.Convert.toBullet;

public class PaintGunManager {

    private final Scene scene;
    private final PaintManager paintManager;
    private final PaintballTeams teams;
    private final Random random;

    public PaintGunManager(Scene scene, PaintManager paintManager, PaintballTeams teams, Random random) {
        this.scene = scene;
        this.paintManager = paintManager;
        this.teams = teams;
        this.random = random;
    }

    public void shoot(ServerPlayerEntity player) {
        BlockState state = teams.teamOf(player)
                .map(PaintballTeam::key)
                .map(paintManager::getPaintBulletState)
                .orElse(null);

        if (state == null) return;

        Vec3d dir = player.getRotationVector();
        Vec3d pos = player.getEyePos().add(dir.multiply(1));

        var obj = new PaintballBullet(state, player.getWorld());
        obj.position.set(pos.getX(), pos.getY(), pos.getZ());
        obj.setOwner(player.getUuid());

        SceneRigidBody rigidBody = obj.getRigidBody();

        if (rigidBody == null) return;

        rigidBody.setLinearVelocity(toBullet(dir.multiply(10)));
        rigidBody.setAngularVelocity(toBullet(MathUtil.randomUnitVec3d(random)));
        rigidBody.setPhysicsLocation(toBullet(pos));

        scene.add(obj);
    }
}
