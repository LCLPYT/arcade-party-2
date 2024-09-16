package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BVH {

    public static final BVH EMPTY = new BVH(null);
    @VisibleForTesting @Nullable
    final Node root;

    private BVH(@Nullable Node root) {
        this.root = root;
    }

    public static BVH build(List<BlockBox> boxes) {
        if (boxes.isEmpty()) {
            return EMPTY;
        }

        return new BVH(buildNode(boxes));
    }

    private static Node buildNode(List<BlockBox> boxes) {
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

        Node leftChild = buildNode(leftObjects);
        Node rightChild = buildNode(rightObjects);

        return new Node(leftChild, rightChild);
    }

    public boolean intersects(BlockBox box) {
        return root != null && root.intersects(box);
    }

    public boolean intersects(BVH other) {
        return this.root != null && other.root != null && this.root.intersects(other.root);
    }

    @Nullable
    public BlockBox box() {
        return root != null ? root.bounds : null;
    }

    public int width() {
        return root != null ? root.bounds.width() : 0;
    }

    public int height() {
        return root != null ? root.bounds.height() : 0;
    }

    public int length() {
        return root != null ? root.bounds.length() : 0;
    }

    public BVH transform(AffineIntMatrix mat) {
        if (root == null) return EMPTY;

        return new BVH(root.transform(mat));
    }

    @VisibleForTesting
    static class Node {
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
            if (!this.bounds.intersects(box)) {
                return false;
            }

            if (left == null || right == null) {
                return true;
            }

            return left.intersects(box) || right.intersects(box);
        }

        boolean intersects(Node other) {
            // if node bounds to not intersect, they are not colliding
            if (!this.bounds.intersects(other.bounds)) {
                return false;
            }

            boolean thisLeaf = this.isLeaf();
            boolean otherLeaf = other.isLeaf();

            // if both nodes are leafs, an intersection is detected
            if (thisLeaf && otherLeaf) {
                return true;
            }

            if (thisLeaf) {
                return leafCollides(other);
            }

            if (otherLeaf) {
                return other.leafCollides(this);
            }

            // both nodes are not leafs, check all child combinations: LL, LR, RL, RR
            // first check left combinations
            if (this.left != null) {
                if (other.left != null && this.left.intersects(other.left)) {
                    return true;
                }

                if (other.right != null && this.left.intersects(other.right)) {
                    return true;
                }
            }

            // now check right combinations
            if (this.right == null) {
                return false;
            }

            if (other.left != null && this.right.intersects(other.left)) {
                return true;
            }

            return other.right != null && this.right.intersects(other.right);
        }

        boolean leafCollides(Node other) {
            // assumes this is a leaf node and other is not
            return other.left != null && this.bounds.intersects(other.left.bounds) ||
                   other.right != null && this.bounds.intersects(other.right.bounds);

        }

        boolean isLeaf() {
            return this.left == null || this.right == null;
        }

        public Node transform(AffineIntMatrix mat) {
            if (left == null || right == null) {
                return new Node(bounds.transform(mat));
            }

            return new Node(left.transform(mat), right.transform(mat));
        }
    }
}
