package work.lclpnet.ap2.game.guess_it.util;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.game.util.WorldModifier;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

public class GuessItDisplay {

    private final ServerLevel world;
    private final WorldModifier modifier;
    private final BlockShape blockShape;

    public GuessItDisplay(ServerLevel world, WorldModifier modifier, BlockShape blockShape) {
        this.world = world;
        this.modifier = modifier;
        this.blockShape = blockShape;
    }

    public void displayItem(ItemStack stack) {
        var display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, world);

        DisplayEntityAccess.setItemStack(display, stack);
        DisplayEntityAccess.setBillboardMode(display, Display.BillboardConstraints.CENTER);

        float scale = 8;

        Transformation transformation = new Transformation(new Matrix4f(
                -scale, 0, 0, 0,
                0, scale, 0, 0,
                0, 0, -scale, 0,
                0, 0, 0, 1
        ));

        DisplayEntityAccess.setTransformation(display, transformation);

        BlockPos origin = blockShape.origin();

        double x = origin.getX() + 0.5;
        double y = origin.getY() + scale;
        double z = origin.getZ() + 0.5;

        display.setPosRaw(x, y, z);

        modifier.spawnEntity(display);
    }
}
