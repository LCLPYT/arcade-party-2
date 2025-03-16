package work.lclpnet.ap2.core.mixin;

import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LecternBlockEntity.class)
public interface LecternBlockEntityAccessor {
    @Accessor
    Inventory getInventory();
}
