package work.lclpnet.ap2.impl.game.item;

import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import work.lclpnet.ap2.impl.scene.ItemDisplayObject;
import work.lclpnet.ap2.impl.scene.Object3d;

public class SpecialItemObject extends Object3d {

    public static final double DEFAULT_SIZE = 0.25;
    private final double size;

    public SpecialItemObject(ItemStack stack) {
        this(stack, 0.25);
    }

    public SpecialItemObject(ItemStack stack, double size) {
        this.size = size;

        ItemDisplayObject itemDisplay = new ItemDisplayObject(stack);
        itemDisplay.position.set(0, 0.25, 0);
        itemDisplay.scale.set(size / DEFAULT_SIZE);
        itemDisplay.setTransformationMode(ModelTransformationMode.GROUND);

        addChild(itemDisplay);
    }

    public Box boxAt(double x, double y, double z) {
        return new Box(
                x - size, y, z - size,
                x + size, y + 2 * size, z + size);
    }

    public boolean isOnGround(ServerWorld world) {
        Box box = boxAt(position.x, position.y - 0.05, position.z);
        return world.getBlockCollisions(null, box).iterator().hasNext();
    }
}
