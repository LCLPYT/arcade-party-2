package work.lclpnet.ap2.impl.util.structure;

import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3i;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.util.Printable;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.world.MappedBlockStructure;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.SchematicFormats;
import work.lclpnet.kibu.schematic.api.Cuboid;
import work.lclpnet.kibu.schematic.api.SchematicReader;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.StructureWriter;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static work.lclpnet.kibu.util.StructureWriter.Option.*;

public class StructureUtil {

    private static final EnumSet<StructureWriter.Option> FAST_NO_VIEWERS = EnumSet.of(SKIP_AIR, FORCE_STATE, SKIP_PLAYER_SYNC, SKIP_DROPS, SKIP_NEIGHBOUR_UPDATE);

    private StructureUtil() {}

    public static void placeStructureFast(Printable printable, ServerWorld world) {
        BlockStructure structure = printable.structure();
        Vec3i pos = printable.printOffset();

        StructureWriter.placeStructure(structure, world, pos, printable.printMatrix(), FAST_NO_VIEWERS);
    }

    public static void placeStructureFast(BlockStructure structure, ServerWorld world, Vec3i pos) {
        placeStructureFast(structure, world, pos, Matrix3i.IDENTITY);
    }

    public static void placeStructureFast(BlockStructure structure, ServerWorld world, Vec3i pos, Matrix3i transformation) {
        StructureWriter.placeStructure(structure, world, pos, transformation, FAST_NO_VIEWERS);
    }

    public static BlockBox getBounds(Cuboid structure) {
        return new BlockBox(0, 0, 0,
                structure.getWidth() - 1, structure.getHeight() - 1, structure.getLength() - 1);
    }

    public static BlockStructure map(BlockStructure structure, UnaryOperator<BlockState> mapper) {
        var adapter = FabricBlockStateAdapter.getInstance();

        return new MappedBlockStructure(structure, state -> adapter.adapt(mapper.apply(adapter.revert(state))));
    }

    public static BlockStructure replace(BlockStructure structure, BlockState target, BlockState replacement) {
        return map(structure, state -> state == target ? replacement : state);
    }

    public static Optional<BlockStructure> readAndFixStructure(Path path, Logger logger, RegistryWrapper.WrapperLookup registries) {
        return readStructure(path, logger)
                .map(structure -> new StructureFix(registries, logger).fixStructure(structure));
    }

    public static Optional<BlockStructure> readStructure(Path path, Logger logger) {
        SchematicReader reader = SchematicFormats.SPONGE_V2.reader();
        var adapter = FabricBlockStateAdapter.getInstance();

        try (var in = Files.newInputStream(path)) {
            BlockStructure structure = reader.read(in, adapter);
            return Optional.of(structure);
        } catch (IOException e) {
            logger.error("Failed to read schematic {}", path, e);
            return Optional.empty();
        }
    }
}
