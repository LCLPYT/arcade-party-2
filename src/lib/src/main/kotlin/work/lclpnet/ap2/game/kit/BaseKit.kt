package work.lclpnet.ap2.game.kit

abstract class BaseKit protected constructor(
    val handle: KitHandle,
    val id: String
) : Kit {
    override fun id(): String = id
}
