package work.lclpnet.ap2.game.guess_it.data

import net.minecraft.network.chat.Component

interface InputInterface {

    fun expectInput(): InputValue

    fun expectSelection(vararg options: Component)
}
