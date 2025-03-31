package work.lclpnet.ap2.api.util.heads;

import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Uuids;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

public record PlayerHead(UUID uuid, String textureId, String texture) {

    public static final Codec<PlayerHead> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.INT_STREAM_CODEC.fieldOf("uuid").forGetter(PlayerHead::uuid),
            Codec.STRING.fieldOf("texture").forGetter(PlayerHead::textureId)
    ).apply(instance, PlayerHead::new));

    public PlayerHead(UUID uuid, String textureId) {
        this(uuid, textureId, getBase64Texture(textureId));
    }

    public ItemStack createStack() {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponentTypes.PROFILE, createProfileComponent());

        return stack;
    }

    public @NotNull ProfileComponent createProfileComponent() {
        var properties = new PropertyMap();
        properties.put("textures", new Property("textures", texture));

        return new ProfileComponent(Optional.empty(), Optional.of(uuid), properties);
    }

    public void apply(SkullBlockEntity skull) {
        skull.setOwner(createProfileComponent());
    }

    public static String getBase64Texture(String textureId) {
        @SuppressWarnings("HttpUrlsUsage")
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/%s\"}}}".formatted(textureId);

        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
