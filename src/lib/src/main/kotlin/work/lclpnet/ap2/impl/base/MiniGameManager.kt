package work.lclpnet.ap2.impl.base

import com.mojang.serialization.Codec
import net.minecraft.resources.Identifier
import work.lclpnet.ap2.game.MiniGame

interface MiniGameManager {
    val games: Set<MiniGame>

    fun getGame(gameId: Identifier): MiniGame?

    val gameCodec: Codec<MiniGame>
}
