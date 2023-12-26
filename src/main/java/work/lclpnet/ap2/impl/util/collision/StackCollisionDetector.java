package work.lclpnet.ap2.impl.util.collision;

import net.minecraft.util.math.Position;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.api.util.CollisionDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class StackCollisionDetector implements CollisionDetector {

    private final CollisionDetector parent;
    private final Stack<List<Collider>> stack;

    public StackCollisionDetector(CollisionDetector parent) {
        this.parent = parent;
        this.stack = new Stack<>();
    }

    @Override
    public void add(Collider collider) {
        current().add(collider);
        parent.add(collider);
    }

    @Override
    public void remove(Collider collider) {
        // if the collider was added in a deeper stack frame it will still hold a reference
        // this scenario is neglected at the moment
        current().remove(collider);
        parent.remove(collider);
    }

    @Override
    public @Nullable Collider getCollision(Position pos) {
        return parent.getCollision(pos);
    }

    public void push() {
        stack.add(new ArrayList<>());
    }

    public void pop() {
        List<Collider> colliders = stack.pop();

        for (Collider collider : colliders) {
            parent.remove(collider);
        }
    }

    private List<Collider> current() {
        if (stack.isEmpty()) push();

        return stack.peek();
    }
}
