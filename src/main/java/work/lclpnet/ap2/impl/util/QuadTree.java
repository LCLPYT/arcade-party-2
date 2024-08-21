package work.lclpnet.ap2.impl.util;

import com.google.common.collect.Iterators;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;
import java.util.function.Function;

public class QuadTree<T> {

    private final int nodeCapacity;
    private final Function<T, Vec3d> posProvider;
    private final Map<T, Entry> entryMap = new HashMap<>();
    final @VisibleForTesting Node root;

    public QuadTree(double x, double z, double width, double length, int nodeCapacity, Function<T, Vec3d> posProvider) {
        if (nodeCapacity < 1) throw new IllegalArgumentException("Node capacity must be at least one");
        if (width < 0) throw new IllegalArgumentException("Width must be positive");
        if (length < 0) throw new IllegalArgumentException("Length must be positive");

        this.root = new Node(x, z, width, length, 0);
        this.nodeCapacity = nodeCapacity;
        this.posProvider = posProvider;
    }

    public boolean add(T element) {
        if (entryMap.containsKey(element)) {
            // already added, but update the element
            update(element);
            return false;
        }

        Vec3d pos = posProvider.apply(element);

        if (root.outOfBounds(pos)) {
            // can't insert out-of-bounds
            return false;
        }

        // find node for insertion
        Node node = root;

        while (!node.hasCapacity()) {
            node.divide();
            node = node.childAt(pos);
        }

        // found node with capacity
        Entry entry = new Entry(element, pos);
        entryMap.put(element, entry);

        return node.add(entry);
    }

    public boolean remove(T element) {
        Entry entry = entryMap.remove(element);

        if (entry == null) return false;

        if (root.outOfBounds(entry.pos)) {
            // can't insert out-of-bounds
            return false;
        }

        // find node for removal
        Node parent = null;
        Node node = root;

        while (node.divided) {
            parent = node;
            node = node.childAt(entry.pos);
        }

        // now remove the element from it
        if (!node.remove(entry)) {
            // node didn't contain the element
            return false;
        }

        // merge parent if possible
        if (parent != null) {
            parent.merge();
        }

        return true;
    }

    /**
     * Checks if a given element has moved enough so that it needs to be relocated in the tree.
     * If the elements can move dynamically, this method should be called for every moved element.
     * @param element The element that should be updated, e.g. when the element changed position.
     */
    public void update(T element) {
        Entry entry = entryMap.get(element);

        if (entry == null) return;

        // check if the element is still in the same node
        Vec3d oldPos = entry.pos;
        Vec3d newPos = posProvider.apply(element);

        // search for the containing node
        Node oldNode = root;
        Node newNode = root;

        while (oldNode.divided) {
            oldNode = oldNode.childAt(oldPos);
        }

        while (newNode.divided) {
            newNode = newNode.childAt(newPos);
        }

        if (oldNode == newNode) {
            entry.pos = newPos;
            return;
        }

        // re-insert element
        remove(element);
        add(element);
    }

    public void traverse(Visitor<T> visitor) {
        Stack<Node> stack = new Stack<>();
        stack.push(root);

        // DFS
        while (!stack.isEmpty()) {
            Node node = stack.pop();

            if (!visitor.visit(node) || !node.divided) continue;

            if (node.nw != null) stack.push(node.nw);
            if (node.ne != null) stack.push(node.ne);
            if (node.sw != null) stack.push(node.sw);
            if (node.se != null) stack.push(node.se);
        }
    }

    public INode<T> getRoot() {
        return root;
    }

    @VisibleForTesting
    class Node implements INode<T> {

        final double x, z, width, length;
        final int level;
        @Nullable List<Entry> entries = null;
        @Nullable Node nw, ne, sw, se = null;
        boolean divided = false;

        Node(double x, double z, double width, double length, int level) {
            this.x = x;
            this.z = z;
            this.width = width;
            this.length = length;
            this.level = level;
        }

        boolean hasCapacity() {
            return !divided && (entries == null || entries.size() < nodeCapacity);
        }

        boolean outOfBounds(Vec3d position) {
            return position.x < x || position.x >= x + width ||
                   position.z < z || position.z >= z + length;
        }

        /**
         * Gets or creates the child node at a given position.
         * The position is assumed to be within the nodes bounds.
         * @param pos The position.
         * @return The child node containing that position.
         */
        Node childAt(Vec3d pos) {
            double halfWidth = width * 0.5;
            double halfHeight = length * 0.5;

            boolean west = pos.x < x + halfWidth;
            boolean north = pos.z < z + halfHeight;

            return north
                    ? (west ? nw : ne)
                    : (west ? sw : se);
        }

