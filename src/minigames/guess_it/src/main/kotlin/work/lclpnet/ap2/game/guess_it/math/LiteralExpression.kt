package work.lclpnet.ap2.game.guess_it.math

internal data class LiteralExpression(val value: Int) : Expression {

    override fun evaluate(): Int = value

    override fun precedence(): Int = Int.MIN_VALUE

    override fun commutative(): Boolean = false

    override fun stringify(parent: Expression?, pos: Int): String = value.toString()
}
