package work.lclpnet.ap2.game.guess_it.math.op;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.guess_it.math.Expression;

import static work.lclpnet.ap2.game.guess_it.math.ExpressionUtils.addParentheses;
import static work.lclpnet.ap2.game.guess_it.math.ExpressionUtils.join;

public record Multiplication(Expression left, Expression right) implements Expression {

    @Override
    public int evaluate() {
        return left.evaluate() * right.evaluate();
    }

    @Override
    public int precedence() {
        return 300;
    }

    @Override
    public boolean commutative() {
        return true;
    }

    @Override
    public String stringify(@Nullable Expression parent, int pos) {
        String inner = join(this, left, right, '×');

        return addParentheses(inner, this, parent, pos);
    }
}
