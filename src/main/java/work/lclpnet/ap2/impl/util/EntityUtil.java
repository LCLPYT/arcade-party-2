package work.lclpnet.ap2.impl.util;

import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Optional;

public class EntityUtil {

    private EntityUtil() {}

    public static void setAttribute(LivingEntity entity, RegistryEntry<EntityAttribute> attribute, double value) {
        EntityAttributeInstance instance = entity.getAttributeInstance(attribute);

        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public static void resetAttribute(LivingEntity entity, RegistryEntry<EntityAttribute> attribute) {
        if (attribute == EntityAttributes.MOVEMENT_SPEED && entity instanceof ServerPlayerEntity player) {
            setAttribute(player, attribute, player.getAbilities().getWalkSpeed());
            return;
        }

        setAttribute(entity, attribute, attribute.value().getDefaultValue());
    }

    public static void addAttributeModifier(LivingEntity entity, RegistryEntry<EntityAttribute> attribute, Identifier id, double value, EntityAttributeModifier.Operation operation) {
        EntityAttributeInstance instance = entity.getAttributeInstance(attribute);

        if (instance == null || instance.hasModifier(id)) return;

        instance.addTemporaryModifier(new EntityAttributeModifier(id, value, operation));
    }

    public static void removeAttributeModifier(LivingEntity entity, RegistryEntry<EntityAttribute> attribute, Identifier id) {
        EntityAttributeInstance instance = entity.getAttributeInstance(attribute);

        if (instance == null) return;

        instance.removeModifier(id);
    }

    public static <T> void sutCustomData(Entity entity, MapEncoder<T> encoder, T value) {
        entity.setComponent(DataComponentTypes.CUSTOM_DATA, entity.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .with(NbtOps.INSTANCE, encoder, value)
                .getOrThrow());
    }

    public static <T> Optional<T> getCustomData(Entity entity, MapDecoder<T> decoder) {
        return entity.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .get(decoder)
                .resultOrPartial();
    }
}
