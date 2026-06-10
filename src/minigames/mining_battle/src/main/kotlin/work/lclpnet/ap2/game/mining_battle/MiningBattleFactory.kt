package work.lclpnet.ap2.game.mining_battle

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.util.openRandomMap
import work.lclpnet.ap2.impl.map.MapUtil
import java.util.*

class MiningBattleFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        level.gameRules.set(GameRules.BLOCK_DROPS, false, handle.server)

        val ore = MiningBattleOre(Random(), handle)
        ore.init()

        val material = mutableSetOf<BlockState>()
        MapUtil.readBlockStates(map.requireProperty("material"), material, handle.logger)

        val box = MapUtil.readBox(map.requireProperty("mining-box"))

        MiningBattleGenerator(ore, box, material).generateOre(level)

        return MiningBattleInstance(handle, level, map, ore, material, box)
    }
}