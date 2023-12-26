package work.lclpnet.ap2.game.jump_and_run;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.collision.ChunkedCollisionDetector;
import work.lclpnet.ap2.impl.util.collision.StackCollisionDetector;
import work.lclpnet.ap2.impl.util.math.Matrix3i;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.*;

public class JumpAndRunGenerator {

    private final float maxTotal;
    private final Random random;

    public JumpAndRunGenerator(float maxTotal, Random random) {
        this.maxTotal = maxTotal;
        this.random = random;
    }

    public JumpAndRun generate(JumpAndRunSetup.Parts parts, BlockPos spawnPos) {
        StackCollisionDetector shape = new StackCollisionDetector(new ChunkedCollisionDetector());

        OrientedPart start = createStart(parts, spawnPos);
        shape.add(start.getBounds());

        List<OrientedPart> rooms = createRooms(parts, shape);

        OrientedPart lastPart = rooms.isEmpty() ? start : rooms.get(rooms.size() - 1);
        Connector lastExit = lastPart.getOut();
        if (lastExit == null) throw new IllegalStateException("Exit of previous part not found");

        List<OrientedPart> combined = new ArrayList<>();
        combined.add(start);
        combined.addAll(rooms);

        return new JumpAndRun(combined);
    }

    @NotNull
    private List<OrientedPart> createRooms(JumpAndRunSetup.Parts parts, StackCollisionDetector shape) {
        final int maxTries = 10;

        for (int i = 0; i < maxTries; i++) {
            var selection = getRandomRooms(parts.rooms());

            var rooms = createRoomsFromSelection(selection, shape);

            if (rooms != null) {
                return rooms;
            }
        }

        // TODO end room must be included in case the generation failed

        return List.of();
    }

    @Nullable
    private List<OrientedPart> createRoomsFromSelection(List<JumpRoom> rooms, StackCollisionDetector shape) {
        rooms.sort(Comparator.comparing(JumpRoom::getValue));

        var built = buildRooms(rooms, shape);

        if (built != null) return built;

        final int maxTries = 10;

        for (int i = 0; i < maxTries; i++) {
            Collections.shuffle(rooms);

            built = buildRooms(rooms, shape);

            if (built != null) return built;
        }

        return null;
    }

    @Nullable
    private List<OrientedPart> buildRooms(List<JumpRoom> rooms, StackCollisionDetector shape) {
        shape.push();

        // TODO try to join rooms at their connectors and return parts if successful. also consider the end room

        shape.pop();
        return null;
    }

    private List<JumpRoom> getRandomRooms(Set<JumpRoom> parts) {
        List<JumpRoom> open = new ArrayList<>(parts);
        List<JumpRoom> selection = new ArrayList<>(4);  // estimated 1.2 min per room

        float total = 0f;

        while (!open.isEmpty() && total < maxTotal) {
            int index = random.nextInt(open.size());
            JumpRoom room = open.remove(index);
            selection.add(room);

            total += room.getValue();
        }

        return selection;
    }

    @NotNull
    private OrientedPart createStart(JumpAndRunSetup.Parts parts, BlockPos spawnPos) {
        JumpEnd startRoom = parts.start();
        BlockStructure startStruct = startRoom.getStructure();

        int rotation = startRoom.getExit().direction().getHorizontal();
        Matrix3i rotationMatrix = Matrix3i.makeRotationY(rotation);

        BlockPos spawn = Objects.requireNonNull(startRoom.getSpawn(), "Spawn position must be non-null");
        Vec3i rotatedSpawn = rotationMatrix.transform(spawn);
        BlockPos startOffset = spawnPos.subtract(rotatedSpawn);

        return new OrientedPart(startStruct, startOffset, rotation, null, startRoom.getExit());
    }
}
