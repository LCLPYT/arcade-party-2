package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.Orientation;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.gen.Graph;
import work.lclpnet.ap2.game.maze_scape.gen.GraphGenerator;
import work.lclpnet.ap2.game.maze_scape.gen.GraphGenerator.Result;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.impl.util.FutureUtil;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.ap2.impl.util.world.ResetBlockWorldModifier;
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

import static work.lclpnet.ap2.game.maze_scape.gen.GraphGenerator.ResultType.FAILURE;
import static work.lclpnet.ap2.game.maze_scape.gen.GraphGenerator.ResultType.INTERRUPTED;
import static work.lclpnet.kibu.util.StructureWriter.Option.*;

public class MSGenerator {

    private static final EnumSet<StructureWriter.Option> UPDATE_NEIGHBOURS_OPTS = EnumSet.of(SKIP_AIR, FORCE_STATE, SKIP_PLAYER_SYNC, SKIP_DROPS);
    public static final int PLACE_FLAGS = Block.FORCE_STATE | Block.SKIP_DROPS;
    public static final boolean DEBUG = false;
    private static final int GENERATOR_MAX_TRIES = 5;
    private static final int GENERATOR_MAX_DURATION_MS = 15_000;
    private final ServerWorld world;
    private final GameMap map;
    private final MSLoader.Result loaded;
    private final Random random;
    private final long seed;
    private final Logger logger;
    private final Map<Connector3, ResetBlockWorldModifier> closedConnectors = new HashMap<>();
    private final int targetArea;
    private final float deadEndChance;
    private final StructureDomain domain;
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
        deadEndChance = Math.max(0, Math.min(1, deadEndChanceProp.floatValue()));

        // this will determine the bounding box that the generated pieces must be generated in
        Number maxChunkSizeProp = map.requireProperty("max-chunk-size");
        int maxChunkSize = Math.max(maxChunkSizeProp.intValue(), 2);

        int bottomY = world.getBottomY();
        int topY = world.getTopY() - 1;

        var bounds = new StructureDomain.BoundsCfg(maxChunkSize, bottomY, topY);
        domain = new StructureDomain(loaded.pieces(), random, deadEndStart, bounds);
    }

    private void placeAdditionalDeadEnds(Graph<Connector3, StructurePiece, OrientedStructurePiece> graph) {
        domain.setOnlyDeadEnds(true);

        for (var node : graph.openNodes()) {
            var oriented = node.oriented();

            if (oriented == null) continue;

            var connectors = oriented.connectors();
            var children = node.children();
            int size = connectors.size();

            // place a dead end at open connectors with a certain chance
            for (int i = 0; i < size; i++) {
                if ((children != null && children.get(i) != null) || random.nextFloat() >= deadEndChance) continue;

                appendDeadEnd(oriented, i, node);
            }
        }
    }

    private void appendDeadEnd(OrientedStructurePiece oriented, int connectorIndex,
                               Graph.Node<Connector3, StructurePiece, OrientedStructurePiece> node) {

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

    private boolean placeNode(Graph.Node<Connector3, StructurePiece, OrientedStructurePiece> node) {
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

        return true;
    }

    private void replaceJigsaws(OrientedStructurePiece oriented) {
        Matrix3i mat = oriented.transformation();
        var transformation = new AffineIntMatrix(mat, oriented.pos());

        StructurePiece piece = oriented.piece();
        BlockStructure struct = piece.wrapper().getStructure();

        var placedPos = new BlockPos.Mutable();
        var kibuPos = new KibuBlockPos.Mutable();

        for (Connector3 connector : piece.connectors()) {
            BlockPos pos = connector.pos();
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

    private void closeConnectors(Graph.Node<Connector3, StructurePiece, OrientedStructurePiece> node, OrientedStructurePiece oriented) {
        var connectors = oriented.connectors();
        var children = node.children();
        int size = connectors.size();

        if (children == null || children.size() != size) {
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
        Orientation orientation = connector.orientation();
        Direction facing = orientation.getFacing();

        Orientation opposingOrientation = Orientation.byDirections(facing.getOpposite(), orientation.getRotation());

        if (opposingOrientation == null) return;

        BlockPos opposingPos = connector.pos().add(facing.getVector());

        // target and name must be inverted for the opposing connector
        var opposing = new Connector3(opposingPos, opposingOrientation, connector.target(), connector.name());

        var wall = closedConnectors.get(opposing);

        if (wall != null) {
            // the opposing connector was closed, remove the wall and link the two rooms
            wall.undo();
            closedConnectors.remove(opposing);
            // TODO link nodes in graph
            return;
        }

        // close the connector
        ResetBlockWorldModifier modifier = new ResetBlockWorldModifier(world, PLACE_FLAGS);

        loaded.defaultConnectorWall().place(connector, oriented, modifier, random);

        closedConnectors.put(connector, modifier);
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

                if (DEBUG) {
                    logger.error(msg);
                } else {
                    future.completeExceptionally(new IllegalStateException(msg));
                    return;
                }
            } else if (type == FAILURE) {
                logger.error("Failed to generate structure. Map: {}, Seed: {}; Retries remaining: {}", map.getDescriptor(), seed, GENERATOR_MAX_TRIES - i - 1);

                // accept result regardless in debug mode
                if (!DEBUG) continue;
            }

            future.complete(res);
            return;
        }

        future.completeExceptionally(new IllegalStateException("Failed to generate structure. Maximum number of re-tries has been exceeded"));
    }
}
