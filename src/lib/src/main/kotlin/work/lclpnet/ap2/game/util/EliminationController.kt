package work.lclpnet.ap2.game.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import work.lclpnet.kibu.translate.text.TranslatedText

interface EliminationController {

    fun eliminateAll(players: Iterable<ServerPlayer>)

    fun eliminate(
        player: ServerPlayer,
        source: DamageSource? = null,
        customMsg: TranslatedText? = null,
    )
}
