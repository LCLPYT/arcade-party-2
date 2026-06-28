package work.lclpnet.ap2.game.guess_it.math.op

import work.lclpnet.ap2.game.guess_it.math.Expression
import work.lclpnet.ap2.game.guess_it.math.ExpressionUtils

data class Multiplication(val left: Expression, val right: Expression) : Expression {

    override fun evaluate(): Int = left.evaluate() * right.evaluate()

    override fun precedence(): Int = 300

    override fun commutative(): Boolean = true

    override fun stringify(parent: Expression?, pos: Int): String {
        val inner = ExpressionUtils.join(this, left, right, '×')

        return ExpressionUtils.addParentheses(inner, this, parent, pos)
    }
}
