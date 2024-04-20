package work.lclpnet.ap2.impl.util.collision;

import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;

public class BoxCollisionDetector {

    private final Set<BlockBox> boxes = new HashSet<>();
    private final Stack<Set<BlockBox>> stack = new Stack<>();

    public boolean add(BlockBox box) {
        Objects.requireNonNull(box);

        if (hasCollisions(box)) return false;

        current().add(box);
        boxes.add(box);

        return true;
    }

    public boolean hasCollisions(BlockBox box) {
        return boxes.stream().anyMatch(box::collidesWith);
    }

    public void push() {
        synchronized (this) {
            stack.push(new HashSet<>());
        }
    }

    public void pop() {
        synchronized (this) {
            if (stack.isEmpty()) return;

            Set<BlockBox> frame = stack.pop();
            boxes.removeAll(frame);
        }
    }

    private Set<BlockBox> current() {
        synchronized (this) {
            if (stack.isEmpty()) {
                push();
            }

            return stack.peek();
        }
    }

    public void reset() {
        synchronized (this) {
            stack.clear();
            boxes.clear();
        }
    }
}
