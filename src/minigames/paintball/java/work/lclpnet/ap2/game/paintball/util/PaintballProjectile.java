package work.lclpnet.ap2.game.paintball.util;

import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.gaco.scene.Scene;
import work.lclpnet.gaco.scene.animation.AnimationContext;
import work.lclpnet.gaco.scene.physics.PhysicsBlockDisplayObject;

public class PaintballProjectile extends PhysicsBlockDisplayObject {

    public static final int
            TEAM_COLLISION_ENABLE_TICKS = 4;

    @Getter
    private int ageTicks = 0;

    public PaintballProjectile(Scene scene, BlockState state, ServerWorld world) {
        super(scene, state, world);
    }

    @Override
    public void updateAnimation(double dt, AnimationContext ctx) {
        super.updateAnimation(dt, ctx);

        if (ageTicks++ == TEAM_COLLISION_ENABLE_TICKS) {
            enableTeamCollision();
        }
    }

    private void enableTeamCollision() {
        int groups = rigidBody.getCollideWithGroups();
        int group = rigidBody.getCollisionGroup();

        rigidBody.setCollideWithGroups(groups | (group >> 1));
    }
}
