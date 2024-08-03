package work.lclpnet.ap2.game.apocalypse_survival.util;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Matrix4f;
import work.lclpnet.ap2.mixin.EntityNavigationAccessor;
import work.lclpnet.ap2.mixin.PathNodeMakerAccessor;
import work.lclpnet.ap2.mixin.PathNodeNavigatorAccessor;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

public class PathDebugging {

    private final ServerWorld world;

    public PathDebugging(ServerWorld world) {
        this.world = world;
    }

    public void displayPath(Path path, MobEntity entity) {
        var navigation = entity.getNavigation();
        var navigator = ((EntityNavigationAccessor) navigation).getPathNodeNavigator();

        displayPath(path, navigator);
    }

    public void displayPath(Path path, PathNodeNavigator navigator) {
        if (path == null) {
            System.out.println("could not find path");
            return;
        }

        System.out.printf("target: %s reaches: %s distance: %s%n", path.getTarget(), path.reachesTarget(), path.getManhattanDistanceFromTarget());

        IntSet marked = new IntArraySet(path.getLength());

        for (int i = 0; i < path.getLength(); i++) {
            PathNode node = path.getNode(i);
            marked.add(node.hashCode());

            displayPathNode(node, Blocks.LIME_CONCRETE.getDefaultState());
        }

        var navigatorAccess = (PathNodeNavigatorAccessor) navigator;
        var maker = navigatorAccess.getPathNodeMaker();

        if (maker == null) return;

        var makerAccess = (PathNodeMakerAccessor) maker;

        for (PathNode node : makerAccess.getPathNodeCache().values()) {
            if (marked.contains(node.hashCode())) continue;

            displayPathNode(node, Blocks.LIGHT_BLUE_CONCRETE.getDefaultState());
        }
    }

    public void displayPathNode(PathNode node, BlockState state) {
        var display = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        display.setPosition(node.getPos().add(.25, .25, .25));
        DisplayEntityAccess.setBlockState(display, state);
        DisplayEntityAccess.setTransformation(display, new AffineTransformation(new Matrix4f().scale(.5f)));

        world.spawnEntity(display);
    }
}
