package work.lclpnet.ap2.game.guess_it.math;

import org.jetbrains.annotations.Nullable;

public interface Expression {

    int evaluate();

    int precedence();

    boolean commutative();

    String stringify(@Nullable Expression parent, int pos);

    default String stringify() {
        return stringify(null, -1);
    }
}
