package work.lclpnet.ap2.dragons

import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.impl.map.schema.CommonMapSchema
import work.lclpnet.ap2.impl.map.schema.MapSchema
import work.lclpnet.ap2.impl.map.schema.Property
import work.lclpnet.gaco.ds.BlockBox

@MapSchema(namespace = "ap2", id = "dragons", name = "Dragons")
class DragonEscapeMapSchema : CommonMapSchema() {

    @Property(name = "Map bounds")
    val mapBounds: BlockBox? = null

    @Property(name = "Dragons spawn")
    val dragonsSpawn: Vec3? = null
}