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
import work.lclpnet.ap2.api.util.model.Model;
import work.lclpnet.ap2.api.util.model.ModelManager;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.game.maze_scape.util.Passage;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.ap2.impl.util.model.Models;
import work.lclpnet.ap2.impl.util.model.TemplateModel;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

import java.util.*;

import static java.lang.Math.abs;

public class MSDebugController {

    private final DebugController parent = new DebugController();
    private @Nullable Model spawnMarker = null, childMarker = null, passageMarker = null;
    private @Nullable ServerWorld world = null;

    public DebugController parent() {
        return parent;
    }

    public void init(ModelManager modelManager, ServerWorld world) {
        parent.init(modelManager, world);

        this.world = world;

        spawnMarker = modelManager.getModel(Models.CROSS).orElseThrow();
        Model arrow = modelManager.getModel(Models.ARROW).orElseThrow();
        childMarker = arrow;
        passageMarker = TemplateModel.replace(arrow, Blocks.LIME_CONCRETE.getDefaultState(), Blocks.LIGHT_BLUE_CONCRETE.getDefaultState());
    }

    public void visualizeSpawn(OrientedStructurePiece oriented) {
        Vec3d pos = oriented.spawn();

        if (pos == null) return;

        if (spawnMarker != null) {
            Object3d marker = spawnMarker.createInstance();
            marker.position.set(pos.x, pos.y, pos.z);
            marker.scale.set(0.75);

            parent.renderer().ifPresent(renderer -> renderer.display(marker));
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

                parent.renderer().ifPresent(renderer -> renderer.arrow(
                        pos.getX() + 0.5 - abs(dir.getZ()) * 0.25,
                        pos.getY() + 1,
                        pos.getZ() + 0.5 - abs(dir.getX()) * 0.25,
                        angleY, 0.75,
                        childMarker));
            }
        }

        for (Connector3 connector : getOutgoingConnectors(node)) {
            BlockPos pos = connector.pos();
            Vec3i dir = connector.direction();
            double angleY = MathUtil.angleY(dir);

            parent().renderer().ifPresent(renderer -> renderer.arrow(
                    pos.getX() + 0.5 + abs(dir.getZ()) * 0.25,
                    pos.getY() + 1,
                    pos.getZ() + 0.5 + abs(dir.getX()) * 0.25,
                    angleY, 0.75,
                    passageMarker));
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

    public void visualizePassages(MSStruct struct) {
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
        return parent.renderer()
                .map(renderer -> renderer.line(to.pos().toBottomCenterPos(), from.pos().toBottomCenterPos(), 0.03125, material))
                .orElse(null);
    }
}
