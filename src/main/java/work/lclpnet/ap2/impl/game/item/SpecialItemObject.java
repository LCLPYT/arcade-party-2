package work.lclpnet.ap2.impl.game.item;

import lombok.Getter;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.scene.ItemDisplayObject;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.animation.Animatable;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;

public class SpecialItemObject extends Object3d implements Animatable {

    public static final double DEFAULT_SIZE = 0.25;
    private final SpecialItem item;
    private final double size;
    private final float ageOffset = (float) (Math.random() * Math.PI * 2);
    private final ItemDisplayObject itemDisplay;
    private double age = 0;
    private Box boundingBox;
    @Getter private boolean pickedUp = false;
    private @Nullable PickupAnimation pickupAnimation = null;

    public SpecialItemObject(SpecialItem item, ItemStack stack) {
        this(item, stack, 0.25);
    }

    public SpecialItemObject(SpecialItem item, ItemStack stack, double size) {
        this.item = item;
        this.size = size;

        itemDisplay = new ItemDisplayObject(stack);
        itemDisplay.position.set(0, 0.25, 0);
        itemDisplay.scale.set(size / DEFAULT_SIZE);
        itemDisplay.setTransformationMode(ModelTransformationMode.GROUND);

        addChild(itemDisplay);

        updateBoundingBox();
    }

    public ItemDisplayObject itemDisplay() {
        return itemDisplay;
    }

    public SpecialItem item() {
        return item;
    }

    public Box boxAt(double x, double y, double z) {
        return new Box(
                x - size, y, z - size,
                x + size, y + 2 * size, z + size);
    }

    private void updateBoundingBox() {
        boundingBox = boxAt(position.x, position.y, position.z);
    }

    public boolean isOnGround(ServerWorld world) {
        Box box = boxAt(position.x, position.y - 0.05, position.z);
        return world.getBlockCollisions(null, box).iterator().hasNext();
    }

    @Override
    public void updateMatrixWorld(boolean withParent, boolean withChildren) {
        super.updateMatrixWorld(withParent, withChildren);

        updateBoundingBox();
    }

    @Override
    public void updateAnimation(double dt, AnimationContext ctx) {
        age += dt;

        if (pickupAnimation != null) {
            pickupAnimation.updateAnimation(dt, ctx);
            return;
        }

        itemDisplay.rotation.set(RotationAxis.POSITIVE_Y.rotation((float) age + ageOffset));

        float offsetY = MathHelper.sin((float) age * 2.f + this.ageOffset) * 0.1F + 0.1F;
        itemDisplay.position.set(0.0F, offsetY + 0.25F * itemDisplay.scale.y, 0.0F);
    }

    public boolean intersects(Box box) {
        return boundingBox.intersects(box);
    }

    public void startPickup(ServerPlayerEntity player, Runnable whenDone) {
        if (pickedUp) return;

        pickedUp = true;

        pickupAnimation = new PickupAnimation(this, target -> target.set(player.getX(), player.getY(), player.getZ()), whenDone);
    }
}
