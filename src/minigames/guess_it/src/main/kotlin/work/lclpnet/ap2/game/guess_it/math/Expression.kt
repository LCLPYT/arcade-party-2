package work.lclpnet.ap2.game.guess_it.math

interface Expression {
    fun evaluate(): Int

    fun precedence(): Int

    fun commutative(): Boolean

    fun stringify(parent: Expression?, pos: Int): String

    fun stringify(): String {
        return stringify(null, -1)
    }
}
