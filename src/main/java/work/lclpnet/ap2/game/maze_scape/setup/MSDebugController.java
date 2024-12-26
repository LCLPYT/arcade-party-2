package work.lclpnet.ap2.game.maze_scape.setup;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import work.lclpnet.ap2.api.util.model.Model;
import work.lclpnet.ap2.api.util.model.ModelManager;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.util.DebugRenderer;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.game.maze_scape.util.Passage;
import work.lclpnet.ap2.impl.scene.BlockDisplayObject;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.ServerWorldMountContext;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.ap2.impl.util.model.Models;
import work.lclpnet.ap2.impl.util.model.TemplateModel;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.*;

import static java.lang.Math.abs;

public class MSDebugController {
    private @Nullable Scene scene = null;
    private @Nullable Model spawnMarker = null, childMarker = null, passageMarker = null;
    private @Nullable ServerWorld world = null;
    private @Nullable DebugRenderer renderer = null;

    public void init(ModelManager modelManager, ServerWorld world) {
        this.world = world;

        scene = new Scene(new ServerWorldMountContext(world));
        renderer = new DebugRenderer(scene);

        spawnMarker = modelManager.getModel(Models.CROSS).orElseThrow();
        childMarker = modelManager.getModel(Models.ARROW).orElseThrow();
        passageMarker = Optional.of(childMarker)
                .map(model -> model instanceof TemplateModel m ? m : null)
                .map(m -> m.copy().replace(Blocks.LIME_CONCRETE.getDefaultState(), Blocks.LIGHT_BLUE_CONCRETE.getDefaultState()))
                .orElseThrow();
    }

    public void display(Object3d obj) {
        if (scene != null) {
            scene.add(obj);
        }
    }

    public void displayArrow(Model model, double x, double y, double z, double scale, double angleY) {
        Object3d marker = model.createInstance();
        marker.scale.set(scale);
        marker.position.set(x, y, z);
        marker.rotation.setAngleAxis(angleY, 0, 1, 0);

        display(marker);
    }

    public Object3d displayMarker(double x, double y, double z, BlockState state, int glowColor) {
        BlockDisplayObject marker = new BlockDisplayObject(state);

        marker.position.set(-0.5, -0.5, -0.5);
        marker.setGlowing(true);
        marker.setGlowColorOverride(glowColor);
        marker.setInterpolationDuration(1);

        Object3d wrapper = new Object3d();
        wrapper.position.set(x, y, z);
        wrapper.scale.set(0.25);
        wrapper.addChild(marker);

        display(wrapper);

        return wrapper;
    }

    public void visualizeSpawn(OrientedStructurePiece oriented) {
        Vec3d pos = oriented.spawn();

        if (pos == null) return;

        if (spawnMarker != null) {
            Object3d marker = spawnMarker.createInstance();
            marker.position.set(pos.x, pos.y, pos.z);
            marker.scale.set(0.75);

            display(marker);
        }

        var display = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
        display.setPosition(pos.add(0, 0.2, 0));

        var text = Text.literal(oriented.piece().name() + " r" + oriented.rotation());
        DisplayEntityAccess.setText(display, text);

        DisplayEntityAccess.setBillboardMode(display, DisplayEntity.BillboardMode.CENTER);
        DisplayEntityAccess.setBackground(display, 0);
        DisplayEntityAccess.setTransformation(display, new AffineTransformation(new Matrix4f().scale(0.3f)));

        if (world != null) {
            world.spawnEntity(display);
        }
    }

