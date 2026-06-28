package work.lclpnet.ap2.game.guess_it.math

import work.lclpnet.ap2.game.guess_it.math.op.Addition
import work.lclpnet.ap2.game.guess_it.math.op.Division
import work.lclpnet.ap2.game.guess_it.math.op.Multiplication
import work.lclpnet.ap2.game.guess_it.math.op.Subtraction

object Term {
    @JvmStatic
    fun num(num: Int): Expression {
        return LiteralExpression(num)
    }

    @JvmStatic
    fun add(left: Expression, right: Expression): Expression {
        return Addition(left, right)
    }

    @JvmStatic
    fun sub(left: Expression, right: Expression): Expression {
        return Subtraction(left, right)
    }

    @JvmStatic
    fun mul(left: Expression, right: Expression): Expression {
        return Multiplication(left, right)
    }

    @JvmStatic
    fun div(left: Expression, right: Expression): Expression {
        return Division(left, right)
    }
}
