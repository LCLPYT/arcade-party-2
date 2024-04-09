package work.lclpnet.ap2.game.guess_it.math;

import work.lclpnet.ap2.game.guess_it.math.op.Addition;
import work.lclpnet.ap2.game.guess_it.math.op.Division;
import work.lclpnet.ap2.game.guess_it.math.op.Multiplication;
import work.lclpnet.ap2.game.guess_it.math.op.Subtraction;

public class Term {

    private Term() {}

    public static Expression num(int num) {
        return new LiteralExpression(num);
    }

    public static Expression add(Expression left, Expression right) {
        return new Addition(left, right);
    }

    public static Expression sub(Expression left, Expression right) {
        return new Subtraction(left, right);
    }

    public static Expression mul(Expression left, Expression right) {
        return new Multiplication(left, right);
    }

    public static Expression div(Expression left, Expression right) {
        return new Division(left, right);
    }
}