    public void visualizeGraphEdges(OrientedStructurePiece oriented) {
        var node = oriented.node();

        if (node == null || childMarker == null || passageMarker == null) return;

        var children = node.children();
        var connectors = oriented.connectors();

        if (!children.isEmpty()) {
            for (int i = 0; i < connectors.size(); i++) {
                var child = children.get(i);

                if (child == null) continue;

                Connector3 connector = connectors.get(i);
                BlockPos pos = connector.pos();
                Vec3i dir = connector.direction();
                double angleY = MathUtil.angleY(dir);

                displayArrow(childMarker,
                        pos.getX() + 0.5 - abs(dir.getZ()) * 0.25,
                        pos.getY() + 1,
                        pos.getZ() + 0.5 - abs(dir.getX()) * 0.25,
                        0.75, angleY);
            }
        }

        for (Connector3 connector : getOutgoingConnectors(node)) {
            BlockPos pos = connector.pos();
            Vec3i dir = connector.direction();
            double angleY = MathUtil.angleY(dir);

            displayArrow(passageMarker,
                    pos.getX() + 0.5 + abs(dir.getZ()) * 0.25,
                    pos.getY() + 1,
                    pos.getZ() + 0.5 + abs(dir.getX()) * 0.25,
                    0.75, angleY);
        }
    }

    private List<Connector3> getOutgoingConnectors(Node<Connector3, StructurePiece, OrientedStructurePiece> from) {
        OrientedStructurePiece oriented = from.oriented();

        if (oriented == null) {
            return List.of();
        }

        // collect all parent connectors from neighbour pieces
        Set<Connector3> neighbourConnectors = new HashSet<>();

        for (var neighbour : from.neighbours()) {
            if (neighbour == null) continue;

            OrientedStructurePiece adj = neighbour.oriented();

            if (adj == null) continue;

            neighbourConnectors.addAll(adj.connectors());

            Connector3 parentConnector = adj.parentConnector();

            if (parentConnector != null) {
                neighbourConnectors.add(parentConnector);
            }
        }

        // iterate over each connector and check if there is a connection via neighbours
        List<Connector3> connectors = new ArrayList<>(1);

        for (Connector3 connector : oriented.connectors()) {
            Connector3 opposing = connector.createOpposing();

            if (neighbourConnectors.contains(opposing)) {
                connectors.add(connector);
            }
        }

        // also check the parent connector
        Connector3 parentConnector = oriented.parentConnector();

        if (parentConnector != null) {
            Connector3 opposing = parentConnector.createOpposing();

            if (neighbourConnectors.contains(opposing)) {
                connectors.add(parentConnector);
            }
        }

        return connectors;
    }

    public void visualizePits(OrientedStructurePiece oriented) {
        if (world == null) return;

        Matrix3i mat = oriented.transformation();
        BlockPos pos = oriented.pos();

        for (BlockBox box : oriented.piece().pit().greedyMeshing().generateBoxes()) {
            box = box.transform(mat);

            var display = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
            display.setPosition(Vec3d.of(pos.add(box.min())));

            DisplayEntityAccess.setBlockState(display, Blocks.RED_STAINED_GLASS.getDefaultState());

            var scale = new Vector3f(box.width(), box.height(), box.length());
            DisplayEntityAccess.setTransformation(display, new AffineTransformation(null, null, scale, null));

            world.spawnEntity(display);
        }
    }

    public void visualizePassages(MSStruct struct) {
        if (renderer == null) return;

        var queue = new LinkedList<Passage>();
        var processed = new IntOpenHashSet();

        struct.passagesOf(struct.graph().root()).forEach(queue::offer);

        BlockState material = Blocks.BLUE_CONCRETE.getDefaultState();

        while (!queue.isEmpty()) {
            Passage passage = queue.poll();

            for (Passage neighbour : passage.neighbours()) {
                if (!processed.add(passage.hashCode() + neighbour.hashCode())) continue;

                queue.offer(neighbour);
                visualizePassage(neighbour, passage, material);
            }
        }
    }

    @Nullable
    public Object3d visualizePassage(Passage from, Passage to, BlockState material) {
        if (renderer == null) return null;

        return renderer.line(to.pos().toBottomCenterPos(), from.pos().toBottomCenterPos(), 0.03125, material);
    }

    public @Nullable Scene scene() {
        return scene;
    }

    public @Nullable DebugRenderer renderer() {
        return renderer;
    }
}
