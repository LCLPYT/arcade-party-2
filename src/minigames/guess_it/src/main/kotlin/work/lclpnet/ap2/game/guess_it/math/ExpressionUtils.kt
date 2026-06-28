package work.lclpnet.ap2.game.guess_it.math

object ExpressionUtils {
    const val POS_LEFT: Int = 0
    const val POS_RIGHT: Int = 1

    fun addParentheses(inner: String, expression: Expression, parent: Expression?, pos: Int): String {
        if (parent == null) {
            return inner
        }

        // check operation precedence
        val precedence = expression.precedence()
        val parentPrecedence = parent.precedence()

        if (precedence > parentPrecedence) {
            return "($inner)"
        }

        if (precedence < parentPrecedence) {
            return inner
        }

        // equal precedence; add parentheses if parent is not commutative and the expression is on the right
        if (pos == POS_RIGHT && !parent.commutative()) {
            // add parentheses on right expression
            return "($inner)"
        }

        return inner
    }

    fun join(self: Expression, left: Expression, right: Expression, sign: Char): String {
        val leftStr = left.stringify(self, POS_LEFT)
        var rightStr = right.stringify(self, POS_RIGHT)

        val rsTrimmed = rightStr.trim { it <= ' ' }

        if (!rsTrimmed.isEmpty() && rsTrimmed.get(0) == '-') {
            rightStr = "($rightStr)"
        }

        return "$leftStr $sign $rightStr"
    }
}
