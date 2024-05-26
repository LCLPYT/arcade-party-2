package work.lclpnet.ap2.game.glowing_bomb.data;

import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import work.lclpnet.ap2.impl.scene.BlockDisplayObject;
import work.lclpnet.ap2.impl.scene.ItemDisplayObject;
import work.lclpnet.ap2.impl.scene.Object3d;

public class GbBomb extends Object3d {

    public GbBomb() {
        double v = 0.0625;
        double w = 1.125;

        // lower
        frame(0, -v, -v, 1, v, v);
        frame(0, -v, 1, 1, v, v);
        frame(-v, -v, -v, v, v, w);
        frame(1, -v, -v, v, v, w);

        // upper
        frame(0, 1, -v, 1, v, v);
        frame(0, 1, 1, 1, v, v);
        frame(-v, 1, -v, v, v, w);
        frame(1, 1, -v, v, v, w);

        // sides
        frame(1, 0, -v, v, 1, v);
        frame(1, 0, 1, v, 1, v);
        frame(-v, 0, 1, v, 1, v);
        frame(-v, 0, -v, v, 1, v);

        // glass
        addChild(new BlockDisplayObject(Blocks.TINTED_GLASS.getDefaultState()));

        ItemDisplayObject lever = new ItemDisplayObject(new ItemStack(Items.LEVER));
        lever.position.set(0.875, 1.1875, 0.1875);
        lever.rotation.setAngleAxis(0.5235987755982988, 0, 1, 0);
        lever.scale.set(0.5);

        addChild(lever);
    }

    private void frame(double px, double py, double pz, double sx, double sy, double sz) {
        BlockDisplayObject frame = new BlockDisplayObject(Blocks.RED_CONCRETE.getDefaultState());
        frame.position.set(px, py, pz);
        frame.scale.set(sx, sy, sz);

        addChild(frame);
    }
}
