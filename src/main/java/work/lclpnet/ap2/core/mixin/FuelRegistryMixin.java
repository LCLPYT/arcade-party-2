package work.lclpnet.ap2.core.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntSortedMap;
import net.minecraft.item.FuelRegistry;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApFuelRegistry;

@Mixin(FuelRegistry.class)
public class FuelRegistryMixin implements ApFuelRegistry {

    @Shadow @Final private Object2IntSortedMap<Item> fuelValues;

    @Override
    public int ap2$getFuelTicks(Item item) {
        return fuelValues.getOrDefault(item, 0);
    }
}
