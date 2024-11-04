package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.util.model.Model;
import work.lclpnet.ap2.api.util.model.ModelManager;
import work.lclpnet.ap2.base.resource.ApResources;
import work.lclpnet.ap2.game.maze_scape.gen.Graph;
import work.lclpnet.ap2.game.maze_scape.gen.GraphGenerator;
import work.lclpnet.ap2.game.maze_scape.gen.GraphGenerator.Result;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.util.FutureUtil;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.ap2.impl.util.model.Models;
import work.lclpnet.ap2.impl.util.model.TemplateModel;
import work.lclpnet.ap2.impl.util.world.ResetBlockWorldModifier;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;
import work.lclpnet.kibu.jnbt.CompoundTag;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.BlockStateUtils;
import work.lclpnet.kibu.util.RotationUtil;
import work.lclpnet.kibu.util.StructureWriter;
import work.lclpnet.kibu.util.math.Matrix3i;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.Math.min;
import static work.lclpnet.ap2.game.maze_scape.gen.GraphGenerator.ResultType.FAILURE;
import static work.lclpnet.ap2.game.maze_scape.gen.GraphGenerator.ResultType.INTERRUPTED;
import static work.lclpnet.kibu.util.StructureWriter.Option.*;

public class MSGenerator {

    private static final EnumSet<StructureWriter.Option> UPDATE_NEIGHBOURS_OPTS = EnumSet.of(SKIP_AIR, FORCE_STATE, SKIP_PLAYER_SYNC, SKIP_DROPS);
    public static final int PLACE_FLAGS = Block.FORCE_STATE | Block.SKIP_DROPS;
    public static final boolean
            DEBUG_GENERATOR = false,
            DEBUG_SPAWNS = true,
            DEBUG_GRAPH = true;
    private static final int GENERATOR_MAX_TRIES = 5;
    private static final int GENERATOR_MAX_DURATION_MS = 15_000;
    private final ServerWorld world;
    private final GameMap map;
    private final MSLoader.Result loaded;
    private final Random random;
    private final long seed;
    private final Logger logger;
    private final Map<Connector3, ClosedConnector> closedConnectors = new HashMap<>();
    private final int targetArea;
    private final float deadEndChance;
    private final StructureDomain domain;
    private final DebugController debugger = new DebugController();
    private GraphGenerator<Connector3, StructurePiece, OrientedStructurePiece> generator;
    private boolean decorate = true;

    public MSGenerator(ServerWorld world, GameMap map, MSLoader.Result loaded, Random random, long seed, Logger logger) {
        this.world = world;
        this.map = map;
        this.loaded = loaded;
        this.random = random;
        this.seed = seed;
        this.logger = logger;

        // the generator will continue until the total room area is greater than this value
        targetArea = ((Number) map.requireProperty("target-room-area")).intValue();

        // the generator will try to only put dead-ends after this many rooms on the path towards the start room
        int deadEndStart = ((Number) map.requireProperty("dead-end-start-level")).intValue();

        // chance to append a dead-end piece to an open connector instead of closing it
        Number deadEndChanceProp = map.requireProperty("dead-end-chance");
        deadEndChance = Math.max(0, min(1, deadEndChanceProp.floatValue()));

        // this will determine the bounding box that the generated pieces must be generated in
        int maxChunkSize = getMaxChunkSize(map);

        int bottomY = world.getBottomY();
        int topY = world.getTopY() - 1;

        var bounds = new StructureDomain.BoundsCfg(maxChunkSize, bottomY, topY);
        domain = new StructureDomain(loaded.pieces(), random, deadEndStart, bounds);

        ModelManager modelManager = ApResources.getInstance();

        if (DEBUG_SPAWNS) {
            debugger.enable(world);
            debugger.spawnMarker = modelManager.getModel(Models.CROSS).orElseThrow();
        }

        if (DEBUG_GRAPH) {
            debugger.enable(world);
            debugger.childMarker = modelManager.getModel(Models.ARROW).orElseThrow();
            debugger.passageMarker = Optional.of(debugger.childMarker)
                    .map(model -> model instanceof TemplateModel m ? m : null)
                    .map(m -> m.copy().replace(Blocks.LIME_CONCRETE.getDefaultState(), Blocks.LIGHT_BLUE_CONCRETE.getDefaultState()))
                    .orElseThrow();
        }
    }

    public static int getMaxChunkSize(GameMap map) {
        Number maxChunkSizeProp = map.requireProperty("max-chunk-size");

        return Math.max(maxChunkSizeProp.intValue(), 2);
    }

