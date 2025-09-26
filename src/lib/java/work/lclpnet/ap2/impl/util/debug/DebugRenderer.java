package work.lclpnet.ap2.impl.util.debug;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.util.model.Model;
import work.lclpnet.ap2.api.util.model.ModelManager;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.object.BlockDisplayObject;
import work.lclpnet.ap2.impl.scene.object.TextDisplayObject;
import work.lclpnet.ap2.impl.util.model.Models;
import work.lclpnet.ap2.impl.util.model.TemplateModel;
import work.lclpnet.gaco.ds.BlockBox;

import java.util.NoSuchElementException;
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
        var base = new BlockDisplayObject(scene, state);
        
        // base will be pointing in positive x direction, center in y and z plane
        base.position.set(0, -0.5, -0.5);

        var line = new Object3d(scene);
        line.addChild(base);

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
        var obj = new BlockDisplayObject(scene, state);

        BlockPos pos = box.min();
        obj.position.set(pos.getX(), pos.getY(), pos.getZ());
        obj.scale.set(box.width(), box.height(), box.length());

        display(obj);

        return obj;
    }

    public Object3d box(Box box, BlockState state) {
        var obj = new BlockDisplayObject(scene, state);

        obj.position.set(box.minX, box.minY, box.minZ);
        obj.scale.set(box.getLengthX(), box.getLengthY(), box.getLengthZ());

        display(obj);

        return obj;
    }

    private Model crossModel(BlockState state) {
        Model baseModel = modelManager.getModel(Models.CROSS).orElseThrow();
        return TemplateModel.replace(baseModel, Blocks.RED_CONCRETE.getDefaultState(), state);
    }

    private Model arrowModel(BlockState state) {
        Model baseModel = modelManager.getModel(Models.ARROW).orElseThrow();
        return TemplateModel.replace(baseModel, Blocks.LIME_CONCRETE.getDefaultState(), state);
    }

    public Object3d arrow(double x, double y, double z, double angleYRad, double scale, BlockState state) {
        return arrow(x, y, z, angleYRad, scale, arrowModel(state));
    }

    public Object3d arrow(double x, double y, double z, double angleYRad, double scale, Model model) {
        Object3d arrow = model.createInstance(scene);

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

        Object3d marker = model.createInstance(scene);
        marker.position.set(0, 0, 0.5);

        Object3d wrapper = new Object3d(scene);
        wrapper.addChild(marker);
        wrapper.scale.set(scale);
        wrapper.position.set(x, y, z);
        wrapper.rotation.rotateTo(0, 0, 1, dx, dy, dz);

        display(wrapper);
    }

    public Object3d marker(Vec3d pos, BlockState state, int glowColor) {
        return marker(pos.x, pos.y, pos.z, state, glowColor);
    }

    public Object3d marker(Vec3d pos, BlockState state, int glowColor, double scale) {
        return marker(pos.x, pos.y, pos.z, state, glowColor, scale);
    }

    public Object3d marker(double x, double y, double z, BlockState state, int glowColor) {
        return marker(x, y, z, state, glowColor, 0.25);
    }

    public Object3d marker(double x, double y, double z, BlockState state, int glowColor, double scale) {
        var marker = new BlockDisplayObject(scene, state);

        marker.position.set(-0.5, -0.5, -0.5);
        marker.setGlowing(true);
        marker.setGlowColorOverride(glowColor);
        marker.setInterpolationDuration(1);

        Object3d wrapper = new Object3d(scene);
        wrapper.position.set(x, y, z);
        wrapper.scale.set(scale);
        wrapper.addChild(marker);

        display(wrapper);

        return wrapper;
    }

    public TextDisplayObject text(Vec3d pos, Text text) {
        return text(pos, text, 0.25);
    }

    public TextDisplayObject text(double x, double y, double z, Text text) {
        return text(x, y, z, text, 0.25);
    }

    public TextDisplayObject text(Vec3d pos, Text text, double scale) {
        return text(pos.getX(), pos.getY(), pos.getZ(), text, scale);
    }

    public TextDisplayObject text(double x, double y, double z, Text text, double scale) {
        var display = new TextDisplayObject(scene, text);

        display.position.set(x, y, z);
        display.scale.set(scale);
        display.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        display.setBackground(0);

        display(display);

        return display;
    }

    public void quadStroke(double x1, double z1, double x2, double z2, double y, double thickness, BlockState color) {
        line(x1, y, z2, x2, y, z2, thickness, color);
        line(x1, y, z1, x2, y, z1, thickness, color);
        line(x2, y, z2, x2, y, z1, thickness, color);
        line(x1, y, z1, x1, y, z2, thickness, color);
    }

    public void model(Identifier modelId, double x, double y, double z, double scale) {
        Model model = modelManager.getModel(modelId).orElseThrow(() -> new NoSuchElementException("Unknown model: " + modelId));

        model(model, x, y, z, scale);
    }

    public void model(Model model, Position pos, double scale) {
        model(model, pos.getX(), pos.getY(), pos.getZ(), scale);
    }

    public void model(Model model, double x, double y, double z, double scale) {
        Object3d instance = model.createInstance(scene);
        instance.position.set(x, y, z);
        instance.scale.set(scale);

        display(instance);
    }

    public void labeledCross(double x, double y, double z, BlockState color, Text label) {
        Model model = crossModel(color);

        model(model, x, y, z, 0.75);
        text(x, y + 0.2, z, label);
    }
}
