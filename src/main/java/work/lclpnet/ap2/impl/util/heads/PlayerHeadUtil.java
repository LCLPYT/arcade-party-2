package work.lclpnet.ap2.impl.util.heads;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.UUID;

public class PlayerHeadUtil {

    public static ItemStack getItem(UUID uuid, String texture) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);

        NbtCompound nbt = stack.getOrCreateSubNbt("SkullOwner");
        nbt.putUuid("Id", uuid);

        NbtCompound properties = new NbtCompound();
        NbtList textures = new NbtList();
        NbtCompound entry = new NbtCompound();

        entry.putString("Value", texture);

        textures.add(entry);
        properties.put("textures", textures);
        nbt.put("Properties", properties);

        return stack;
    }

    public static ItemStack getItem(PlayerHead playerHead) {
        return getItem(playerHead.uuid(), playerHead.texture());
    }

    private PlayerHeadUtil() {}
}
