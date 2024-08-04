package work.lclpnet.ap2.impl.util;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        this.root = new Node(x, z, width, length);
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
            node.split();
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

        while (node.split) {
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

        while (oldNode.split) {
            oldNode = oldNode.childAt(oldPos);
        }

        while (newNode.split) {
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

    @VisibleForTesting
    class Node {

        final double x, z, width, length;
        @Nullable List<Entry> entries = null;
        @Nullable Node nw, ne, sw, se = null;
        boolean split = false;

        Node(double x, double z, double width, double length) {
            this.x = x;
            this.z = z;
            this.width = width;
            this.length = length;
        }

        boolean hasCapacity() {
            return !split && (entries == null || entries.size() < nodeCapacity);
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

        void split() {
            // check if already split
            if (split) return;

            split = true;

            double halfWidth = width * 0.5;
            double halfHeight = length * 0.5;

            nw = new Node(x, z, halfWidth, halfHeight);
            ne = new Node(x + halfWidth, z, halfWidth, halfHeight);
            sw = new Node(x, z + halfHeight, halfWidth, halfHeight);
            se = new Node(x + halfWidth, z + halfHeight, halfWidth, halfHeight);

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
            if (split) return false;

            if (entries == null) {
                entries = new ArrayList<>(nodeCapacity);
            }

            return entries.add(entry);
        }

        boolean remove(Entry entry) {
            if (split || entries == null || !entries.remove(entry)) {
                return false;
            }

            // unset entries so that is can be freed
            if (entries.isEmpty()) {
                entries = null;
            }

            return true;
        }

        int count() {
            if (!split) {
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
            if (!split || count() > nodeCapacity) return;

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

            split = false;
        }

        @Override
        public String toString() {
            return "Node{count=%s, split=%s, x=%s, z=%s, width=%s, length=%s}".formatted(count(), split, x, z, width, length);
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
}