        void divide() {
            // check if already divided
            if (divided) return;

            divided = true;

            double halfWidth = width * 0.5;
            double halfHeight = length * 0.5;

            nw = new Node(x, z, halfWidth, halfHeight, level + 1);
            ne = new Node(x + halfWidth, z, halfWidth, halfHeight, level + 1);
            sw = new Node(x, z + halfHeight, halfWidth, halfHeight, level + 1);
            se = new Node(x + halfWidth, z + halfHeight, halfWidth, halfHeight, level + 1);

            if (entries == null) return;

            // now put every entry in the subtrees
            for (Entry entry : entries) {
                Node node = childAt(entry.pos);

                node.add(entry);
            }

            // finally unset entries as they are now in the child nodes
            entries = null;
        }

        boolean add(Entry entry) {
            if (divided) return false;

            if (entries == null) {
                entries = new ArrayList<>(nodeCapacity);
            }

            return entries.add(entry);
        }

        boolean remove(Entry entry) {
            if (divided || entries == null || !entries.remove(entry)) {
                return false;
            }

            // unset entries so that is can be freed
            if (entries.isEmpty()) {
                entries = null;
            }

            return true;
        }

        @Override
        public int count() {
            if (!divided) {
                return entries != null ? entries.size() : 0;
            }

            int total = 0;

            if (nw != null) total += nw.count();
            if (ne != null) total += ne.count();
            if (sw != null) total += sw.count();
            if (se != null) total += se.count();

            return total;
        }

        void merge() {
            if (!divided || count() > nodeCapacity) return;

            if (entries == null) {
                entries = new ArrayList<>(nodeCapacity);
            }

            if (nw != null && nw.entries != null) {
                entries.addAll(nw.entries);
            }

            if (ne != null && ne.entries != null) {
                entries.addAll(ne.entries);
            }

            if (sw != null && sw.entries != null) {
                entries.addAll(sw.entries);
            }

            if (se != null && se.entries != null) {
                entries.addAll(se.entries);
            }

            nw = null;
            ne = null;
            sw = null;
            se = null;

            divided = false;
        }

        @Override
        public String toString() {
            return "Node{count=%s, divided=%s, level=%s, x=%s, z=%s, width=%s, length=%s}".formatted(count(), divided, level, x, z, width, length);
        }

        @NotNull
        @Override
        public Iterator<T> iterator() {
            if (entries == null) {
                return Collections.emptyIterator();
            }

            return Iterators.transform(entries.iterator(), e -> e.element);
        }

        @Override
        public int level() {
            return level;
        }

        @Override
        public double x() {
            return x;
        }

        @Override
        public double z() {
            return z;
        }

        @Override
        public double width() {
            return width;
        }

        @Override
        public double length() {
            return length;
        }

        @Override
        public boolean divided() {
            return divided;
        }

        @Override
        public Iterator<INode<T>> children() {
            if (!divided) {
                return Collections.emptyIterator();
            }

            return new Iterator<INode<T>>() {
                int i = 0;
                INode<T> next = advance();

                INode<T> advance() {
                    while (i < 4) {
                        switch (i++) {
                            case 0 -> {
                                if (nw != null) return nw;
                            }
                            case 1 -> {
                                if (ne != null) return ne;
                            }
                            case 2 -> {
                                if (sw != null) return sw;
                            }
                            case 3 -> {
                                if (se != null) return se;
                            }
                            default -> {}
                        }
                    }

                    return null;
                }

                @Override
                public boolean hasNext() {
                    return next != null;
                }

                @Override
                public INode<T> next() {
                    INode<T> ret = next;

                    next = advance();

                    return ret;
                }
            };
        }
    }

    class Entry {
        final T element;
        Vec3d pos;

        Entry(T element, Vec3d pos) {
            this.element = element;
            this.pos = pos;
        }
    }

    public interface INode<T> extends Iterable<T> {

        int count();

        int level();

        double x();

        double z();

        double width();

        double length();

        boolean divided();

        Iterator<INode<T>> children();
    }

    public interface Visitor<T> {

        /**
         * Called for each node in the tree.
         * @param node The node to be visited.
         * @return True, if the children of the node should be traversed, if there are any.
         */
        boolean visit(INode<T> node);
    }
}
