package work.lclpnet.ap2.core.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntSortedMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.FuelValues;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApFuelRegistry;

@Mixin(FuelValues.class)
public class FuelValuesMixin implements ApFuelRegistry {

    @Shadow @Final private Object2IntSortedMap<Item> values;

    @Override
    public int ap2$getFuelTicks(Item item) {
        return values.getOrDefault(item, 0);
    }
}
