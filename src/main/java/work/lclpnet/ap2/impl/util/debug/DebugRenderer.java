package work.lclpnet.ap2.impl.util.debug;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.util.model.Model;
import work.lclpnet.ap2.api.util.model.ModelManager;
import work.lclpnet.ap2.impl.scene.BlockDisplayObject;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.TextDisplayObject;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.model.Models;
import work.lclpnet.ap2.impl.util.model.TemplateModel;

import java.util.function.Consumer;

public class DebugRenderer {

    private final Scene scene;
    private final ModelManager modelManager;
    private final Consumer<Object3d> displayConsumer;

    public DebugRenderer(Scene scene, ModelManager modelManager, Consumer<Object3d> displayConsumer) {
        this.scene = scene;
        this.modelManager = modelManager;
        this.displayConsumer = displayConsumer;
    }

    public void display(Object3d obj) {
        if (scene == null) return;

        scene.add(obj);

        if (displayConsumer != null) {
            displayConsumer.accept(obj);
        }
    }

    public Object3d line(Vec3d start, Vec3d end, double thickness, BlockState state) {
        return line(start.getX(), start.getY(), start.getZ(), end.getX(), end.getY(), end.getZ(), thickness, state);
    }

    public Object3d line(double x1, double y1, double z1, double x2, double y2, double z2, double thickness, BlockState state) {
        var line = new BlockDisplayObject(state);

        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;

        line.position.set(x1, y1, z1);

        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // line points in x-direction
        line.scale.set(len, thickness, thickness);

        line.rotation.rotateTo(1, 0, 0, dx / len, dy / len, dz / len);

        display(line);

        return line;
    }

    public Object3d box(BlockBox box, BlockState state) {
        var obj = new BlockDisplayObject(state);

        BlockPos pos = box.min();
        obj.position.set(pos.getX(), pos.getY(), pos.getZ());
        obj.scale.set(box.width(), box.height(), box.length());

        display(obj);

        return obj;
    }

    public Object3d box(Box box, BlockState state) {
        var obj = new BlockDisplayObject(state);

        obj.position.set(box.minX, box.minY, box.minZ);
        obj.scale.set(box.getLengthX(), box.getLengthY(), box.getLengthZ());

        display(obj);

        return obj;
    }

    private Model arrowModel(BlockState state) {
        Model baseModel = modelManager.getModel(Models.ARROW).orElseThrow();
        return TemplateModel.replace(baseModel, Blocks.LIME_CONCRETE.getDefaultState(), state);
    }

    public Object3d arrow(double x, double y, double z, double angleYRad, double scale, BlockState state) {
        return arrow(x, y, z, angleYRad, scale, arrowModel(state));
    }

    public Object3d arrow(double x, double y, double z, double angleYRad, double scale, Model model) {
        Object3d arrow = model.createInstance();

        arrow.scale.set(scale);
        arrow.position.set(x, y, z);
        arrow.rotation.setAngleAxis(angleYRad, 0, 1, 0);

        display(arrow);

        return arrow;
    }

    public void arrow(Vec3d pos, Vec3d dir, BlockState color) {
        arrow(pos, dir, 0.5, color);
    }

    public void arrow(Vec3d pos, Vec3d dir, double scale, BlockState color) {
        arrow(pos.x, pos.y, pos.z, dir.x, dir.y, dir.z, scale, color);
    }

    public void arrow(double x, double y, double z, double dx, double dy, double dz, double scale, BlockState state) {
        var model = TemplateModel.replace(arrowModel(state), Blocks.LIME_CONCRETE.getDefaultState(), state);

        Object3d marker = model.createInstance();
        marker.position.set(0, 0, 0.5);

        Object3d wrapper = new Object3d();
        wrapper.addChild(marker);
        wrapper.scale.set(scale);
        wrapper.position.set(x, y, z);
        wrapper.rotation.rotateTo(0, 0, 1, dx, dy, dz);

        display(wrapper);
    }

    public Object3d marker(Vec3d pos, BlockState state, int glowColor) {
        return marker(pos.x, pos.y, pos.z, state, glowColor);
    }

    public Object3d marker(double x, double y, double z, BlockState state, int glowColor) {
        return marker(x, y, z, state, glowColor, 0.25);
    }

    public Object3d marker(double x, double y, double z, BlockState state, int glowColor, double scale) {
        var marker = new BlockDisplayObject(state);

        marker.position.set(-0.5, -0.5, -0.5);
        marker.setGlowing(true);
        marker.setGlowColorOverride(glowColor);
        marker.setInterpolationDuration(1);

        Object3d wrapper = new Object3d();
        wrapper.position.set(x, y, z);
        wrapper.scale.set(scale);
        wrapper.addChild(marker);

        display(wrapper);

        return wrapper;
    }

    public Object3d text(Vec3d pos, Text text) {
        return text(pos, text, 0.25);
    }

    public Object3d text(Vec3d pos, Text text, double scale) {
        return text(pos.getX(), pos.getY(), pos.getZ(), text, scale);
    }

    public Object3d text(double x, double y, double z, Text text, double scale) {
        var display = new TextDisplayObject(text);

        display.position.set(x, y, z);
        display.scale.set(scale);
        display.setBillboardMode(DisplayEntity.BillboardMode.CENTER);

        display(display);

        return display;
    }
}
