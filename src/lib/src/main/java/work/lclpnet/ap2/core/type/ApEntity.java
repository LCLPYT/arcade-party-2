package work.lclpnet.ap2.core.type;

public interface ApEntity {

    void ap2$patchNarrowMovement();

    boolean ap2$isPatchNarrowMovement();

    void ap2$patchTrapdoorJumping();

    boolean ap2$isPatchTrapdoorJumping();

    void ap2$setUseMovementYaw(boolean useMovementYaw);

    boolean ap2$isUseMovementYaw();

    void ap2$setMovementYaw(float yaw);

    float ap2$getMovementYaw();
}