    private void placeAdditionalDeadEnds(Graph<Connector3, StructurePiece, OrientedStructurePiece> graph) {
        domain.setOnlyDeadEnds(true);

        for (var node : graph.openNodes()) {
            var oriented = node.oriented();

            if (oriented == null) continue;

            var connectors = oriented.connectors();
            var children = node.children();
            int size = min(connectors.size(), children.size());

            // place a dead end at open connectors with a certain chance
            for (int i = 0; i < size; i++) {
                if (children.get(i) != null || random.nextFloat() >= deadEndChance) continue;

                appendDeadEnd(oriented, i, node);
            }
        }
    }

    private void appendDeadEnd(OrientedStructurePiece oriented, int connectorIndex,
                               Node<Connector3, StructurePiece, OrientedStructurePiece> node) {

        var connectors = oriented.connectors();
        generator.initChildren(node, connectors.size());

        var connector = connectors.get(connectorIndex);
        var fitting = domain.fittingPieces(oriented, connector, node);

        if (fitting.isEmpty()) return;

        generator.placeRandomChildPiece(node, connectorIndex, fitting);
    }

    private void placePieces(Graph<Connector3, StructurePiece, OrientedStructurePiece> graph) {
        graph.root().traverse(this::placeNode);
    }

    private boolean placeNode(Node<Connector3, StructurePiece, OrientedStructurePiece> node) {
        OrientedStructurePiece oriented = node.oriented();

        if (oriented == null) {
            logger.error("No piece for node at level {}", node.level());
            return false;
        }

        StructurePiece piece = oriented.piece();
        FabricStructureWrapper wrapper = piece.wrapper();
        BlockStructure struct = wrapper.getStructure();
        BlockPos pos = oriented.pos();
        Matrix3i transformation = oriented.transformation();

        if (piece.updateBlocks()) {
            StructureWriter.placeStructure(struct, world, pos, transformation, UPDATE_NEIGHBOURS_OPTS);
        } else {
            StructureUtil.placeStructureFast(struct, world, pos, transformation);
        }

        if (decorate) {
            replaceJigsaws(oriented);

            closeConnectors(node, oriented);
        }

        if (DEBUG_SPAWNS) {
            debugger.visualizeSpawn(oriented);
        }

        if (DEBUG_GRAPH) {
            debugger.visualizeGraphEdges(oriented);
        }

        return true;
    }

    private void replaceJigsaws(OrientedStructurePiece oriented) {
        Matrix3i mat = oriented.transformation();
        var transformation = new AffineIntMatrix(mat, oriented.pos());

        StructurePiece piece = oriented.piece();
        BlockStructure struct = piece.wrapper().getStructure();

        var placedPos = new BlockPos.Mutable();
        var kibuPos = new KibuBlockPos.Mutable();

        for (BlockPos pos : piece.jigsaws()) {
            kibuPos.set(pos.getX(), pos.getY(), pos.getZ());

            // determine jigsaw final state
            var blockEntity = struct.getBlockEntity(kibuPos);

            if (blockEntity == null) {
                logger.warn("Could not find jigsaw block entity in structure at position {}", pos);
                continue;
            }

            CompoundTag nbt = blockEntity.createNbt();
            String str = nbt.getString("final_state");

            if (str.isEmpty()) continue;

            BlockState state = BlockStateUtils.parse(str);

            if (state == null) {
                logger.warn("Unknown block state {} as jigsaw final state", str);
                continue;
            }

            // apply rotation to state
            state = RotationUtil.rotate(state, mat);

            // determine actual location
            transformation.transform(pos.getX(), pos.getY(), pos.getZ(), placedPos);

            world.setBlockState(placedPos, state, PLACE_FLAGS);
        }
    }

    private void closeConnectors(Node<Connector3, StructurePiece, OrientedStructurePiece> node, OrientedStructurePiece oriented) {
        var connectors = oriented.connectors();
        var children = node.children();
        int size = connectors.size();

        if (children.size() != size) {
            // close all connectors
            for (Connector3 connector : connectors) {
                handleCloseConnector(oriented, connector);
            }
            return;
        }

        // close only open connectors
        for (int i = 0; i < size; i++) {
            var child = children.get(i);

            if (child != null) continue;

            Connector3 connector = connectors.get(i);

            handleCloseConnector(oriented, connector);
        }
    }

