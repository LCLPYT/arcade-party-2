package work.lclpnet.ap2.game.guess_it.util;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import work.lclpnet.ap2.impl.util.world.stage.Stage;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;
import work.lclpnet.lobby.util.WorldModifier;

public class GuessItDisplay {

    private final ServerWorld world;
    private final WorldModifier modifier;
    private final Stage stage;

    public GuessItDisplay(ServerWorld world, WorldModifier modifier, Stage stage) {
        this.world = world;
        this.modifier = modifier;
        this.stage = stage;
    }

    public void displayItem(ItemStack stack) {
        var display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);

        DisplayEntityAccess.setItemStack(display, stack);
        DisplayEntityAccess.setBillboardMode(display, DisplayEntity.BillboardMode.CENTER);

        float scale = 8;

        AffineTransformation transformation = new AffineTransformation(new Matrix4f(
                -scale, 0, 0, 0,
                0, scale, 0, 0,
                0, 0, -scale, 0,
                0, 0, 0, 1
        ));

        DisplayEntityAccess.setTransformation(display, transformation);

        BlockPos origin = stage.getOrigin();

        double x = origin.getX() + 0.5;
        double y = origin.getY() + scale;
        double z = origin.getZ() + 0.5;

        display.setPos(x, y, z);

        modifier.spawnEntity(display);
    }
}
