package work.lclpnet.ap2.game.paintball.util;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;
import work.lclpnet.ap2.impl.scene.object.PhysicsBlockDisplayObject;

public class PaintballProjectile extends PhysicsBlockDisplayObject {

    public static final int
            TEAM_COLLISION_ENABLE_TICKS = 4;

    private int age = 0;

    public PaintballProjectile(BlockState state, ServerWorld world) {
        super(state, world);
    }

    @Override
    public void updateAnimation(double dt, AnimationContext ctx) {
        super.updateAnimation(dt, ctx);

        if (age++ == TEAM_COLLISION_ENABLE_TICKS) {
            enableTeamCollision();
        }
    }

    private void enableTeamCollision() {
        int groups = rigidBody.getCollideWithGroups();
        int group = rigidBody.getCollisionGroup();

        rigidBody.setCollideWithGroups(groups | (group >> 1));
    }
}