    private void handleCloseConnector(OrientedStructurePiece oriented, Connector3 connector) {
        // check if there is another piece, perfectly aligned with the current connector by any chance
        var opposing = connector.createOpposing();

        if (opposing == null) return;

        var wall = closedConnectors.get(opposing);

        if (wall != null) {
            // the opposing connector was closed, remove the wall and link the two rooms
            wall.modifier().undo();

            closedConnectors.remove(opposing);

            var node = oriented.node();
            var other = wall.oriented();
            var otherNode = other.node();

            if (node != null && otherNode != null) {
                node.addConnection(otherNode);
                otherNode.addConnection(node);
            }

            return;
        }

        // close the connector
        ResetBlockWorldModifier modifier = new ResetBlockWorldModifier(world, PLACE_FLAGS);

        loaded.defaultConnectorWall().place(connector, oriented, modifier, random);

        closedConnectors.put(connector, new ClosedConnector(oriented, modifier));
    }

    public CompletableFuture<Optional<MSStruct>> startGenerator() {
        generator = new GraphGenerator<>(domain, random, logger);

        var future = new CompletableFuture<Result<Connector3, StructurePiece, OrientedStructurePiece>>();

        // dispatch structure generation in a separate thread
        var generatorThread = Thread.ofPlatform().name("Structure Generator").unstarted(() -> tryGenerate(future));

        // also dispatch a watchdog thread that interrupts the generator thread after the maximum duration
        Thread.ofVirtual().name("Structure Generator Watchdog").start(() -> {
            logger.info("Starting structure generator thread with a maximum duration of {} ms", GENERATOR_MAX_DURATION_MS);
            generatorThread.start();

            try {
                generatorThread.join(GENERATOR_MAX_DURATION_MS);
            } catch (InterruptedException ignored) {} finally {
                if (generatorThread.isAlive()) {
                    logger.error("Generator thread is taking too long. Interrupting it...");
                    generator.interrupt();
                    generatorThread.interrupt();
                }
            }
        });

        // finally place structure on the server thread
        return future.thenCompose(FutureUtil.onThread(world.getServer(), res -> {
            decorate = res.success();
            var graph = res.graph();

            if (decorate) {
                placeAdditionalDeadEnds(graph);
            }

            placePieces(graph);

            return res.optional().map(MSStruct::new);
        }));
    }

    private void tryGenerate(CompletableFuture<Result<Connector3, StructurePiece, OrientedStructurePiece>> future) {
        for (int i = 0; i < GENERATOR_MAX_TRIES; i++) {
            domain.reset();

            var res = generator.generateGraph(loaded.startPiece(), g -> domain.totalArea() < targetArea);
            var type = res.type();

            if (type == INTERRUPTED) {
                String msg = "Failed to generate structure. Generation took too long";

                if (DEBUG_GENERATOR) {
                    logger.error(msg);
                } else {
                    future.completeExceptionally(new IllegalStateException(msg));
                    return;
                }
            } else if (type == FAILURE) {
                logger.error("Failed to generate structure. Map: {}, Seed: {}; Retries remaining: {}", map.getDescriptor(), seed, GENERATOR_MAX_TRIES - i - 1);

                // accept result regardless in debug mode
                if (!DEBUG_GENERATOR) continue;
            }

            future.complete(res);
            return;
        }

        future.completeExceptionally(new IllegalStateException("Failed to generate structure. Maximum number of re-tries has been exceeded"));
    }

    private record ClosedConnector(OrientedStructurePiece oriented, ResetBlockWorldModifier modifier) {}

    private static class DebugController {
        private @Nullable Scene scene = null;
        private @Nullable Model spawnMarker = null, childMarker = null, passageMarker = null;
        private @Nullable ServerWorld world = null;

        public void display(Object3d obj) {
            if (scene != null) {
                scene.add(obj);
            }
        }

        public void enable(ServerWorld world) {
            if (scene == null) {
                scene = new Scene(world);
                this.world = world;
            }
        }

        public void displayArrow(Model model, double x, double y, double z, double scale, double angleY) {
            Object3d marker = model.createInstance();
            marker.scale.set(scale);
            marker.position.set(x, y, z);
            marker.rotation.setAngleAxis(angleY, 0, 1, 0);

            display(marker);
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

        private void visualizeGraphEdges(OrientedStructurePiece oriented) {
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
                    double angleY = MathUtil.angleY(connector.direction());

                    displayArrow(childMarker, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0.75, angleY);
                }
            }

            for (Connector3 connector : getOutgoingConnectors(node)) {
                BlockPos pos = connector.pos();
                double angleY = MathUtil.angleY(connector.direction());

                displayArrow(passageMarker, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0.75, angleY);
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
                neighbourConnectors.add(adj.parentConnector());
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
    }
}
