package work.lclpnet.ap2.game.kit

@JvmRecord
data class KitOptions(val mainItemSlot: Int, val kitSelectorSlot: Int) {
    fun withKitSelectorSlot(kitSelectorSlot: Int): KitOptions {
        return KitOptions(mainItemSlot, kitSelectorSlot)
    }

    fun withMainItemSlot(mainItemSlot: Int): KitOptions {
        return KitOptions(mainItemSlot, kitSelectorSlot)
    }

    companion object {
        @JvmStatic
        val DEFAULT: KitOptions = KitOptions(0, 4)
    }
}
