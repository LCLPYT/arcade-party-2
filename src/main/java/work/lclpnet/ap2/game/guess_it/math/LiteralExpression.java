package work.lclpnet.ap2.game.guess_it.math;

import org.jetbrains.annotations.Nullable;

record LiteralExpression(int value) implements Expression {

    @Override
    public int evaluate() {
        return value;
    }

    @Override
    public int precedence() {
        return Integer.MIN_VALUE;
    }

    @Override
    public boolean commutative() {
        return false;
    }

    @Override
    public String stringify(@Nullable Expression parent, int pos) {
        return String.valueOf(value);
    }
}
