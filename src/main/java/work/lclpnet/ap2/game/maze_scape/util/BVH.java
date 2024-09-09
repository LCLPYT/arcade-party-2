package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BVH {

    private final Node root;

    public BVH(List<BlockBox> objects) {
        this.root = build(objects);
    }

    private Node build(List<BlockBox> boxes) {
        if (boxes.size() == 1) {
            return new Node(boxes.getFirst());
        }

        var bounds = BlockBox.enclosing(boxes);

        // make list mutable
        boxes = new ArrayList<>(boxes);

        // sort boxes along the major axis
        var axis = bounds.majorAxis();
        boxes.sort(Comparator.comparingInt(box -> box.min().getComponentAlongAxis(axis)));

        // split list into halves and recursively build the tree
        int mid = boxes.size() / 2;

        var leftObjects = boxes.subList(0, mid);
        var rightObjects = boxes.subList(mid, boxes.size());

        Node leftChild = build(leftObjects);
        Node rightChild = build(rightObjects);

        return new Node(leftChild, rightChild);
    }

    public boolean intersects(BlockBox box) {
        return root.intersects(box);
    }

    private static class Node {
        final BlockBox bounds;
        final @Nullable Node left, right;

        Node(BlockBox bounds) {
            this.left = this.right = null;
            this.bounds = bounds;
        }

        Node(Node left, Node right) {
            this.left = left;
            this.right = right;
            this.bounds = new BlockBox(BlockPos.min(left.bounds.min(), right.bounds.min()),
                    BlockPos.max(left.bounds.max(), right.bounds.max()));
        }

        boolean intersects(BlockBox box) {
            if (!this.bounds.intersects(box)) return false;

            if (left == null || right == null) {
                return true;
            }

            return left.intersects(box) || right.intersects(box);
        }
    }
}
