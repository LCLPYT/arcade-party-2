package work.lclpnet.ap2.game.kit

abstract class BaseKit protected constructor(
    protected val handle: KitHandle,
    protected val id: String
) : Kit {
    override fun id(): String = id
}
