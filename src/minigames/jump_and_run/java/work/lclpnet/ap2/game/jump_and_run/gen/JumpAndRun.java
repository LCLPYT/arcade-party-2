package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.jump_and_run.JumpAndRunModuleSchema;
import work.lclpnet.ap2.impl.map.schema.MapSchemaLoader;
import work.lclpnet.ap2.impl.util.world.SubWorldManager;
import work.lclpnet.gaco.asset.AssetPath;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.ds.Checkpoint;
import work.lclpnet.gaco.math.BlockFace;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class JumpAndRun {

    private final GameMap map;
    private final SubWorldManager subWorldManager;
    private final List<JumpModule> modules;
    private final MapSchemaLoader schemaLoader;
    private final MinecraftServer server;
    private final JumpAndRunSetup.Parts parts;
    private final JumpAndRunGenerator generator;
    private int moduleIndex = 0;
    private @Nullable ServerWorld world = null, prevWorld = null;
    private @Nullable JumpAndRunModuleSchema schema = null;
    private @Nullable Checkpoint generatedStartCheckpoint = null;
    private @Nullable Checkpoint generatedEndCheckpoint = null;
    private @Nullable BlockBox entranceBridgeBounds = null;
    private @Nullable Runnable reloadModuleCallback = null;

    public JumpAndRun(GameMap map, SubWorldManager subWorldManager, List<JumpModule> modules,
                      MapSchemaLoader schemaLoader, MinecraftServer server, JumpAndRunSetup.Parts parts,
                      JumpAndRunGenerator generator) {
        this.map = map;
        this.subWorldManager = subWorldManager;
        this.modules = new ArrayList<>(modules);
        this.schemaLoader = schemaLoader;
        this.server = server;
        this.parts = parts;
        this.generator = generator;
    }

    public List<JumpModule> modules() {
        return modules;
    }

    public int moduleIndex() {
        return moduleIndex;
    }

    public boolean isDone() {
        return moduleIndex >= modules.size();
    }

    public void setReloadModuleCallback(@Nullable Runnable reloadModuleCallback) {
        this.reloadModuleCallback = reloadModuleCallback;
    }

    public CompletableFuture<SubWorldManager.WorldWithData> loadModule() {
        JumpModule module = module();

        AssetPath path = AssetPath.of(map.getDescriptor().getMapPath())
                .resolve("modules")
                .resolve(module.path() + ".tar.xz");

        Identifier id = map.getDescriptor().getIdentifier().withSuffixedPath("/modules/" + module.path());

        return subWorldManager.loadWorldWithData(path, id).thenCompose(res -> {
            setCurrent(res);

            return server.submit(() -> {
                processWorld();
                return res;
            });
        });
    }

    private void setCurrent(SubWorldManager.WorldWithData res) {
        var schema = schemaLoader.load(res.data(), JumpAndRunModuleSchema.class);

        if (schema == null) {
            throw new IllegalStateException("Jump and run module schema could not be loaded");
        }

        schema.validate();

        this.schema = schema;
        prevWorld = world;
        world = res.world();
        entranceBridgeBounds = null;
        generatedStartCheckpoint = null;
        generatedEndCheckpoint = null;
    }

    private void processWorld() {
        var schema = schema();

        BlockFace entrance = schema.getEntrance();

        if (entrance != null) {
            placeEntrance(entrance);
        }

        BlockFace exit = schema.getExit();

        if (exit != null) {
            placeExit(exit);
        }
    }

    private void placeEntrance(BlockFace face) {
        var entrance = generator.getEntrance(parts, face);

        entrance.place(world);

        generatedStartCheckpoint = entrance.checkpoint();
        entranceBridgeBounds = entrance.checkpoint().bounds();
    }

    private void placeExit(BlockFace face) {
        var exit = generator.getExit(parts, face);

        exit.place(world);

        generatedEndCheckpoint = exit.checkpoint();
    }

    public JumpModule module() {
        return modules.get(this.moduleIndex);
    }

    public JumpAndRunModuleSchema schema() {
        return Objects.requireNonNull(schema);
    }

    public ServerWorld world() {
        return Objects.requireNonNull(world);
    }

    public Checkpoint startCheckpoint() {
        var schema = schema();

        Checkpoint customStart = schema.getStart();

        if (customStart != null) {
            return customStart;
        }

        Checkpoint generated = generatedStartCheckpoint;

        if (generated != null) {
            return generated;
        }

        throw new IllegalStateException("No custom start nor generated start checkpoint exists");
    }

    public Checkpoint endCheckpoint() {
        var schema = schema();

        Checkpoint customEnd = schema.getEnd();

        if (customEnd != null) {
            return customEnd;
        }

        Checkpoint generated = generatedEndCheckpoint;

        if (generated != null) {
            return generated;
        }

        throw new IllegalStateException("No custom end nor generated end checkpoint exists");
    }

    public CompletableFuture<Void> unloadPreviousModule() {
        ServerWorld world = prevWorld;

        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }

        prevWorld = null;

        return subWorldManager.unloadWorld(world);
    }

    public List<Checkpoint> checkpoints() {
        var schema = schema();

        var checkpoints = new ArrayList<Checkpoint>();

        checkpoints.add(startCheckpoint());
        checkpoints.addAll(schema.getCheckpoints());
        checkpoints.add(endCheckpoint());

        return checkpoints;
    }

    public PositionRotation spawn() {
        Checkpoint start = startCheckpoint();
        Vec3d pos = start.pos();

        return new PositionRotation(pos.getX(), pos.getY(), pos.getZ(), start.yaw(), start.pitch());
    }

    public List<BlockBox> startGates() {
        BlockBox bounds = entranceBridgeBounds;

        if (bounds != null) {
            return List.of(bounds);
        }

        return schema().getStartGates();
    }

    public void onModuleCompleted() {
        if (isDone()) return;

        JumpModule module = module();
        generator.pushModuleHistory(module);

        moduleIndex++;
    }

    public List<JumpModule> availableModules() {
        return parts.modules();
    }

    public void setModule(JumpModule module) {
        if (isDone() || reloadModuleCallback == null) return;

        modules.set(moduleIndex, module);
        reloadModuleCallback.run();
    }
}
