package work.lclpnet.ap2.api.util.heads;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.core.mixin.block.SkullBlockEntityAccessor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public record PlayerHead(String textureId, String texture) {

    private static final UUID NULL_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static final Codec<PlayerHead> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("texture").forGetter(PlayerHead::textureId)
    ).apply(instance, PlayerHead::new));

    public PlayerHead(String textureId) {
        this(textureId, getBase64Texture(textureId));
    }

    public ItemStack createStack() {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.PROFILE, createProfileComponent());

        return stack;
    }

    public @NotNull ResolvableProfile createProfileComponent() {
        var properties = new PropertyMap(ImmutableMultimap.of(
                "textures", new Property("textures", texture)
        ));

        return ResolvableProfile.createResolved(new GameProfile(NULL_UUID, "", properties));
    }

    public void apply(SkullBlockEntity skull) {
        ((SkullBlockEntityAccessor) skull).setOwner(createProfileComponent());
    }

    public static String getBase64Texture(String textureId) {
        @SuppressWarnings("HttpUrlsUsage")
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/%s\"}}}".formatted(textureId);

        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
