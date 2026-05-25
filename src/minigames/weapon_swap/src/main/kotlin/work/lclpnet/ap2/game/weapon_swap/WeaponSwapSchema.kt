package work.lclpnet.ap2.game.weapon_swap

import net.minecraft.core.BlockPos
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.impl.map.schema.CommonMapSchema
import work.lclpnet.ap2.impl.map.schema.MapSchema
import work.lclpnet.ap2.impl.map.schema.Property
import work.lclpnet.gaco.ds.BlockBox

@MapSchema(
    namespace = ApConstants.ID,
    id = "weapon_swap",
    name = "Weapon Swap"
)
class WeaponSwapSchema : CommonMapSchema() {

    @Property(name = "Spawn scanner bounds")
    val scanBox: BlockBox? = null

    @Property(name = "Spawn scanner starts")
    val scanStarts: List<BlockPos> = listOf()
}