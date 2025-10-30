package work.lclpnet.ap2.impl.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;

import java.util.Optional;
import java.util.function.Consumer;

public class CustomNbt {

    public static <T> void set(ItemStack stack, MapCodec<T> mapCodec, T value) {
        set(stack, mapCodec, value,
                component -> stack.set(DataComponentTypes.CUSTOM_DATA, component));
    }

    public static <T> void set(Entity entity, MapCodec<T> mapCodec, T value) {
        set(entity, mapCodec, value,
                component -> entity.setComponent(DataComponentTypes.CUSTOM_DATA, component));
    }

    private static <T> void set(ComponentsAccess components, MapCodec<T> mapCodec, T value, Consumer<NbtComponent> setter) {
        NbtComponent component = components.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);

        mapCodec.codec().encode(value, NbtOps.INSTANCE, component.copyNbt())
                .ifSuccess(nbt -> setter.accept(NbtComponent.of((NbtCompound) nbt)));
    }

    public static <T> Optional<T> get(ComponentsAccess components, MapCodec<T> mapCodec) {
        NbtComponent component = components.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);

        return mapCodec.codec().decode(NbtOps.INSTANCE, component.copyNbt())
                .resultOrPartial()
                .map(Pair::getFirst);
    }
}
