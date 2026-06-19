package work.lclpnet.ap2.game.team

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import work.lclpnet.ap2.api.game.team.DyeTeamKey

fun interface Paintable {
    fun blockFor(team: DyeTeamKey): Block
}

class DyeBlockManager(val level: ServerLevel) {

    private val paintableBlocks = HashMap<Block, Paintable>()
    private val blockTeamMap = HashMap<Block, DyeTeamKey>()

    fun init(teams: Iterable<DyeTeamKey>) {
        val paintables: List<Paintable> = listOf(
            Paintable(DyeTeamKey::woolBlock),
            Paintable(DyeTeamKey::carpetBlock),
            Paintable(DyeTeamKey::concreteBlock),
            Paintable(DyeTeamKey::concretePowderBlock),
            Paintable(DyeTeamKey::terracottaBlock),
            Paintable(DyeTeamKey::glazedTerracottaBlock),
            Paintable(DyeTeamKey::stainedGlassBlock),
            Paintable(DyeTeamKey::stainedGlassPaneBlock),
            Paintable(DyeTeamKey::bedBlock),
            Paintable(DyeTeamKey::shulkerBoxBlock),
            Paintable(DyeTeamKey::candleBlock),
            Paintable(DyeTeamKey::candleCakeBlock),
            Paintable(DyeTeamKey::bannerBlock),
            Paintable(DyeTeamKey::wallBannerBlock),
        )

        for (paintable in paintables) {
            for (team in DyeTeamKey.entries) {
                paintableBlocks[paintable.blockFor(team)] = paintable
            }

            for (team in teams) {
                blockTeamMap[paintable.blockFor(team)] = team
            }
        }
    }

    fun paintable(block: Block): Paintable? = paintableBlocks[block]

    fun getTeam(block: Block): DyeTeamKey? = blockTeamMap[block]

    fun replace(pos: BlockPos, target: DyeTeamKey): Boolean {
        val current = level.getBlockState(pos)
        val paintable = paintable(current.block) ?: return false
        return replace(pos, current, paintable, target)
    }

    fun replace(pos: BlockPos, current: BlockState, paintable: Paintable, targetTeam: DyeTeamKey): Boolean {
        val baseState = paintable.blockFor(targetTeam).defaultBlockState()
        val targetState = copyProperties(current, baseState)

        if (current == targetState) return false

        return level.setBlock(
            pos,
            targetState,
            Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_CLIENTS or Block.UPDATE_SUPPRESS_DROPS
        )
    }

    private fun copyProperties(reference: BlockState, state: BlockState): BlockState {
        var result = state

        @Suppress("UNCHECKED_CAST")
        for (property in reference.properties) {
            result = applyProperty(result, reference, property as Property<Comparable<Any>>)
        }

        return result
    }

    private fun <T : Comparable<T>> applyProperty(
        state: BlockState,
        reference: BlockState,
        property: Property<T>
    ): BlockState =
        state.trySetValue(property, reference.getValue(property))
}