package work.lclpnet.ap2.game.item

fun interface SpecialItemRegistrar {
    fun register(item: SpecialItem, chance: Float): SpecialItemRegistrar
}
