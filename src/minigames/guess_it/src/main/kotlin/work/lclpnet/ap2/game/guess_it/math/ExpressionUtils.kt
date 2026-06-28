package work.lclpnet.ap2.game.guess_it.math;

import org.jetbrains.annotations.Nullable;

public class ExpressionUtils {

    public static final int POS_LEFT = 0, POS_RIGHT = 1;

    private ExpressionUtils() {}

    public static String addParentheses(String inner, Expression expression, @Nullable Expression parent, int pos) {
        if (parent == null) {
            return inner;
        }

        // check operation precedence
        int precedence = expression.precedence();
        int parentPrecedence = parent.precedence();

        if (precedence > parentPrecedence) {
            return "(" + inner + ")";
        }

        if (precedence < parentPrecedence) {
            return inner;
        }

        // equal precedence; add parentheses if parent is not commutative and the expression is on the right
        if (pos == POS_RIGHT && !parent.commutative()) {
            // add parentheses on right expression
            return "(" + inner + ")";
        }

        return inner;
    }

    public static String join(Expression self, Expression left, Expression right, char sign) {
        String leftStr = left.stringify(self, POS_LEFT);
        String rightStr = right.stringify(self, POS_RIGHT);

        String rsTrimmed = rightStr.trim();

        if (!rsTrimmed.isEmpty() && rsTrimmed.charAt(0) == '-') {
            rightStr = "(" + rightStr + ")";
        }

        return "%s %s %s".formatted(leftStr, sign, rightStr);
    }
}
