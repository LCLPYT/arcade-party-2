package work.lclpnet.ap2.impl.util.heads;

import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.Optional;
import java.util.UUID;

public class PlayerHeadUtil {

    public static ItemStack getItem(UUID uuid, String texture) {
        PropertyMap properties = new PropertyMap();
        properties.put("textures", new Property("textures", texture));

        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponentTypes.PROFILE, new ProfileComponent(Optional.empty(), Optional.of(uuid), properties));

        return stack;
    }

    public static ItemStack getItem(PlayerHead playerHead) {
        return getItem(playerHead.uuid(), playerHead.texture());
    }

    private PlayerHeadUtil() {}
}
