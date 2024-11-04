package work.lclpnet.ap2.base.resource;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Quaternionf;
import work.lclpnet.ap2.impl.scene.BlockDisplayObject;
import work.lclpnet.ap2.impl.scene.ItemDisplayObject;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.util.model.PreparedModel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ModelLoader {

    private final RegistryWrapper.WrapperLookup lookup;
    private final RegistryWrapper.Impl<Block> blockLookup;

    public ModelLoader(RegistryWrapper.WrapperLookup lookup) {
        this.lookup = lookup;
        blockLookup = lookup.getWrapperOrThrow(RegistryKeys.BLOCK);
    }

    public PreparedModel load(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        String str = new String(bytes, StandardCharsets.UTF_8);

        StringReader reader = new StringReader(str);

        double x, y, z;
        NbtCompound nbt;

        try {
            x = reader.readDouble();
            reader.skipWhitespace();

            y = reader.readDouble();
            reader.skipWhitespace();

            z = reader.readDouble();
            reader.skipWhitespace();

            nbt = new StringNbtReader(reader).parseCompound();
        } catch (CommandSyntaxException e) {
            throw new IOException("Failed to parse model", e);
        }

        Object3d root = new Object3d();
        root.position.set(x, y, z);

        parseChildren(root, nbt);

        root.updateMatrixWorld();

        return new PreparedModel(root);
    }

    private void parseChildren(Object3d root, NbtCompound nbt) {
        NbtList passengers = nbt.getList("Passengers", NbtElement.COMPOUND_TYPE);

        for (NbtElement passenger : passengers) {
            if (passenger instanceof NbtCompound compound) {
                parseChild(root, compound);
            }
        }
    }

    private void parseChild(Object3d root, NbtCompound nbt) {
        String id = nbt.getString("id");

        Object3d obj = switch (id) {
            case "minecraft:block_display" -> {
                BlockState state = NbtHelper.toBlockState(blockLookup, nbt.getCompound("block_state"));
                yield new BlockDisplayObject(state);
            }
            case "minecraft:item_display" -> {
                var stack = ItemStack.fromNbt(lookup, nbt.getCompound("item")).orElse(ItemStack.EMPTY);
                yield new ItemDisplayObject(stack);
            }
            default -> null;
        };

        if (obj == null) return;

        var transformation = AffineTransformation.ANY_CODEC.decode(NbtOps.INSTANCE, nbt.get("transformation"))
                .result().map(Pair::getFirst)
                .orElse(AffineTransformation.identity());

        obj.scale.set(transformation.getScale());
        obj.position.set(transformation.getTranslation());

        // rotation = leftRotation * rightRotation
        Quaternionf right = transformation.getRightRotation();
        obj.rotation.set(transformation.getLeftRotation()).mul(right.x, right.y, right.z, right.w);

        root.addChild(obj);
    }
}
