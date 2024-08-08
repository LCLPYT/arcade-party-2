package work.lclpnet.ap2.impl.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

public class EntityUtil {

    private EntityUtil() {}

    public static void setAttribute(LivingEntity entity, RegistryEntry<EntityAttribute> attribute, double value) {
        EntityAttributeInstance instance = entity.getAttributeInstance(attribute);

        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public static void resetAttribute(LivingEntity entity, RegistryEntry<EntityAttribute> attribute) {
        if (attribute == EntityAttributes.GENERIC_MOVEMENT_SPEED && entity instanceof ServerPlayerEntity player) {
            setAttribute(player, attribute, player.getAbilities().getWalkSpeed());
            return;
        }

        setAttribute(entity, attribute, attribute.value().getDefaultValue());
    }
}
